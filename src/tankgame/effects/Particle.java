package tankgame.effects;

import javafx.scene.paint.Color;
import tankgame.Launcher;

// 通用粒子（碎片/火花/火光/烟雾/扬尘/弹壳），全部 dt 缩放，速度不随帧率变化
public class Particle {
    public final Launcher game;
    public double x, y, vx, vy, size;
    public double life, maxLife;
    public Color color;
    public double gravity;   // 正=下落，负=上飘（烟雾）
    public double growth;    // 每帧尺寸变化，负=收缩，正=膨胀

    public Particle(Launcher game, double x, double y, double vx, double vy, double life, double size, Color color,
                    double gravity, double growth) {
        this.game = game;
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.life = life; this.maxLife = life; this.size = size; this.color = color;
        this.gravity = gravity; this.growth = growth;
    }

    public void update() {
        x += vx * game.dt; y += vy * game.dt;
        double drag = Math.pow(0.92, game.dt);   // 阻力减速（帧率无关）
        vx *= drag; vy *= drag;
        vy += gravity * game.dt;
        size += growth * game.dt; if (size < 0) size = 0;
        life -= game.dt;
    }

    public boolean alive() { return life > 0; }
    public double alpha() { return Math.max(0, life / maxLife); }
}
