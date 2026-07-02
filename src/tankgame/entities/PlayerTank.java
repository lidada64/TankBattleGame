package tankgame.entities;

import static tankgame.Launcher.*;

import java.util.ArrayList;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import tankgame.Launcher;

public class PlayerTank extends Tank {
    public double invincibleTimer = 0;
    public int health = 3;

    public PlayerTank(Launcher game, double x, double y, int type) {
        super(game, x, y, type);
    }

    @Override
    public void update() {
        super.update();
        if (invincibleTimer > 0) {
            invincibleTimer -= game.dt;
        }
        if (health <= 0 && !game.dying) {
            game.killPlayer();   // 走死亡演出（爆炸+碎片+红屏），演出结束后进入结算
        }
    }

    public void takeDamage(int amount) {
        if (invincibleTimer > 0) return;
        if (game.selectedBuff == 1 && game.safeZone != null && game.safeZone.active && game.safeZone.contains(this.x, this.y)) {
            return;
        }
        health -= amount;
        if (health < 0) health = 0;
        if (health == 0) {
            game.killPlayer();   // 走死亡演出，而非立即结算
        } else {
            invincibleTimer = 90; // 受击后短暂金身，避免连续判定（Lidada）
        }
    }

    public void shoot(ArrayList<Bullet> bList, Point2D target) {
        if (isCharging) return;

        int bulletDamage = (game.selectedBuff == 2) ? 2 : 1;

        if (type == TANK_NORMAL) {
            if (burstCoolDown > 0) { if (game.sound != null) game.sound.play("empty_click"); return; }
            if (cooldown <= 0) {
                Bullet b = new Bullet(game, x, y, angle, true, TANK_NORMAL, true, 6);
                b.isEnemyBullet = false;
                b.color = Color.RED;
                b.damage = bulletDamage;
                bList.add(b);
                if (game.sound != null) game.sound.play("shoot_normal");
                muzzleTimer = 4; recoil = 4; game.spawnCasing(x, y, angle);
                burstCount++;
                cooldown = 12;
                if (burstCount >= 5) {
                    burstCoolDown = 60;
                    burstCount = 0;
                }
            }
        } else if (type == TANK_SHOTGUN) {
            if (cooldown <= 0) {
                double startAngle = angle - 0.3;
                for (int i = 0; i < 5; i++) {
                    Bullet b = new Bullet(game, x, y, startAngle + (i * 0.15), false, TANK_SHOTGUN, true, 6);
                    b.isEnemyBullet = false;
                    b.color = Color.YELLOW;
                    b.life = 25;
                    b.damage = bulletDamage;
                    bList.add(b);
                }
                if (game.sound != null) game.sound.play("shoot_shotgun");
                muzzleTimer = 5; recoil = 6; game.spawnCasing(x, y, angle);
                cooldown = 60;
            }
        } else if (type == TANK_ARTILLERY) {
            if (cooldown <= 0) {
                double targetX = target.getX();
                double targetY = target.getY();

                int wallCount = game.countWallsBetween(this.x, this.y, targetX, targetY);
                int totalFrames = 40 + (wallCount * 24);
                if (totalFrames > 210) totalFrames = 210;

                double addedRadius = wallCount * 10;
                if (addedRadius > 40) addedRadius = 40;
                double finalRadius = 80 + addedRadius;

                game.playerStrikes.add(new PlayerArtilleryStrike(targetX, targetY, totalFrames, finalRadius));
                if (game.sound != null) game.sound.play("artillery_fire");
                muzzleTimer = 6; recoil = 8;
                cooldown = 120;
            }
        }
    }
}
