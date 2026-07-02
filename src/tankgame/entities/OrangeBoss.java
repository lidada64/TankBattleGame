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

        // ==================== 🔥 UI 终极升级：烈焰风暴粒子喷涌 (OrangeBoss 专属版) ====================
        if (flameActive) {
            double[][] corners = getFlameCorners();
            // 拿到四个角的绝对坐标：
            // corners[0] 和 corners[3] 是靠近 Boss 嘴边的起点
            // corners[1] 和 corners[2] 是喷火区域的最远端终点
            double startX = (corners[0][0] + corners[3][0]) / 2.0;
            double startY = (corners[0][1] + corners[3][1]) / 2.0;

            // 1. 【底色强光晕】利用你原有的 4 个顶点画一层极淡的渐变火焰底色，烘托空气受热变形的高温感
            gc.save();
            // 设置一层微弱的红橙色基础力场
            gc.setFill(Color.rgb(255, 100, 0, 0.12));
            double[] xs = {corners[0][0], corners[1][0], corners[2][0], corners[3][0]};
            double[] ys = {corners[0][1], corners[1][1], corners[2][1], corners[3][1]};
            gc.fillPolygon(xs, ys, 4);
            gc.restore();

            // 2. 【高密度动态喷涌火苗粒子群】（35颗粒子，完全利用数学公式，无需添加额外变量）
            for (int i = 0; i < 35; i++) {
                // 每颗火苗独特的喷射速度和生命周期比例 factor
                double speedFactor = 0.5 + (i % 4) * 0.25;
                // 粒子在火焰长度上的推进比例（0.0 到 1.0）
                double progress = (Launcher.animTick * 0.015 * speedFactor + i * 0.03) % 1.0;

                // 核心插值算法：让粒子从起点(startX, startY) 完美沿着 Boss 面向的扇形推演到远端
                double targetX = corners[0][0] + (corners[1][0] - corners[0][0]) * progress;
                double targetY = corners[0][1] + (corners[1][1] - corners[0][1]) * progress;
                double targetX2 = corners[3][0] + (corners[2][0] - corners[3][0]) * progress;
                double targetY2 = corners[3][1] + (corners[2][1] - corners[3][1]) * progress;

                // 在上下两条边界线之间随机/交错散开，并且加上高频火苗剧烈抖动
                double wave = Math.sin(Launcher.animTick * 0.3 + i);
                double ratio = (i % 5) / 5.0; // 粒子在宽度方向的分散度
                double pX = targetX + (targetX2 - targetX) * ratio + Math.cos(Launcher.animTick * 0.1 + i) * 2.0;
                double pY = targetY + (targetY2 - targetY) * ratio + wave * 3.5; // 火苗跳动

                // 3. 【三阶段高温渐变色控制】
                double age = progress; // 离 Boss 的距离百分比
                double pAlpha = Math.sin(age * Math.PI) * 0.95; // 喷出时淡入，末端平滑淡出

                if (pAlpha > 0) {
                    if (age < 0.25) {
                        // A. 【核心超高温区】：纯白金色粒子，个体较小，速度极快
                        gc.setFill(Color.color(1.0, 1.0, 0.8, pAlpha));
                        double size = 2.0 + (i % 2);
                        gc.fillRect(pX - size/2.0, pY - size/2.0, size, size);
                    } else if (age < 0.7) {
                        // B. 【中段炽热区】：爆裂的亮橙黄色，粒子体积最大
                        gc.setFill(Color.color(1.0, 0.55, 0.0, pAlpha));
                        double size = 4.5 + (i % 3);
                        gc.fillRect(pX - size/2.0, pY - size/2.0, size, size);
                    } else {
                        // C. 【尾部消散区】：暗红色的余烬粒子，即将消散
                        gc.setFill(Color.color(0.85, 0.1, 0.0, pAlpha * 0.6));
                        double size = 1.5 + (i % 2);
                        gc.fillRect(pX - size/2.0, pY - size/2.0, size, size);
                    }
                }
            }
        }
        // ============================================================================================
    }
}
