package tankgame.effects;

import tankgame.Launcher;

public class ExplosionEffect {
    public final Launcher game;
    public double x, y;
    public double timer = 90;
    public boolean active = true;

    public ExplosionEffect(Launcher game, double x, double y) {
        this.game = game;
        this.x = x;
        this.y = y;
    }

    public void update() {
        timer -= game.dt;
        if (timer <= 0) active = false;
    }
}
