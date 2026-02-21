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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import nand.modid.StasisChess;

import java.util.*;

public class MinecraftChessManager {
    private static final MinecraftChessManager INSTANCE = new MinecraftChessManager();
    
    private static class MoveAnimation {
        String gameId;
        String pieceId;
        double startX, startY, startZ;
        double endX, endY, endZ;
        float yaw;
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
    // 포켓 표시 엔티티: 플레이어(0=백, 1=흑) -> 디스플레이 엔티티 UUID 목록
    private final Map<Integer, List<UUID>> pocketEntities = new HashMap<>();
    
    // 체스판 생성 전의 블록들을 저장
    private final Map<BlockPos, BlockState> savedBlocks = new HashMap<>();
    
    private int[] selectedSquare = null;
    private List<Move.LegalMove> currentLegalMoves = new ArrayList<>();
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

    public void startExperimentalGame(BlockPos origin, ServerPlayerEntity player) {
        this.boardOrigin = origin;
        this.activeGameId = engine.createExperimentalGame();

        this.selectedSquare = null;
        this.selectedPocketIndex = -1;

        player.sendMessage(Text.literal("§d§lExperimental Game Started!"), false);
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

        // 포켓 표시 엔티티 정리
        for (List<UUID> uuids : pocketEntities.values()) {
            for (UUID uuid : uuids) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        pocketEntities.clear();
        
        if (statusEntity != null) {
            Entity e = world.getEntity(statusEntity);
            if (e != null) e.discard();
            statusEntity = null;
        }
    }

    private void syncAllPieces(ServerWorld world) {
        updateStatusEntity(world);
        syncPocketDisplays(world);
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
        String turnText;
        if (activeGameId == null) {
            turnText = "§6§lGAME OVER";
        } else {
            Move.GameResult result = engine.getGameResult(activeGameId);
            if (result != Move.GameResult.ONGOING) {
                turnText = "§6§lGAME OVER: " + result;
            } else {
                turnText = (engine.getCurrentPlayer(activeGameId) == 0 ? "§f§lWhite's Turn" : "§7§lBlack's Turn");
            }
        }
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
        return getPieceBlockForKind(p.effectiveKind(), p.owner == 0);
    }

    private BlockState getPieceBlockForKind(Piece.PieceKind kind, boolean isWhite) {
        return switch (kind) {
            case KING -> (isWhite ? Blocks.GOLD_BLOCK : Blocks.NETHERITE_BLOCK).getDefaultState();
            case QUEEN -> (isWhite ? Blocks.DIAMOND_BLOCK : Blocks.CRYING_OBSIDIAN).getDefaultState();
            case ROOK -> (isWhite ? Blocks.IRON_BLOCK : Blocks.OBSIDIAN).getDefaultState();
            case BISHOP -> (isWhite ? Blocks.QUARTZ_BLOCK : Blocks.COAL_BLOCK).getDefaultState();
            case KNIGHT -> (isWhite ? Blocks.WHITE_TERRACOTTA : Blocks.BLACK_TERRACOTTA).getDefaultState();
            case PAWN -> (isWhite ? Blocks.WHITE_WOOL : Blocks.BLACK_WOOL).getDefaultState();
            case AMAZON -> Blocks.PURPUR_BLOCK.getDefaultState();
            case CANNON -> Blocks.TNT.getDefaultState();
            case GRASSHOPPER -> Blocks.SLIME_BLOCK.getDefaultState();
            case KNIGHTRIDER -> Blocks.BLUE_ICE.getDefaultState();
            case ARCHBISHOP -> Blocks.AMETHYST_BLOCK.getDefaultState();
            case DABBABA -> Blocks.COPPER_BLOCK.getDefaultState();
            case ALFIL -> Blocks.PRISMARINE.getDefaultState();
            case FERZ -> Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
            case CENTAUR -> Blocks.MUD_BRICKS.getDefaultState();
            case CAMEL -> Blocks.CUT_SANDSTONE.getDefaultState();
            case TEMPEST_ROOK -> Blocks.SEA_LANTERN.getDefaultState();
            case BOUNCING_BISHOP -> Blocks.HONEY_BLOCK.getDefaultState();
            case EXPERIMENT -> Blocks.GILDED_BLACKSTONE.getDefaultState();
            case CUSTOM -> Blocks.EMERALD_BLOCK.getDefaultState();
        };
    }

    /**
     * 게임 시작/매 수마다 양옆에 포켓 표시를 갱신한다.
     * 백 포켓: 보드 남쪽(z-2), 흑 포켓: 보드 북쪽(z+17)
     */
    private void syncPocketDisplays(ServerWorld world) {
        // 기존 포켓 엔티티 제거
        for (List<UUID> uuids : pocketEntities.values()) {
            for (UUID uuid : uuids) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        pocketEntities.clear();

        if (activeGameId == null || boardOrigin == null) return;

        double y = boardOrigin.getY() + 1.0;
        // player: 0=백(남쪽), 1=흑(북쪽)
        int[]    zOffsets = { -2, 17 };
        String[] titles   = { "§f§lWHITE POCKET", "§7§lBLACK POCKET" };

        for (int player = 0; player < 2; player++) {
            double pocketZ = boardOrigin.getZ() + zOffsets[player];
            boolean isWhite = (player == 0);
            List<UUID> playerUuids = pocketEntities.computeIfAbsent(player, k -> new ArrayList<>());

            // 제목 텍스트 (보드 중앙 X+8.0으로 정렬)
            DisplayEntity.TextDisplayEntity titleDisplay =
                new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            titleDisplay.addCommandTag("sc_pocket");
            titleDisplay.addCommandTag("sc_game_" + activeGameId);
            // 제목 위치를 약간 더 뒤로 밀어서 기물과 겹치지 않게 함
            double titleZ = pocketZ + (isWhite ? 1.5 : -1.5);
            titleDisplay.refreshPositionAndAngles(
                boardOrigin.getX() + 8.0, y + 2.5, titleZ, 0, 0);
            titleDisplay.setText(Text.literal(titles[player]));
            titleDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
            world.spawnEntity(titleDisplay);
            playerUuids.add(titleDisplay.getUuid());

            // 포켓 내 기물 종류별 블록+카운트 텍스트
            Map<Piece.PieceKind, Integer> counts = getGroupedPocket(player);
            int slot = 0;
            // 현재 플레이어의 포켓에만 선택 표시 적용
            int currentPlayer = (activeGameId != null) ? engine.getCurrentPlayer(activeGameId) : -1;
            boolean isCurrentPlayer = (player == currentPlayer);
            for (Map.Entry<Piece.PieceKind, Integer> entry : counts.entrySet()) {
                Piece.PieceKind kind = entry.getKey();
                int count = entry.getValue();

                int col = slot % 6; // 한 줄에 6개씩
                int row = slot / 6;
                // 중앙 정렬 오프셋 1.75 ( (16 - (5*2.5)) / 2 )
                double slotX = boardOrigin.getX() + col * 2.5 + 1.75; 
                // 백(0)은 남쪽으로(-), 흑(1)은 북쪽으로(+) 줄을 늘림. 줄 간격 3.0
                double rowZ = pocketZ + (isWhite ? -row * 3.0 : row * 3.0);

                boolean isSelected = isCurrentPlayer && (slot == selectedPocketIndex);

                // 블록 디스플레이
                DisplayEntity.BlockDisplayEntity blockDisplay =
                    new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
                blockDisplay.addCommandTag("sc_pocket");
                blockDisplay.addCommandTag("sc_game_" + activeGameId);
                // 선택된 슬롯은 살짝 위로 띄워 강조
                double blockY = isSelected ? y + 0.3 : y;
                blockDisplay.refreshPositionAndAngles(slotX - 0.5, blockY, rowZ - 0.5, 0, 0);
                blockDisplay.setBlockState(getPieceBlockForKind(kind, isWhite));
                world.spawnEntity(blockDisplay);
                playerUuids.add(blockDisplay.getUuid());

                // 수량 텍스트 (선택 시 황금색 강조)
                DisplayEntity.TextDisplayEntity countDisplay =
                    new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
                countDisplay.addCommandTag("sc_pocket");
                countDisplay.addCommandTag("sc_game_" + activeGameId);
                countDisplay.refreshPositionAndAngles(slotX, blockY + 1.3, rowZ, 0, 0);
                String color = isSelected ? "§6§l" : (isWhite ? "§f" : "§7");
                String label = color + kind.name() + " ×" + count + (isSelected ? " §e◀" : "");
                countDisplay.setText(Text.literal(label));
                countDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
                world.spawnEntity(countDisplay);
                playerUuids.add(countDisplay.getUuid());

                slot++;
            }
        }
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

    /**
     * drop_tool로 포켓 표시 영역을 클릭했을 때 해당 슬롯의 기물을 선택한다.
     * 백 포켓: dz ∈ [-3, -1], 흑 포켓: dz ∈ [16, 18]
     *
     * @return 포켓 영역 클릭이면 true (처리됨), 아니면 false (보드 클릭으로 처리 위임)
     */
    public boolean handlePocketClick(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) return false;

        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();

        // 포켓 영역 판별 (3줄 지원)
        // 백 포켓: dz ∈ [-12, -1], 흑 포켓: dz ∈ [16, 28]
        boolean isWhitePocket = dz >= -12 && dz <= -1;
        boolean isBlackPocket = dz >= 16 && dz <= 28;
        if (!isWhitePocket && !isBlackPocket) return false;
        // 가로 대칭 0~16블록 범위 (16블록 보드 너비와 동일하게 맞춤)
        if (dx < 0 || dx >= 16) return false;

        int clickedPlayer = isWhitePocket ? 0 : 1;
        int currentPlayer = engine.getCurrentPlayer(activeGameId);

        if (clickedPlayer != currentPlayer) {
            player.sendMessage(Text.literal("§cNot your turn!"), false);
            return true;
        }

        // col 계산: dx 1.75를 기준으로 2.5씩 간격 (dx - (1.75 - 1.25)) / 2.5
        int col = (int)((dx - 0.5) / 2.5);
        if (col < 0) col = 0;
        if (col >= 6) col = 5;
        
        int row;
        if (isWhitePocket) {
            // Row 0: dz -2, Row 1: dz -5, Row 2: dz -8, Row 3: dz -11
            row = (Math.abs(dz - (-2)) + 1) / 3;
        } else {
            // Row 0: dz 17, Row 1: dz 20, Row 2: dz 23, Row 3: dz 26
            row = (Math.abs(dz - 17) + 1) / 3;
        }

        int slot = row * 6 + col;
        Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
        List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

        if (uniqueKinds.isEmpty()) {
            player.sendMessage(Text.literal("§cPocket is empty!"), false);
            return true;
        }
        if (slot >= uniqueKinds.size()) {
            // 슬롯에 기물이 없으면 선택 해제
            selectedPocketIndex = -1;
            player.sendMessage(Text.literal("§7Pocket Selection: None"), false);
            syncPocketDisplays(player.getServerWorld());
            return true;
        }

        selectedPocketIndex = slot;
        Piece.PieceKind kind = uniqueKinds.get(slot);
        int count = counts.get(kind);
        
        // 포켓 디스플레이 갱신 (선택 표시)
        syncPocketDisplays(player.getServerWorld());
        return true;
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
                currentLegalMoves = engine.getLegalMoves(activeGameId, boardX, boardY);
                player.sendMessage(Text.literal("§eSelected §l" + piece.kind.name() + " §7(" + currentLegalMoves.size() + " moves)"), false);
            }
        } else {
            if (selectedSquare[0] == boardX && selectedSquare[1] == boardY) {
                selectedSquare = null;
                currentLegalMoves.clear();
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
                        
                        // Calculate yaw (horizontal rotation) to face the destination
                        double adx = endX - startX;
                        double adz = endZ - startZ;
                        anim.yaw = (float) Math.toDegrees(Math.atan2(-adx, adz));
                        
                        anim.currentTick = 0;
                        
                        // Increase detection range to 20 blocks so player is almost always picked up
                        if (player.getPos().distanceTo(new Vec3d(startX, startY, startZ)) < 20.0) {
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
                currentLegalMoves.clear();
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
            // We keep activeGameId so resetGame() can still clean up entities.
        }
    }

    public void tick(MinecraftServer server) {
        // Show particles for legal moves if a piece is selected
        if (selectedSquare != null && !currentLegalMoves.isEmpty() && boardOrigin != null) {
            ServerWorld world = server.getOverworld(); // Defaulting to overworld for particles
            // Find world if possible, or just use first world
            for (ServerWorld w : server.getWorlds()) {
                for (Move.LegalMove lm : currentLegalMoves) {
                    double px = boardOrigin.getX() + lm.to.x * 2 + 1.0;
                    double pz = boardOrigin.getZ() + lm.to.y * 2 + 1.0;
                    double py = boardOrigin.getY() + 1.2;
                    
                    // Show a few particles at each legal move location
                    w.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }

        if (activeAnimations.isEmpty()) return;

        List<String> finished = new ArrayList<>();
        for (MoveAnimation anim : activeAnimations.values()) {
            anim.currentTick++;
            double t = (double) anim.currentTick / 20.0; // 1 second (20 ticks), linear

            double x = anim.startX + (anim.endX - anim.startX) * t;
            double y = anim.startY + (anim.endY - anim.startY) * t;
            double z = anim.startZ + (anim.endZ - anim.startZ) * t;

            double hop = Math.sin(t * Math.PI) * 1.0;
            double curY = y + hop;

            ServerPlayerEntity rider = anim.playerUuid != null ? server.getPlayerManager().getPlayer(anim.playerUuid) : null;
            if (rider != null) {
                // First tick: Set to 3rd person view
                if (anim.currentTick == 1) {
                    ServerPlayNetworking.send(rider, new StasisChess.PerspectivePacketPayload(1)); // 3rd Person Back
                }

                // Teleport rider slightly above the piece to simulate riding
                // Face the movement direction (anim.yaw) and look down slightly (25 pitch)
                rider.teleport(rider.getServerWorld(), x, curY + 1.2, z, 
                               Collections.emptySet(), 
                               anim.yaw, 25.0f);
                
                // Keep the rider looking forward/down at the board or in the movement direction
                // (Already handled by keeping X_ROT/Y_ROT flags)
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

            if (anim.currentTick >= 20) {
                finished.add(anim.pieceId);
                // End of animation: Restore 1st person view
                if (rider != null) {
                    ServerPlayNetworking.send(rider, new StasisChess.PerspectivePacketPayload(0)); // 1st Person
                }
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
    
    public void saveArea(ServerWorld world, BlockPos origin, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        // If there's an active game, reset it (and restore its blocks) before saving the new area
        // This prevents the chess board itself from being saved as the 'original' state
        // if the player creates a new board while one is already active.
        // We use a temporary flag or check activeGameId.
        // However, resetGame is called by startNewGame too.
        
        // Logical flow should be:
        // 1. User clicks start_tool.
        // 2. start_tool calls resetGame(player) -> restores old blocks.
        // 3. start_tool calls saveArea(...) -> saves current (restored) blocks.
        // 4. start_tool places new blocks.
        // 5. start_tool calls startNewGame(...) -> which currently calls resetGame again.
        
        // Let's refine the sequence in start_tool and MinecraftChessManager.
        savedBlocks.clear();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    savedBlocks.put(pos, world.getBlockState(pos));
                }
            }
        }
    }

    public void restoreArea(ServerWorld world) {
        if (savedBlocks.isEmpty()) return;
        for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue());
        }
        savedBlocks.clear();
    }

    public void resetGame(ServerPlayerEntity player) {
        if (player == null) return;

        player.sendMessage(Text.literal("§c[StasisChess] 보드 삭제 및 초기화 중..."), false);
        
        ServerWorld world = player.getServerWorld();
        var server = player.getServer();

        // 1. Clear all chess-related entities in one pass across all worlds
        if (server != null) {
            for (ServerWorld w : server.getWorlds()) {
                for (Entity e : w.iterateEntities()) {
                    Set<String> tags = e.getCommandTags();
                    if (!tags.isEmpty()) {
                        for (String tag : tags) {
                            if (tag.startsWith("sc_game_") || tag.startsWith("sc_piece_") || 
                                tag.startsWith("sc_pocket") || tag.startsWith("sc_status")) {
                                e.discard();
                                break; // Found a matching tag, move to next entity
                            }
                        }
                    }
                }
            }
        }
        
        // 2. Explicitly clear all internal entity tracking maps
        pieceEntities.clear();
        pocketEntities.clear();
        statusEntity = null;
        activeAnimations.clear();
        
        // 3. Restore blocks
        restoreArea(world);

        // 4. Reset state variables
        this.activeGameId = null;
        this.boardOrigin = null;
        this.selectedSquare = null;
        this.currentLegalMoves.clear();
        this.selectedPocketIndex = -1;
        
        player.sendMessage(Text.literal("§c[StasisChess] Game and Board Reset!"), false);
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

    public void showTurnActions(ServerPlayerEntity player) {
        if (activeGameId == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        List<Move.Action> actions = engine.getTurnActions(activeGameId);
        if (actions.isEmpty()) {
            player.sendMessage(Text.literal("§7No actions taken this turn."), false);
            return;
        }

        player.sendMessage(Text.literal("§e§lActions this turn:"), false);
        for (Move.Action action : actions) {
            String msg = switch (action.type) {
                case PLACE -> String.format("§7- Placed piece at %s", action.to);
                case MOVE -> String.format("§7- Moved piece from %s to %s", action.from, action.to);
                case CROWN -> String.format("§7- Crowned piece %s", action.pieceId);
                case DISGUISE -> String.format("§7- Disguised piece %s as %s", action.pieceId, action.asKind);
                case STUN -> String.format("§7- Stunned piece %s (Amount: %d)", action.pieceId, action.stunAmount);
            };
            player.sendMessage(Text.literal(msg), false);
        }
    }
}

