package tankgame.entities;

import static tankgame.Launcher.*;

import java.util.ArrayList;

import javafx.scene.paint.Color;
import tankgame.Launcher;

public class Tank {
    public final Launcher game;
    public double x, y;
    public double angle;
    public int type;
    public Color color;
    public int size = 34;

    public double cooldown = 0;
    public int burstCount = 0;
    public double burstCoolDown = 0;

    public boolean isCharging = false;
    public double chargeTimer = 0;
    public double chargeTargetX, chargeTargetY;

    // ---- 动画字段 ----
    public double muzzleTimer = 0;    // 炮口闪光计时
    public double recoil = 0;         // 开火后坐位移
    public double hitFlash = 0;       // 受击白闪计时
    public double spawnTimer = 0;     // 出场放大动画剩余帧
    public double spawnMax = 0;       // 出场动画总帧
    public double prevX, prevY;       // 上一帧位置（判断是否移动）
    public double bodyAngle;          // 车身朝向（跟随行进方向，独立于炮塔瞄准）
    public double treadPhase;         // 履带滚动相位
    public boolean movedThisFrame;    // 本帧是否在移动
    public double dustTimer;          // 扬尘节流

    public Tank(Launcher game, double x, double y, int type) {
        this.game = game;
        this.x = x;
        this.y = y;
        this.type = type;
        if (type == TANK_NORMAL) this.color = Color.GREEN;
        else if (type == TANK_SHOTGUN) this.color = Color.YELLOW;
        else this.color = Color.BLUE;
    }

    public void update() {
        if (cooldown > 0) cooldown -= game.dt;
        if (burstCoolDown > 0) burstCoolDown -= game.dt;

        // ---- 动画计时（全部 dt 缩放）----
        if (muzzleTimer > 0) muzzleTimer -= game.dt;
        if (recoil > 0.1) recoil *= Math.pow(0.78, game.dt); else recoil = 0;
        if (hitFlash > 0) hitFlash -= game.dt;
        if (spawnTimer > 0) spawnTimer -= game.dt;

        // 移动检测：车身朝向、履带滚动相位、行进扬尘
        double mvx = x - prevX, mvy = y - prevY;
        double dist = Math.sqrt(mvx * mvx + mvy * mvy);
        movedThisFrame = dist > 0.3;
        if (movedThisFrame) {
            bodyAngle = Math.atan2(mvy, mvx);
            treadPhase += dist;
            dustTimer -= game.dt;
            if (dustTimer <= 0) {
                dustTimer = 4;
                game.spawnDust(x - Math.cos(bodyAngle) * 16, y - Math.sin(bodyAngle) * 16);
            }
        }
        prevX = x; prevY = y;

        if (isCharging) {
            chargeTimer -= game.dt;
            if (chargeTimer <= 0) {
                isCharging = false;
                fireArtilleryBullet();
            }
        }
    }

    public void move(double dx, double dy, ArrayList<Wall> walls) {
        if (isCharging) return;
        double nextX = x + dx;
        double nextY = y + dy;
        if (!game.isCollidingWithWalls(nextX, y, size)) {
            x = nextX;
        }
        if (!game.isCollidingWithWalls(x, nextY, size)) {
            y = nextY;
        }
    }

    private void fireArtilleryBullet() {
        double angleToTarget = Math.atan2(chargeTargetY - y, chargeTargetX - x);
        Bullet b = new Bullet(game, x, y, angleToTarget, false, TANK_ARTILLERY, true, 26);
        b.isEnemyBullet = (this instanceof EnemyTank);
        b.color = Color.BLUE;
        game.bullets.add(b);
        if (game.sound != null) game.sound.play("artillery_fire");
        muzzleTimer = 6; recoil = 8; game.shakeMag = Math.max(game.shakeMag, 4);
    }
}
