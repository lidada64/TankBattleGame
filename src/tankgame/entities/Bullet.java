package tankgame.entities;

import static tankgame.Launcher.*;

import java.util.ArrayList;

import javafx.scene.paint.Color;
import tankgame.Launcher;

public class Bullet {
    public final Launcher game;
    public double x, y;
    public double vx, vy;
    public boolean canBounce;
    public int type;
    public boolean isEnemyBullet = false;
    public Color color = Color.RED;
    public int radius = 5;
    public double life = 300;
    public boolean isActive = true;
    public int speed;
    public boolean isHarmful;
    public int damage = 1;

    public Bullet(Launcher game, double x, double y, double angle, boolean bounce, int type, boolean harmful, int speed) {
        this.game = game;
        this.x = x;
        this.y = y;
        this.canBounce = bounce;
        this.type = type;
        this.isHarmful = harmful;
        this.speed = speed;
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;
    }

    public void move(ArrayList<Wall> walls) {
        double actualVx = vx;
        double actualVy = vy;

        if (game.player != null && game.player.type == TANK_ARTILLERY && game.player.isCharging) {
            actualVx *= 0.25;
            actualVy *= 0.25;
        }

        x += actualVx * game.dt;
        y += actualVy * game.dt;
        life -= game.dt;
        if (life <= 0) isActive = false;
        for (Wall w : walls) {
            if (w.intersects(x - radius, y - radius, radius * 2, radius * 2)) {
                if (canBounce) {
                    if (x - radius < w.x || x + radius > w.x + w.width) {
                        vx = -vx;
                    } else {
                        vy = -vy;
                    }
                    canBounce = false;
                    game.spawnSparks(x, y, 5);
                    if (game.sound != null) game.sound.play("ricochet", 0.4);
                    break;
                } else {
                    isActive = false;
                    game.spawnSparks(x, y, 4);            // 打墙火花
                    if (type == TANK_ARTILLERY) game.spawnSmoke(x, y, 2);
                    break;
                }
            }
        }
    }
}
