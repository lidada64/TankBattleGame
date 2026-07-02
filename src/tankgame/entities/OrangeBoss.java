package tankgame.entities;

import static tankgame.Launcher.*;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import tankgame.Launcher;

// ========== Boss：橙色 ==========
public class OrangeBoss extends EnemyTank {
    public boolean hasShield = true;
    public double shieldCooldown = 720;
    public double flameCooldown = 0;
    public double flameWarningTimer = 0;
    public boolean flameActive = false;
    public double flameDuration = 0;
    public double flameLength = 270;
    public double flameWidth;
    public double flameDamageTimer = 0;

    public OrangeBoss(Launcher game, double x, double y) {
        super(game, x, y, TANK_NORMAL);
        this.color = Color.ORANGE;
        this.size = 50;
        this.hp = 5;
        this.maxHp = 5;
        this.flameWidth = this.size;
        hasShield = true;
        shieldCooldown = 720;
        this.spawnTimer = 40; this.spawnMax = 40;   // Boss 入场放大更慢更夸张
    }

    @Override
    public void update() {
        super.update();

        if (!hasShield) {
            shieldCooldown -= game.dt;
            if (shieldCooldown <= 0) {
                hasShield = true;
                shieldCooldown = 720;
            }
        }

        if (flameCooldown > 0) {
            flameCooldown -= game.dt;
            if (flameCooldown < 0) flameCooldown = 0;
        }

        if (flameWarningTimer > 0) {
            flameWarningTimer -= game.dt;
            if (flameWarningTimer <= 0) {
                flameWarningTimer = 0;
                flameActive = true;
                flameDuration = 120;
            }
        }

        if (flameActive) {
            flameDuration -= game.dt;
            if (flameDuration <= 0) {
                flameActive = false;
                flameCooldown = 180;
            }
        }

        if (flameActive && game.player != null && isPointInFlameRect(game.player.x, game.player.y)) {
            flameDamageTimer += game.dt;
            if (flameDamageTimer >= 30) {
                flameDamageTimer = 0;
                if (game.player.invincibleTimer <= 0) {
                    game.player.takeDamage(1);
                }
            }
        } else {
            flameDamageTimer = 0;
        }

        if (flameCooldown == 0 && flameWarningTimer == 0 && !flameActive) {
            flameWarningTimer = 90;
        }
    }

    private boolean isPointInFlameRect(double px, double py) {
        double dx = px - x;
        double dy = py - y;
        double cos = Math.cos(-angle);
        double sin = Math.sin(-angle);
        double localX = dx * cos - dy * sin;
        double localY = dx * sin + dy * cos;
        return (localX >= 0 && localX <= flameLength &&
                Math.abs(localY) <= flameWidth / 2);
    }

    public double[][] getFlameCorners() {
        double halfLen = flameLength / 2;
        double halfWid = flameWidth / 2;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double cx = x + cos * halfLen;
        double cy = y + sin * halfLen;
        double[][] corners = new double[4][2];
        double[][] offsets = {
                {-halfLen, -halfWid}, { halfLen, -halfWid},
                { halfLen,  halfWid}, {-halfLen,  halfWid}
        };
        for (int i = 0; i < 4; i++) {
            double offX = offsets[i][0];
            double offY = offsets[i][1];
            corners[i][0] = cx + offX * cos - offY * sin;
            corners[i][1] = cy + offX * sin + offY * cos;
        }
        return corners;
    }

    public void draw(GraphicsContext gc) {
        if (hasShield) {
            gc.setStroke(Color.CYAN);
            gc.setLineWidth(3);
            gc.strokeOval(x - 35, y - 35, 70, 70);
            gc.setStroke(Color.rgb(0, 255, 255, 0.3));
            gc.setLineWidth(1);
            gc.strokeOval(x - 40, y - 40, 80, 80);
        }

        if (flameWarningTimer > 0) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.setLineDashes(6);
            double[][] corners = getFlameCorners();
            double[] xs = new double[5];
            double[] ys = new double[5];
            for (int i = 0; i < 4; i++) {
                xs[i] = corners[i][0];
                ys[i] = corners[i][1];
            }
            xs[4] = xs[0];
            ys[4] = ys[0];
            gc.strokePolygon(xs, ys, 5);
            gc.setLineDashes(null);
        }

        if (flameActive) {
            gc.setFill(Color.rgb(255, 165, 0, 0.5));
            double[][] corners = getFlameCorners();
            double[] xs = new double[4];
            double[] ys = new double[4];
            for (int i = 0; i < 4; i++) {
                xs[i] = corners[i][0];
                ys[i] = corners[i][1];
            }
            gc.fillPolygon(xs, ys, 4);
            gc.setStroke(Color.rgb(255, 100, 0, 0.8));
            gc.setLineWidth(2);
            gc.strokePolygon(xs, ys, 4);
        }
    }
}
