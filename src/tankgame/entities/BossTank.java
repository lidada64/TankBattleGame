package tankgame.entities;

import static tankgame.Launcher.*;

import javafx.scene.paint.Color;
import tankgame.Launcher;

// ========== Boss：红色（传送 + 散射） ==========
public class BossTank extends EnemyTank {
    public double scatterTimer = 180;
    public double teleportCooldown = 240;
    public double teleportTimer = 0;
    public boolean isTeleporting = false;
    public double tpTargetX, tpTargetY;

    public BossTank(Launcher game, double x, double y) {
        super(game, x, y, TANK_NORMAL);
        this.color = Color.RED;
        this.size = 50;
        this.hp = 5;
        this.maxHp = 5;
        this.spawnTimer = 40; this.spawnMax = 40;   // Boss 入场放大更慢更夸张
    }

    @Override
    public void update() {
        if (isTeleporting) {
            teleportTimer -= game.dt;
            if (teleportTimer <= 0) {
                this.x = tpTargetX;
                this.y = tpTargetY;
                this.isTeleporting = false;
                this.teleportCooldown = 240;
            }
            return;
        }

        super.update();
        scatterTimer -= game.dt;
        if (scatterTimer <= 0) {
            fireScatterBullets();
            scatterTimer = 180;
        }

        teleportCooldown -= game.dt;
        if (teleportCooldown <= 0) {
            startTeleport();
        }
    }

    private void startTeleport() {
        int attempts = 0;
        double rx = this.x, ry = this.y;
        while (attempts < 50) {
            rx = game.random.nextInt(WIDTH - 200) + 100;
            ry = game.random.nextInt(HEIGHT - 200) + 100;
            if (!game.isCollidingWithWalls(rx, ry, this.size)) {
                break;
            }
            attempts++;
        }
        this.tpTargetX = rx;
        this.tpTargetY = ry;
        this.isTeleporting = true;
        this.teleportTimer = 120;
    }

    private void fireScatterBullets() {
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI / 4);
            Bullet b = new Bullet(game, x, y, angle, true, TANK_SHOTGUN, true, 3);
            b.isEnemyBullet = true;
            b.life = 180;
            game.bullets.add(b);
        }
        if (game.sound != null) game.sound.play("boss_fire");
        muzzleTimer = 6; recoil = 6; game.shakeMag = Math.max(game.shakeMag, 5);
    }
}
