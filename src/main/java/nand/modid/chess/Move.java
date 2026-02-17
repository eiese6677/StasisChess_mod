package nand.modid.chess;

public class Move {
    public final int fromX, fromY;
    public final int toX, toY;

    public Move(int fx,int fy,int tx,int ty){
        this.fromX=fx;
        this.fromY=fy;
        this.toX=tx;
        this.toY=ty;
    }
}