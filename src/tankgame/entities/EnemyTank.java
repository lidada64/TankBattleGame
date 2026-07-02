package tankgame.entities;

import static tankgame.Launcher.*;

import java.util.ArrayList;

import javafx.scene.paint.Color;
import tankgame.Launcher;

// ========== 敌方坦克 ==========
public class EnemyTank extends Tank {
    public int hp = 1;
    public int maxHp = 1;              // Boss血条用
    public double displayHp = -1;      // 血条缓降的显示值(懒初始化为hp)
    public double hitShakeTimer = 0;   // 受击抖动剩余帧
    private int aiState = 0;
    private double stateTimer = 0;
    @SuppressWarnings("unused")
    private double escapeAngle = 0;

    public EnemyTank(Launcher game, double x, double y, int type) {
        super(game, x, y, type);
        this.color = Color.GREEN;
        this.spawnTimer = 16; this.spawnMax = 16;   // 出场弹入动画
    }

    @Override
    public void move(double dx, double dy, ArrayList<Wall> walls) {
        if (isCharging) return;
        double targetNextX = x + dx;
        double targetNextY = y + dy;

        boolean hitWallX = game.isCollidingWithWalls(targetNextX, y, size);
        boolean hitWallY = game.isCollidingWithWalls(x, targetNextY, size);
        boolean hitEnemyX = game.isCollidingWithOtherEnemies(this, targetNextX, y);
        boolean hitEnemyY = game.isCollidingWithOtherEnemies(this, x, targetNextY);

        if (!hitWallX && !hitEnemyX) {
            x = targetNextX;
        } else if (hitWallX && !hitEnemyX) {
            double slideY = (dy != 0) ? dy : (game.random.nextBoolean() ? 1.5 * game.dt : -1.5 * game.dt);
            if (!game.isCollidingWithWalls(x, y + slideY, size)) y += slideY;
        }

        if (!hitWallY && !hitEnemyY) {
            y = targetNextY;
        } else if (hitWallY && !hitEnemyY) {
            double slideX = (dx != 0) ? dx : (game.random.nextBoolean() ? 1.5 * game.dt : -1.5 * game.dt);
            if (!game.isCollidingWithWalls(x + slideX, y, size)) x += slideX;
        }

        if ((hitWallX || hitEnemyX) && (hitWallY || hitEnemyY)) {
            if (aiState != 1) {
                aiState = 1;
                stateTimer = 90;
                escapeAngle = Math.atan2(-dy, -dx) + (game.random.nextDouble() * 1.0 - 0.5);
            }
        }
    }

    public void updateAI(PlayerTank player, ArrayList<Bullet> bList, ArrayList<Wall> wList) {
        if (player == null) return;
        if (this instanceof BossTank && ((BossTank) this).isTeleporting) {
            return;
        }

        double distToPlayer = game.getDistance(x, y, player.x, player.y);
        stateTimer -= game.dt;
        Bullet threat = null;
        for (Bullet b : bList) {
            if (!b.isEnemyBullet && game.getDistance(x, y, b.x, b.y) < 150) {
                threat = b;
                break;
            }
        }

        if (threat != null && stateTimer <= 0) {
            aiState = 1;
            stateTimer = 40;
        } else if (stateTimer <= 0) {
            aiState = 0;
            stateTimer = game.random.nextInt(50) + 30;
        }

        double baseMoveSpeed = (aiState == 0) ? 1.0 * SPEED_MULTIPLIER * game.dt : 1.6 * SPEED_MULTIPLIER * game.dt;

        if (player.type == TANK_ARTILLERY && player.isCharging) {
            baseMoveSpeed *= 0.25;
        }

        double mx = 0, my = 0;
        if (aiState == 0) {
            mx = (player.x > x) ? baseMoveSpeed : -baseMoveSpeed;
            my = (player.y > y) ? baseMoveSpeed : -baseMoveSpeed;
            angle = Math.atan2(player.y - y, player.x - x);
        } else {
            mx = (player.y > y) ? -baseMoveSpeed : baseMoveSpeed;
            my = (player.x > x) ? baseMoveSpeed : -baseMoveSpeed;
        }

        if (game.getDistance(x + mx, y + my, player.x, player.y) > 45) {
            move(mx, my, wList);
        }

        if (!isCharging && cooldown <= 0 && distToPlayer < 500 && game.random.nextDouble() < 0.04 * game.dt) {
            enemyFire(bList, player);
        }
    }

    private void enemyFire(ArrayList<Bullet> bList, PlayerTank p) {
        double enemyAngle = Math.atan2(p.y - y, p.x - x);
        if (type == TANK_NORMAL) {
            Bullet b = new Bullet(game, x, y, enemyAngle, true, TANK_NORMAL, true, 6);
            b.isEnemyBullet = true;
            b.color = this.color;
            bList.add(b);
            if (game.sound != null) game.sound.play("shoot_normal", 0.3);
            muzzleTimer = 4; recoil = 4; game.spawnCasing(x, y, angle);
            cooldown = 45;
        } else if (type == TANK_SHOTGUN) {
            double sAngle = enemyAngle - 0.3;
            for (int i = 0; i < 5; i++) {
                Bullet b = new Bullet(game, x, y, sAngle + (i * 0.15), false, TANK_SHOTGUN, true, 6);
                b.isEnemyBullet = true;
                b.color = this.color;
                b.life = 25;
                bList.add(b);
            }
            if (game.sound != null) game.sound.play("shoot_shotgun", 0.3);
            muzzleTimer = 5; recoil = 6; game.spawnCasing(x, y, angle);
            cooldown = 85;
        } else if (type == TANK_ARTILLERY) {
            isCharging = true;
            chargeTimer = 90;
            chargeTargetX = p.x;
            chargeTargetY = p.y;
            if (game.sound != null) game.sound.play("charge", 0.25);
            cooldown = 160;
        }
    }
}
