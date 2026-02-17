package nand.modid.comand;

import com.mojang.brigadier.context.CommandContext;
import nand.modid.chess.Board;
import nand.modid.chess.ChessGame;
import nand.modid.chess.ChessManager;
import nand.modid.world.ChessBoardRenderer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class ChessCommand {
    public static int renderCommand(CommandContext<ServerCommandSource> ctx) {
    ServerPlayerEntity player = ctx.getSource().getPlayer();
    ServerWorld world = player.getServerWorld();
    BlockPos base = player.getBlockPos();

    Board game = ChessManager.getGame(player); // 네 엔진
    ChessBoardRenderer.render(game.getBoard(), world, base);

    return 1;
}}