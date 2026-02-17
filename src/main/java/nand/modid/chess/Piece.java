package nand.modid.chess;

import java.util.List;

public abstract class Piece {
    public boolean white;

    public Piece(boolean white) {
        this.white = white;
    }

    public abstract List<Move> getPossibleMoves(Board board, int x, int y);

    public abstract String getId();
}