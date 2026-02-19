package nand.modid.game;

import nand.modid.comand.ChessStackEngine;
import nand.modid.chess.core.Piece;
import nand.modid.chess.core.Move;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

public class MinecraftChessManager {
    private static final MinecraftChessManager INSTANCE = new MinecraftChessManager();
    
    private static class MoveAnimation {
        String gameId;
        String pieceId;
        double startX, startY, startZ;
        double endX, endY, endZ;
        int currentTick;
        int maxTicks = 20; 
        UUID playerUuid;
    }

    private final ChessStackEngine engine;
    private String activeGameId;
    private BlockPos boardOrigin;
    
    // Tracks gameId -> pieceId -> List of Display Entity UUIDs (Block and Text)
    private final Map<String, Map<String, List<UUID>>> pieceEntities = new HashMap<>();
    private final Map<String, MoveAnimation> activeAnimations = new HashMap<>();
    private UUID statusEntity;
    
    private int[] selectedSquare = null;
    private int selectedPocketIndex = -1;

    private MinecraftChessManager() {
        this.engine = new ChessStackEngine();
    }

    public static MinecraftChessManager getInstance() {
        return INSTANCE;
    }

    public void startNewGame(BlockPos origin, ServerPlayerEntity player) {
        this.boardOrigin = origin;
        this.activeGameId = engine.createGame();
        
        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        
        player.sendMessage(Text.literal("§aNew Game Started!"), false);
        syncAllPieces(player.getServerWorld());
    }

