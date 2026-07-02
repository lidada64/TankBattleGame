package tankgame.entities;

import static tankgame.Launcher.*;

import java.util.ArrayList;

import javafx.scene.paint.Color;
import tankgame.Launcher;

// ========== Boss：粉色 ==========
public class PinkBoss extends EnemyTank {
    public PinkBoss(Launcher game, double x, double y) {
        super(game, x, y, TANK_NORMAL);
        this.color = Color.PINK;
        this.size = 50;
        this.hp = 5;
        this.maxHp = 5;
        this.spawnTimer = 40; this.spawnMax = 40;   // Boss 入场放大更慢更夸张
    }

    @Override
    public void updateAI(PlayerTank player, ArrayList<Bullet> bList, ArrayList<Wall> wList) {
        if (player == null) return;

        double baseMoveSpeed = 1.2 * game.dt;
        double mx = (player.x > x) ? baseMoveSpeed : -baseMoveSpeed;
        double my = (player.y > y) ? baseMoveSpeed : -baseMoveSpeed;
        if (game.getDistance(x + mx, y + my, player.x, player.y) > 45) {
            move(mx, my, wList);
        }
        angle = Math.atan2(player.y - y, player.x - x);

        if (cooldown <= 0) {
            double[] baseAngles = {0, Math.PI/4, Math.PI/2, 3*Math.PI/4,
                    Math.PI, 5*Math.PI/4, 3*Math.PI/2, 7*Math.PI/4};
            for (double base : baseAngles) {
                for (int i = 0; i < 5; i++) {
                    double offset = (i - 2) * 0.05;
                    double angle = base + offset;
                    Bullet b = new Bullet(game, x, y, angle, false, TANK_NORMAL, true, 3);
                    b.isEnemyBullet = true;
                    b.color = Color.PINK;
                    b.life = 180;
                    bList.add(b);
                }
            }
            if (game.sound != null) game.sound.play("boss_fire");
            muzzleTimer = 6; game.shakeMag = Math.max(game.shakeMag, 5);
            cooldown = 180;
        }
    }
}
