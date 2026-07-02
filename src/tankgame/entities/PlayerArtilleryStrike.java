package tankgame.entities;

// 玩家火炮打击
public class PlayerArtilleryStrike {
    public double targetX, targetY;
    public double remainingFrames;
    public double strikeRadius;
    public PlayerArtilleryStrike(double tx, double ty, int frames, double radius) {
        this.targetX = tx; this.targetY = ty;
        this.remainingFrames = frames;
        this.strikeRadius = radius;
    }
}
