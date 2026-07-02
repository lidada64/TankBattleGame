package tankgame.effects;

import javafx.scene.paint.Color;
import tankgame.Launcher;

// 飘字：向上飘 + 渐隐（击杀 +1 / +5 等）
public class FloatText {
    public final Launcher game;
    public double x, y, size;
    public double life, maxLife;
    public String text;
    public Color color;

    public FloatText(Launcher game, double x, double y, String text, Color color, double size) {
        this.game = game;
        this.x = x; this.y = y; this.text = text; this.color = color; this.size = size;
        this.life = 45; this.maxLife = 45;
    }

    public void update() { y -= 0.9 * game.dt; life -= game.dt; }
    public boolean alive() { return life > 0; }
    public double alpha() { return Math.max(0, life / maxLife); }
}