    private void clearEntitiesByTag(MinecraftServer server, String tag) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e.getCommandTags().contains(tag)) {
                    e.discard();
                }
            }
        }
    }

    private void clearEntities(ServerWorld world) {
        for (Map<String, List<UUID>> gamePieces : pieceEntities.values()) {
            for (List<UUID> uuids : gamePieces.values()) {
                for (UUID uuid : uuids) {
                    Entity e = world.getEntity(uuid);
                    if (e != null) e.discard();
                }
            }
        }
        pieceEntities.clear();
        
        if (statusEntity != null) {
            Entity e = world.getEntity(statusEntity);
            if (e != null) e.discard();
            statusEntity = null;
        }
    }

    private void syncAllPieces(ServerWorld world) {
        updateStatusEntity(world);
        if (activeGameId == null) return;
        
        List<Piece.PieceData> boardPieces = engine.getBoardPieces(activeGameId);
        Set<String> currentPieceIds = new HashSet<>();
        
        for (Piece.PieceData p : boardPieces) {
            currentPieceIds.add(p.id);
            updatePieceVisuals(world, p);
        }
        
        Map<String, List<UUID>> gamePieces = pieceEntities.get(activeGameId);
        if (gamePieces != null) {
            Iterator<Map.Entry<String, List<UUID>>> it = gamePieces.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, List<UUID>> entry = it.next();
                if (!currentPieceIds.contains(entry.getKey())) {
                    for (UUID uuid : entry.getValue()) {
                        Entity e = world.getEntity(uuid);
                        if (e != null) e.discard();
                    }
                    it.remove();
                }
            }
        }
    }

    private void updateStatusEntity(ServerWorld world) {
        if (boardOrigin == null) return;

        double x = boardOrigin.getX() + 8.0;
        double z = boardOrigin.getZ() + 8.0;
        double y = boardOrigin.getY() + 4.0; // Higher above board top

        DisplayEntity.TextDisplayEntity textDisplay;
        if (statusEntity != null && world.getEntity(statusEntity) instanceof DisplayEntity.TextDisplayEntity old) {
            textDisplay = old;
        } else {
            textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            textDisplay.addCommandTag("sc_status");
            textDisplay.addCommandTag("sc_game_" + activeGameId);
            world.spawnEntity(textDisplay);
            statusEntity = textDisplay.getUuid();
        }

        textDisplay.refreshPositionAndAngles(x, y, z, 0, 0);
        String turnText = activeGameId == null ? "§6§lGAME OVER" : 
            (engine.getCurrentPlayer(activeGameId) == 0 ? "§f§lWhite's Turn" : "§7§lBlack's Turn");
        textDisplay.setText(Text.literal(turnText));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private void updatePieceVisuals(ServerWorld world, Piece.PieceData p) {
        if (p.pos == null) return;

        double x = boardOrigin.getX() + p.pos.x * 2 + 1.0;
        double z = boardOrigin.getZ() + p.pos.y * 2 + 1.0;
        double y = boardOrigin.getY() + 1.0; // Top of the board block

        Map<String, List<UUID>> gamePieces = pieceEntities.computeIfAbsent(activeGameId, k -> new HashMap<>());
        List<UUID> uuids = gamePieces.computeIfAbsent(p.id, k -> new ArrayList<>());
        DisplayEntity.BlockDisplayEntity blockDisplay = null;
        DisplayEntity.TextDisplayEntity textDisplay = null;

        for (UUID uuid : uuids) {
            Entity e = world.getEntity(uuid);
            if (e instanceof DisplayEntity.BlockDisplayEntity b) blockDisplay = b;
            else if (e instanceof DisplayEntity.TextDisplayEntity t) textDisplay = t;
        }

        if (blockDisplay == null) {
            blockDisplay = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            blockDisplay.addCommandTag("sc_game_" + activeGameId);
            blockDisplay.addCommandTag("sc_piece_" + p.id);
            world.spawnEntity(blockDisplay);
            uuids.add(blockDisplay.getUuid());
        }
        if (textDisplay == null) {
            textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            textDisplay.addCommandTag("sc_game_" + activeGameId);
            textDisplay.addCommandTag("sc_piece_" + p.id);
            world.spawnEntity(textDisplay);
            uuids.add(textDisplay.getUuid());
        }

        // Update Position (Only if NOT animating)
        if (!activeAnimations.containsKey(p.id)) {
            blockDisplay.refreshPositionAndAngles(x - 0.5, y, z - 0.5, 0, 0);
            textDisplay.refreshPositionAndAngles(x, y + 1.3, z, 0, 0);
        }

        // Update Visuals (Always)
        blockDisplay.setBlockState(getPieceBlock(p));
        
        String name = String.format("%s%s [%d]", p.owner == 0 ? "§f" : "§7", p.effectiveKind().name(), p.moveStack);
        if (p.stun > 0) name += " §c(STUN " + p.stun + ")";
        if (p.isRoyal) name = "§6★ " + name;
        textDisplay.setText(Text.literal(name));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private BlockState getPieceBlock(Piece.PieceData p) {
        boolean isWhite = p.owner == 0;
        return switch (p.effectiveKind()) {
            case KING -> (isWhite ? Blocks.GOLD_BLOCK : Blocks.NETHERITE_BLOCK).getDefaultState();
            case QUEEN -> (isWhite ? Blocks.DIAMOND_BLOCK : Blocks.CRYING_OBSIDIAN).getDefaultState();
            case ROOK -> (isWhite ? Blocks.IRON_BLOCK : Blocks.OBSIDIAN).getDefaultState();
            case BISHOP -> (isWhite ? Blocks.QUARTZ_BLOCK : Blocks.COAL_BLOCK).getDefaultState();
            case KNIGHT -> (isWhite ? Blocks.WHITE_TERRACOTTA : Blocks.BLACK_TERRACOTTA).getDefaultState();
            case PAWN -> (isWhite ? Blocks.WHITE_WOOL : Blocks.BLACK_WOOL).getDefaultState();
            case AMAZON -> Blocks.PURPUR_BLOCK.getDefaultState();
            case CANNON -> Blocks.TNT.getDefaultState();
            default -> (isWhite ? Blocks.SNOW_BLOCK : Blocks.GRAY_CONCRETE).getDefaultState();
        };
    }

    private Map<Piece.PieceKind, Integer> getGroupedPocket(int player) {
        List<Piece.PieceSpec> pocket = engine.getPocket(activeGameId, player);
        Map<Piece.PieceKind, Integer> counts = new LinkedHashMap<>();
        for (Piece.PieceSpec spec : pocket) {
            counts.put(spec.kind, counts.getOrDefault(spec.kind, 0) + 1);
        }
        return counts;
    }

    public void cyclePocketSelection(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        int currentPlayer = engine.getCurrentPlayer(activeGameId);
        Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
        List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

        if (uniqueKinds.isEmpty()) {
            player.sendMessage(Text.literal("§cPocket is empty!"), false);
            selectedPocketIndex = -1;
            return;
        }

        selectedPocketIndex++;
        if (selectedPocketIndex >= uniqueKinds.size()) {
            selectedPocketIndex = -1;
            player.sendMessage(Text.literal("§7Pocket Selection: None"), false);
        } else {
            Piece.PieceKind kind = uniqueKinds.get(selectedPocketIndex);
            int count = counts.get(kind);
            player.sendMessage(Text.literal("§ePocket Selection: §l" + kind.name() + " §r(x" + count + ") (" + (selectedPocketIndex + 1) + "/" + uniqueKinds.size() + ")"), false);
        }
    }

    public void handlePlaceInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();
        if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16) return;
        int boardX = dx / 2;
        int boardY = dz / 2;

        if (selectedPocketIndex >= 0) {
            int currentPlayer = engine.getCurrentPlayer(activeGameId);
            Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
            List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

            if (selectedPocketIndex < uniqueKinds.size()) {
                Piece.PieceKind kind = uniqueKinds.get(selectedPocketIndex);
                try {
                    engine.placePiece(activeGameId, kind.name(), boardX, boardY);
                    player.sendMessage(Text.literal("§aPlaced " + kind.name()), false);
                    selectedPocketIndex = -1; 
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()), false);
                }
            }
        }
        syncAllPieces(player.getServerWorld());
        checkGameResult(player);
    }

    public void handleMoveInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) return;
        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();
        if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16) return;
        int boardX = dx / 2;
        int boardY = dz / 2;

        if (selectedSquare == null) {
            Piece.PieceData piece = engine.getPieceAt(activeGameId, boardX, boardY);
            if (piece != null && piece.owner == engine.getCurrentPlayer(activeGameId)) {
                selectedSquare = new int[]{boardX, boardY};
                player.sendMessage(Text.literal("§eSelected §l" + piece.kind.name()), false);
            }
        } else {
            if (selectedSquare[0] == boardX && selectedSquare[1] == boardY) {
                selectedSquare = null;
                player.sendMessage(Text.literal("§7Deselected"), false);
            } else {
                try {
                    Piece.PieceData piece = engine.getPieceAt(activeGameId, selectedSquare[0], selectedSquare[1]);
                    if (piece != null) {
                        double startX = boardOrigin.getX() + selectedSquare[0] * 2 + 1.0;
                        double startZ = boardOrigin.getZ() + selectedSquare[1] * 2 + 1.0;
                        double startY = boardOrigin.getY() + 1.0;

                        engine.makeMove(activeGameId, selectedSquare[0], selectedSquare[1], boardX, boardY);
                        
                        double endX = boardOrigin.getX() + boardX * 2 + 1.0;
                        double endZ = boardOrigin.getZ() + boardY * 2 + 1.0;
                        double endY = boardOrigin.getY() + 1.0;

                        MoveAnimation anim = new MoveAnimation();
                        anim.gameId = activeGameId;
                        anim.pieceId = piece.id;
                        anim.startX = startX; anim.startY = startY; anim.startZ = startZ;
                        anim.endX = endX; anim.endY = endY; anim.endZ = endZ;
                        anim.currentTick = 0;
                        
                        if (player.getPos().distanceTo(new Vec3d(startX, startY, startZ)) < 5.0) {
                            anim.playerUuid = player.getUuid();
                        }
                        activeAnimations.put(piece.id, anim);
                        player.sendMessage(Text.literal("§7(Animating piece: " + piece.effectiveKind().name() + ")"), false);
                    }
                    player.sendMessage(Text.literal("§aMoved"), false);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()), false);
                }
                selectedSquare = null;
            }
        }
        syncAllPieces(player.getServerWorld());
        checkGameResult(player);
    }

    private void checkGameResult(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        Move.GameResult result = engine.getGameResult(activeGameId);
        if (result != Move.GameResult.ONGOING) {
            player.sendMessage(Text.literal("§6§lGAME OVER: " + result), false);
            activeGameId = null;
        }
    }

    public void tick(MinecraftServer server) {
        if (activeAnimations.isEmpty()) return;

        List<String> finished = new ArrayList<>();
        for (MoveAnimation anim : activeAnimations.values()) {
            anim.currentTick++;
            double t = (double) anim.currentTick / 40.0; // 2 seconds, linear

            double x = anim.startX + (anim.endX - anim.startX) * t;
            double y = anim.startY + (anim.endY - anim.startY) * t;
            double z = anim.startZ + (anim.endZ - anim.startZ) * t;

            double hop = Math.sin(t * Math.PI) * 1.0;
            double curY = y + hop;

            ServerPlayerEntity rider = anim.playerUuid != null ? server.getPlayerManager().getPlayer(anim.playerUuid) : null;
            if (rider != null) {
                rider.teleport(rider.getServerWorld(), x, curY + 1.0, z, 
                               EnumSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT), 
                               0, 0);
            }

            Map<String, List<UUID>> gamePieces = pieceEntities.get(anim.gameId);
            List<UUID> uuids = gamePieces != null ? gamePieces.get(anim.pieceId) : null;
            if (uuids != null) {
                for (ServerWorld world : server.getWorlds()) {
                    boolean foundAny = false;
                    for (UUID uuid : uuids) {
                        Entity e = world.getEntity(uuid);
                        if (e != null) {
                            e.refreshPositionAndAngles(x - (e instanceof DisplayEntity.BlockDisplayEntity ? 0.5 : 0), 
                                                     curY + (e instanceof DisplayEntity.TextDisplayEntity ? 1.3 : 0), 
                                                     z - (e instanceof DisplayEntity.BlockDisplayEntity ? 0.5 : 0), 0, 0);
                            foundAny = true;
                        }
                    }
                    if (foundAny) break;
                }
            }

            if (anim.currentTick >= 40) {
                finished.add(anim.pieceId);
            }
        }

        for (String id : finished) {
            activeAnimations.remove(id);
        }
    }

    public void handleInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (selectedPocketIndex >= 0) handlePlaceInteraction(clickedPos, player);
        else handleMoveInteraction(clickedPos, player);
    }
    
    public void resetGame(ServerPlayerEntity player) {
        if (activeGameId != null) {
            clearEntitiesByTag(player.getServer(), "sc_game_" + activeGameId);
            pieceEntities.remove(activeGameId);
        }
        this.activeGameId = null;
        this.boardOrigin = null;
        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        this.activeAnimations.clear();
        player.sendMessage(Text.literal("§cReset"), false);
    }
    
    public void endTurn(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        try {
            engine.endTurn(activeGameId);
            player.sendMessage(Text.literal("Turn Ended"), false);
            syncAllPieces(player.getServerWorld());
            checkGameResult(player);
        } catch (Exception e) {
             player.sendMessage(Text.literal("§c" + e.getMessage()), false);
        }
    }
}

