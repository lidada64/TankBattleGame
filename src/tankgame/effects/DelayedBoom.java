package tankgame.effects;

// 延时爆炸（Boss 连环爆用）
public class DelayedBoom {
    public double x, y, power;
    public double delay;

    public DelayedBoom(double x, double y, double delay, double power) {
        this.x = x; this.y = y; this.delay = delay; this.power = power;
    }
}
