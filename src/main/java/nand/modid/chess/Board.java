package nand.modid.chess;

import nand.modid.chess.Piece;

public class Board {
    private Piece[][] board = new Piece[8][8];

    public Piece get(int x, int y) {
        return board[x][y];
    }

    public void set(int x, int y, Piece p) {
        board[x][y] = p;
    }

    public Board getBoard() {
        return null;
    }
}