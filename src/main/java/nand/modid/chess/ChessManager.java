package nand.modid.chess;

import net.minecraft.entity.player.PlayerEntity;

public class ChessManager {
    public static Board getGame(PlayerEntity player){
        return new Board();
    }
}
