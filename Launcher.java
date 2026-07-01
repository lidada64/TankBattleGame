import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class Launcher extends Application {
    
    // EnemyTank 内部类
    class EnemyTank extends Tank {
        private int aiState = 0; 
        private int stateTimer = 0;
        @SuppressWarnings("unused")
        private double escapeAngle = 0;

        public EnemyTank(double x, double y, int type, Color c) {
            super(x, y, type);
            this.color = c;
            this.spawnTimer = 16; this.spawnMax = 16;   // 出场弹入动画
        }

        @Override
        public void move(double dx, double dy, ArrayList<Wall> walls) {
            if (isCharging) return;
            double targetNextX = x + dx;
            double targetNextY = y + dy;
            boolean hitWallX = isCollidingWithWalls(targetNextX, y, size);
            boolean hitWallY = isCollidingWithWalls(x, targetNextY, size);
            boolean hitEnemyX = isCollidingWithOtherEnemies(this, targetNextX, y);
            boolean hitEnemyY = isCollidingWithOtherEnemies(this, x, targetNextY);
            if (!hitWallX && !hitEnemyX) {
                x = targetNextX;
            } else if (hitWallX && !hitEnemyX) {
                double slideY = (dy != 0) ? dy : (random.nextBoolean() ? 1.2 : -1.2);
                if (!isCollidingWithWalls(x, y + slideY, size)) y += slideY;
            }

            if (!hitWallY && !hitEnemyY) {
                y = targetNextY;
            } else if (hitWallY && !hitEnemyY) {
                double slideX = (dx != 0) ? dx : (random.nextBoolean() ? 1.2 : -1.2);
                if (!isCollidingWithWalls(x + slideX, y, size)) x += slideX;
            }

            if ((hitWallX || hitEnemyX) && (hitWallY || hitEnemyY)) {
                if (aiState != 1) {
                    aiState = 1;
                    stateTimer = 90; 
                    escapeAngle = Math.atan2(-dy, -dx) + (random.nextDouble() * 1.0 - 0.5);
                }
            }
        }

        public void updateAI(PlayerTank player, ArrayList<Bullet> bList, ArrayList<Wall> wList) {
            if (player == null) return;
            double distToPlayer = getDistance(x, y, player.x, player.y);
            stateTimer--;
            Bullet threat = null;
            for (Bullet b : bList) {
                if (!b.isEnemyBullet && getDistance(x, y, b.x, b.y) < 150) { 
                    threat = b; 
                    break; 
                }
            }

            if (threat != null && stateTimer <= 0) { 
                aiState = 1; 
                stateTimer = 40; 
            } else if (stateTimer <= 0) { 
                aiState = 0; 
                stateTimer = random.nextInt(50) + 30; 
            }

            double baseMoveSpeed = (aiState == 0) ? 0.2 : 0.4;
            
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

            if (getDistance(x + mx, y + my, player.x, player.y) > 45) {
                move(mx, my, wList);
            }

            if (!isCharging && cooldown <= 0 && distToPlayer < 500 && random.nextInt(100) < 4) {
                enemyFire(bList, player);
            }
        }

        private void enemyFire(ArrayList<Bullet> bList, PlayerTank p) {
            double enemyAngle = Math.atan2(p.y - y, p.x - x);
            if (type == TANK_NORMAL) {
                Bullet b = new Bullet(x, y, enemyAngle, true, TANK_NORMAL, true, 6);
                b.isEnemyBullet = true;
                b.color = this.color;
                bList.add(b);
                if (sound != null) sound.play("shoot_normal", 0.3);
                muzzleTimer = 4;
                recoil = 4;
                spawnCasing(x, y, angle);
                cooldown = 45;
            } else if (type == TANK_SHOTGUN) {
                double sAngle = enemyAngle - 0.3;
                for (int i = 0; i < 5; i++) {
                    Bullet b = new Bullet(x, y, sAngle + (i * 0.15), false, TANK_SHOTGUN, true, 6);
                    b.isEnemyBullet = true;
                    b.color = this.color;
                    b.life = 25;
                    bList.add(b);
                }
                if (sound != null) sound.play("shoot_shotgun", 0.3);
                muzzleTimer = 5;
                recoil = 6;
                spawnCasing(x, y, angle);
                cooldown = 85;
            } else if (type == TANK_ARTILLERY) {
                isCharging = true;
                chargeTimer = 90;
                chargeTargetX = p.x;
                chargeTargetY = p.y;
                if (sound != null) sound.play("charge", 0.25);
                cooldown = 160;
            }
        }
    }

    // Boss 机理
    class BossTank extends EnemyTank {
        int hp = 5;
        int scatterTimer = 180;
        boolean landed = false;     // 入场放大结束的落地冲击只触发一次
        boolean enraged = false;    // 低血狂暴

        public BossTank(double x, double y) {
            super(x, y, TANK_NORMAL, Color.RED);
            this.size = 50;
            this.spawnTimer = 40; this.spawnMax = 40;   // Boss 入场放大更慢更夸张
        }

        @Override
        public void update() {
            super.update();
            // 入场放大结束的瞬间：落地冲击波 + 屏震 + 扬尘
            if (!landed && spawnTimer == 0) {
                landed = true;
                shakeMag = Math.max(shakeMag, 11);
                explosions.add(new ExplosionEffect(x, y));
                spawnSmoke(x, y, 10);
            }
            // 血量 ≤ 2 进入狂暴：散射更频繁，并冒黑烟示警
            enraged = hp <= 2;
            scatterTimer--;
            if (scatterTimer <= 0) {
                fireScatterBullets();
                scatterTimer = enraged ? 95 : 180;
            }
            if (enraged && animTick % 6 == 0) spawnSmoke(x, y - 10, 1);
        }

        private void fireScatterBullets() {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4);
                Bullet b = new Bullet(x, y, angle, true, TANK_SHOTGUN, true, 3);
                b.isEnemyBullet = true;
                b.life = 180;
                Launcher.this.bullets.add(b);
            }
            if (sound != null) sound.play("boss_fire");
            muzzleTimer = 6;
            recoil = 6;
            shakeMag = Math.max(shakeMag, 5);
        }
    }

    // 游戏状态
    private static final int STATE_MENU = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_GAMEOVER = 2;
    private int gameState = STATE_MENU;

    // 坦克类型
    private static final int TANK_NORMAL = 0;
    private static final int TANK_SHOTGUN = 1;
    private static final int TANK_ARTILLERY = 2;

    // 窗口尺寸
    public static final int WIDTH = 1000;
    public static final int HEIGHT = 700;

    // 游戏核心变量
    private int selectedTankType = TANK_NORMAL;
    private int score = 0;
    private int level = 1;
    private int showLevelTimer = 0;

    // 新增：开局3秒倒计时变量（60帧 = 1秒，180帧 = 3秒，再加30帧用于显示最后的"GO!"）
    private boolean isCountingDown = false;
    private int countdownTimer = 0;

    private PlayerTank player;
    private ArrayList<EnemyTank> enemies = new ArrayList<>();
    private ArrayList<Bullet> bullets = new ArrayList<>();
    private ArrayList<Wall> walls = new ArrayList<>();
    private ArrayList<ShieldItem> shields = new ArrayList<>();
    private ArrayList<ExplosionEffect> explosions = new ArrayList<>();
    
    // 空中支援列表
    private ArrayList<Airstrike> airstrikes = new ArrayList<>();

    // 输入与控制变量
    private boolean[] keys = new boolean[256];
    private Point2D mousePoint = new Point2D(0, 0);
    private Random random = new Random();
    private long lastShieldSpawnTime = 0;

    // 空中支援控制变量
    private int airstrikeCooldownTimer = 0; 
    private static final int AIRSTRIKE_CD_MAX = 900; 

    private Canvas canvas;

    // 音频管理器
    private SoundManager sound;

    // 动画相关
    private ArrayList<Particle> particles = new ArrayList<>();   // 碎片/火花粒子
    private ArrayList<FloatText> floatTexts = new ArrayList<>(); // 飘字（+1 得分等）
    private ArrayList<DelayedBoom> scheduledBooms = new ArrayList<>(); // 延时爆炸（Boss 连环爆）
    private double shakeMag = 0;                                  // 屏幕震动强度
    private int bossWarnTimer = 0;                                // Boss 登场预警视觉计时
    private long animTick = 0;                                    // 全局动画时钟（用于呼吸/闪烁等）
    private int hitStop = 0;                                      // 命中顿帧：暂停 N 帧逻辑
    private double screenFlash = 0;                               // 全屏染色强度（0~1）
    private Color screenFlashColor = Color.WHITE;                 // 全屏染色颜色
    private boolean dying = false;                                // 玩家阵亡演出中
    private int dyingTimer = 0;                                   // 阵亡演出剩余帧
    private static final int MAX_PARTICLES = 600;                 // 粒子上限（性能兜底）

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        scene.setOnKeyPressed(e -> {
            int code = getKeyCodeValue(e.getCode());
            if (code > 0 && code < keys.length) keys[code] = true;

            if (e.getCode() == KeyCode.DIGIT1 || e.getCode() == KeyCode.NUMPAD1) {
                triggerOrExplodeAirstrike();
            }
        });

        scene.setOnKeyReleased(e -> {
            int code = getKeyCodeValue(e.getCode());
            if (code > 0 && code < keys.length) keys[code] = false;
        });

        canvas.setOnMouseMoved(e -> mousePoint = new Point2D(e.getX(), e.getY()));
        canvas.setOnMouseDragged(e -> mousePoint = new Point2D(e.getX(), e.getY()));
        canvas.setOnMousePressed(e -> handleMousePressed(e.getX(), e.getY()));

        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (gameState == STATE_PLAYING) {
                    updateGame();
                }
                render(gc);
            }
        };
        gameLoop.start();

        lastShieldSpawnTime = System.currentTimeMillis();

        // 初始化音频并播放主菜单背景乐
        sound = new SoundManager();
        sound.playBgm("bgm_menu");

        // M 键全局静音切换
        scene.setOnKeyTyped(e -> {
            if ("m".equalsIgnoreCase(e.getCharacter())) sound.toggleMute();
        });

        primaryStage.setTitle("坦克大战高级战略版 (JavaFX)");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private int getKeyCodeValue(KeyCode code) {
        switch (code) {
            case W: return 87;
            case S: return 83;
            case A: return 65;
            case D: return 68;
            default: return -1;
        }
    }

    private void triggerOrExplodeAirstrike() {
        if (gameState != STATE_PLAYING || isCountingDown) return; // 倒计时期间禁止技能

        if (!airstrikes.isEmpty()) {
            sound.play("airstrike_call");          // 提前引爆
            for (Airstrike air : airstrikes) {
                air.isExploded = true;
            }
            return;
        }

        if (airstrikeCooldownTimer <= 0) {
            airstrikeCooldownTimer = AIRSTRIKE_CD_MAX;
            sound.play("airstrike_call");           // 呼叫
            sound.play("airstrike_incoming");       // 来袭呼啸
            for (int i = 0; i < 3; i++) {
                double rx = random.nextInt(WIDTH - 200) + 100;
                double ry = random.nextInt(HEIGHT - 200) + 100;
                airstrikes.add(new Airstrike(rx, ry));
            }
        }
    }

    private void startNewLevel(boolean resetScoreAndLevel) {
        if (resetScoreAndLevel) {
            score = 0;
            level = 1;
        }
        showLevelTimer = 90;
        airstrikeCooldownTimer = 0;

        // 进入战斗背景乐（已在播放则自动忽略）
        if (sound != null) sound.playBgm("bgm_battle");

        // 清空上一关残留的动画
        particles.clear();
        floatTexts.clear();
        scheduledBooms.clear();
        shakeMag = 0;
        screenFlash = 0;
        bossWarnTimer = 0;
        hitStop = 0;
        dying = false; dyingTimer = 0;

        // 开启 3 秒开局倒计时 (180帧为倒数 3,2,1；多加30帧用于显示"GO!")
        isCountingDown = true;
        countdownTimer = 210; 

        // 清空按键缓存，防止上一关的按键残余导致开局坦克乱跑
        for (int i = 0; i < keys.length; i++) keys[i] = false;

        bullets.clear();
        enemies.clear();
        shields.clear();
        explosions.clear();
        airstrikes.clear(); 

        generateRandomWalls();

        int px = 100, py = HEIGHT / 2;
        player = new PlayerTank(px, py, selectedTankType);

        if (level % 4 == 0) {
            BossTank boss = new BossTank(WIDTH / 2.0, HEIGHT / 2.0);
            enemies.add(boss);
            bossWarnTimer = 180;            // Boss 登场预警视觉（约 3 秒）
            if (sound != null) sound.playBossWarning();   // 8 秒登场曲（压低战斗 BGM）
            int minionCount = (level / 4) - 1;
            for (int i = 0; i < minionCount; i++) {
                enemies.add(new EnemyTank(200 + (i * 150), 200 + (i * 150), random.nextInt(3), Color.ORANGE));
            }
        } else {
            int enemyCount = 0;
            if (level == 1) enemyCount = 1;
            else if (level == 2) enemyCount = 2;
            else if (level == 3) enemyCount = 3;
            else if (level == 5) enemyCount = 5;
            else enemyCount = 6;

            for (int i = 0; i < enemyCount; i++) {
                int ex, ey;
                do {
                    ex = random.nextInt(WIDTH - 200) + 150;
                    ey = random.nextInt(HEIGHT - 150) + 50;
                } while (getDistance(px, py, ex, ey) < 200 || isCollidingWithWalls(ex, ey, 34));

                int eType = random.nextInt(3);
                Color eColor = (eType == TANK_NORMAL) ? Color.GREEN : (eType == TANK_SHOTGUN ? Color.YELLOW : Color.BLUE);
                enemies.add(new EnemyTank(ex, ey, eType, eColor));
            }
        }
        lastShieldSpawnTime = System.currentTimeMillis();
    }

    private void generateRandomWalls() {
        walls.clear();
        walls.add(new Wall(0, 0, WIDTH, 20));
        walls.add(new Wall(0, HEIGHT - 50, WIDTH, 20));
        walls.add(new Wall(0, 0, 20, HEIGHT));
        walls.add(new Wall(WIDTH - 20, 0, 20, HEIGHT));

        int wallCount = random.nextInt(4) + 5;
        for (int i = 0; i < wallCount; i++) {
            int wx = random.nextInt(WIDTH - 300) + 150;
            int wy = random.nextInt(HEIGHT - 200) + 80;
            int w = random.nextBoolean() ? 120 : 30;
            int h = (w == 30) ? 120 : 30;
            walls.add(new Wall(wx, wy, w, h));
        }
    }

    private boolean isCollidingWithWalls(double x, double y, int size) {
        double rX = x - size / 2.0;
        double rY = y - size / 2.0;
        for (Wall w : walls) {
            if (w.intersects(rX, rY, size, size)) return true;
        }
        return false;
    }

    private boolean isCollidingWithOtherEnemies(EnemyTank current, double nextX, double nextY) {
        for (EnemyTank other : enemies) {
            if (other == current) continue;
            if (getDistance(nextX, nextY, other.x, other.y) < 32) {
                return true;
            }
        }
        return false;
    }

    private double getDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private void updateGame() {
        // 命中顿帧：短暂冻结全部逻辑，制造打击定格（仅几帧）
        if (hitStop > 0) { hitStop--; return; }

        if (showLevelTimer > 0) showLevelTimer--;

        // 动画状态：屏幕震动衰减、Boss 预警计时、粒子/飘字/延时爆炸/闪屏更新
        if (shakeMag > 0.1) shakeMag *= 0.88; else shakeMag = 0;
        if (screenFlash > 0.02) screenFlash *= 0.85; else screenFlash = 0;
        if (bossWarnTimer > 0) bossWarnTimer--;
        updateParticles();
        updateFloatTexts();
        updateScheduledBooms();

        // 玩家阵亡演出：定格播放死亡爆炸/碎片后再进入结算界面
        if (dying) {
            Iterator<ExplosionEffect> di = explosions.iterator();
            while (di.hasNext()) { ExplosionEffect e = di.next(); e.update(); if (!e.active) di.remove(); }
            if (--dyingTimer <= 0) { dying = false; gameState = STATE_GAMEOVER; }
            return;
        }

        // 处理倒计时状态逻辑
        if (isCountingDown) {
            // 3-2-1 每秒一声，结束时 GO
            if (countdownTimer == 205 || countdownTimer == 145 || countdownTimer == 85) sound.play("countdown_beep");
            if (countdownTimer == 30) sound.play("go");
            countdownTimer--;
            if (countdownTimer <= 0) {
                isCountingDown = false;
            }
            return; // 倒计时期间不更新游戏内容（坦克、子弹全部静止）
        }

        if (airstrikeCooldownTimer > 0) {
            airstrikeCooldownTimer--;
        }

        Iterator<Airstrike> airIter = airstrikes.iterator();
        while (airIter.hasNext()) {
            Airstrike air = airIter.next();
            air.update();
            if (air.isExploded) {
                triggerAirstrikeDamage(air.x, air.y, air.radius);
                boom(air.x, air.y, 8);
                airIter.remove();
            }
        }

        double dx = 0, dy = 0;
        if (keys[87]) dy -= 2;
        if (keys[83]) dy += 2;
        if (keys[65]) dx -= 2;
        if (keys[68]) dx += 2;//玩家移速
        if (dx != 0 || dy != 0) {
            player.move(dx, dy, walls);
        }
        player.update();
        player.angle = Math.atan2(mousePoint.getY() - player.y, mousePoint.getX() - player.x);

        long nowTime = System.currentTimeMillis();
        if (nowTime - lastShieldSpawnTime > 10000 && shields.size() < 2) {
            if (random.nextInt(100) < 3) {
                int sx = random.nextInt(WIDTH - 100) + 50;
                int sy = random.nextInt(HEIGHT - 150) + 50;
                if (!isCollidingWithWalls(sx, sy, 20)) {
                    shields.add(new ShieldItem(sx, sy));
                    lastShieldSpawnTime = nowTime;
                }
            }
        }

        Iterator<ShieldItem> si = shields.iterator();
        while (si.hasNext()) {
            ShieldItem s = si.next();
            if (getDistance(player.x, player.y, s.x, s.y) < 30) {
                player.invincibleTimer = 300;
                if (sound != null) sound.play("pickup");
                si.remove();
            }
        }

        for (EnemyTank enemy : enemies) {
            enemy.updateAI(player, bullets, walls);
            enemy.update();
        }

        Iterator<Bullet> bIter = bullets.iterator();
        while (bIter.hasNext()) {
            Bullet b = bIter.next();
            b.move(walls);
            if (!b.isActive) {
                if (b.type == TANK_ARTILLERY) {
                    boom(b.x, b.y, 6);
                    triggerExplosionDamage(b.x, b.y);
                }
                bIter.remove();
                continue;
            }

            if (b.isEnemyBullet && getDistance(b.x, b.y, player.x, player.y) < 20) {
                if (player.invincibleTimer <= 0) {
                    killPlayer();
                }
                bIter.remove();
                continue;
            }

            if (!b.isEnemyBullet) {
                boolean hitEnemy = false;
                Iterator<EnemyTank> eIter = enemies.iterator();
                while (eIter.hasNext()) {
                    EnemyTank enemy = eIter.next();
                    if (getDistance(b.x, b.y, enemy.x, enemy.y) < 20) {
                        hitEnemy = true;
                        
                        if (enemy instanceof BossTank) {
                            ((BossTank) enemy).hp--;
                            enemy.hitFlash = 6;                  // 受击白闪
                            if (sound != null) sound.play("boss_hit");
                            spawnSparks(enemy.x, enemy.y, 6);
                            if (((BossTank) enemy).hp <= 0) {
                                eIter.remove();
                                score++;
                                bossDeathSequence(enemy.x, enemy.y);   // 连环爆演出
                            }
                        } else {
                            eIter.remove();
                            score++;
                            if (sound != null) sound.play("hit_enemy");
                            spawnKillBurst(enemy.x, enemy.y);    // 白闪 + 余烟
                            spawnDebris(enemy.x, enemy.y, 12, enemy.color);
                            hitStop = 2;                          // 击杀顿帧
                            addFloatText(enemy.x, enemy.y - 20, "+1", Color.GOLD, 22);
                        }

                        if (b.type == TANK_ARTILLERY) {
                            explosions.add(new ExplosionEffect(b.x, b.y));
                            triggerExplosionDamage(b.x, b.y);
                        }
                        break;
                    }
                }
                if (hitEnemy) {
                    bIter.remove();
                    continue;
                }
            }
        }

        Iterator<ExplosionEffect> expIter = explosions.iterator();
        while (expIter.hasNext()) {
            ExplosionEffect exp = expIter.next();
            exp.update();
            if (!exp.active) expIter.remove();
        }

        if (enemies.isEmpty() && showLevelTimer == 0) {
            if (sound != null) sound.play("levelup");
            level++;
            startNewLevel(false);
        }
    }

    private void triggerExplosionDamage(double ex, double ey) {
        int radius = 80;
        if (getDistance(ex, ey, player.x, player.y) < radius) {
            if (player.invincibleTimer <= 0) {
                gameState = STATE_GAMEOVER;
            }
        }

        Iterator<EnemyTank> eIter = enemies.iterator();
        while (eIter.hasNext()) {
            EnemyTank enemy = eIter.next();
            if (getDistance(ex, ey, enemy.x, enemy.y) < radius) {
                addFloatText(enemy.x, enemy.y - 20, "+1", Color.GOLD, 22);
                eIter.remove();
                score++;
            }
        }
    }

    private void triggerAirstrikeDamage(double ax, double ay, double radius) {
        if (getDistance(ax, ay, player.x, player.y) < radius) {
            if (player.invincibleTimer <= 0) {
                gameState = STATE_GAMEOVER;
            }
        }

        Iterator<EnemyTank> eIter = enemies.iterator();
        while (eIter.hasNext()) {
            EnemyTank enemy = eIter.next();
            if (getDistance(ax, ay, enemy.x, enemy.y) < radius) {
                addFloatText(enemy.x, enemy.y - 20, "+1", Color.GOLD, 22);
                eIter.remove();
                score++;
            }
        }
    }

    // ---------------- 动画辅助 ----------------

    /** 玩家被击毁：死亡爆炸 + 碎片 + 结算音效。 */
    private void killPlayer() {
        if (dying) return;                                                   // 防重复触发
        boom(player.x, player.y, 13);
        spawnDebris(player.x, player.y, 20, player.color);
        screenFlash = 0.85; screenFlashColor = Color.color(1, 0.12, 0.12);  // 死亡红屏
        hitStop = 4;                                                         // 短暂定格
        if (sound != null) sound.play("gameover");
        dying = true; dyingTimer = 45;                                       // 先播死亡动画再结算
    }

    /** 触发一次爆炸：视觉光圈 + 音效 + 屏幕震动 + 火光粒子。 */
    private void boom(double x, double y, double power) {
        explosions.add(new ExplosionEffect(x, y));
        if (sound != null) sound.play("explosion");
        shakeMag = Math.max(shakeMag, power);
        int count = (int) (8 + power);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 1.5 + random.nextDouble() * 4.5;
            Color c = (random.nextBoolean()) ? Color.ORANGE : Color.rgb(255, 220, 80);
            int life = 18 + random.nextInt(16);
            double sz = 2 + random.nextInt(3);
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    life, sz, c, 0.04, -sz / life));   // 火光边飞边缩
        }
        spawnSmoke(x, y, (int) (3 + power / 3));        // 爆炸残留烟雾
    }

    /** 坦克被击毁时迸射的金属/火焰碎片。 */
    private void spawnDebris(double x, double y, int count, Color base) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 1.0 + random.nextDouble() * 4.0;
            Color c = (random.nextInt(3) == 0) ? Color.ORANGE : base;
            int life = 16 + random.nextInt(18);
            double sz = 2 + random.nextInt(3);
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    life, sz, c, 0.06, -sz / life));   // 碎片边飞边缩
        }
    }

    /** 小火花（子弹反弹用）。 */
    private void spawnSparks(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 1.0 + random.nextDouble() * 3.0;
            int life = 8 + random.nextInt(8);
            double sz = 1 + random.nextInt(2);
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    life, sz, Color.rgb(255, 240, 180), 0.05, -sz / life));
        }
    }

    /** 灰色烟雾：上飘、膨胀、缓慢淡出（爆炸残留 / 击毁余烟）。 */
    private void spawnSmoke(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 0.3 + random.nextDouble() * 0.9;
            double gray = 0.35 + random.nextDouble() * 0.2;
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp - 0.5,
                    36 + random.nextInt(24), 4 + random.nextInt(4),
                    Color.color(gray, gray, gray, 0.5), -0.012, 0.22));
        }
    }

    /** 击毁瞬间的白色闪光 + 余烟（普通击杀用，便宜但有"炸掉"感）。 */
    private void spawnKillBurst(double x, double y) {
        particles.add(new Particle(x, y, 0, 0, 6, 16, Color.color(1, 1, 1, 1), 0, -2.2));
        spawnSmoke(x, y, 4);
    }

    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (!p.alive()) it.remove();
        }
        // 性能兜底：超上限丢弃最旧的
        if (particles.size() > MAX_PARTICLES) {
            particles.subList(0, particles.size() - MAX_PARTICLES).clear();
        }
    }

    /** 坦克行进扬尘：车尾掉落的淡灰尘土。 */
    private void spawnDust(double x, double y) {
        double a = random.nextDouble() * Math.PI * 2;
        particles.add(new Particle(x, y, Math.cos(a) * 0.4, Math.sin(a) * 0.4 - 0.2,
                14 + random.nextInt(10), 2 + random.nextInt(2),
                Color.color(0.55, 0.5, 0.45, 0.4), -0.01, 0.08));
    }

    /** 开火弹壳：向侧后方弹出的小铜壳。 */
    private void spawnCasing(double x, double y, double angle) {
        double side = angle + Math.PI / 2 * (random.nextBoolean() ? 1 : -1);
        double sp = 1.5 + random.nextDouble() * 1.5;
        particles.add(new Particle(x, y, Math.cos(side) * sp, Math.sin(side) * sp - 1.0,
                18 + random.nextInt(10), 2, Color.rgb(200, 160, 60), 0.14, -0.05));
    }

    /** 飘字（击杀 +1 / +5 等），向上飘并渐隐。 */
    private void addFloatText(double x, double y, String text, Color color, double size) {
        floatTexts.add(new FloatText(x, y, text, color, size));
    }

    private void updateFloatTexts() {
        Iterator<FloatText> it = floatTexts.iterator();
        while (it.hasNext()) {
            FloatText f = it.next();
            f.update();
            if (!f.alive()) it.remove();
        }
    }

    /** 安排一次延时爆炸（Boss 连环爆用）。 */
    private void scheduleBoom(double x, double y, int delay, double power) {
        scheduledBooms.add(new DelayedBoom(x, y, delay, power));
    }

    private void updateScheduledBooms() {
        Iterator<DelayedBoom> it = scheduledBooms.iterator();
        while (it.hasNext()) {
            DelayedBoom d = it.next();
            if (--d.delay <= 0) {
                boom(d.x, d.y, d.power);
                it.remove();
            }
        }
    }

    /** Boss 死亡演出：顿帧 + 白闪 + 一连串延时爆炸 + 大量碎片 + 大字飘分。 */
    private void bossDeathSequence(double x, double y) {
        hitStop = 8;
        shakeMag = Math.max(shakeMag, 16);
        screenFlash = 0.6; screenFlashColor = Color.WHITE;
        boom(x, y, 16);
        spawnDebris(x, y, 40, Color.RED);
        for (int i = 0; i < 6; i++) {
            double ox = x + (random.nextDouble() * 2 - 1) * 55;
            double oy = y + (random.nextDouble() * 2 - 1) * 55;
            scheduleBoom(ox, oy, 4 + i * 5, 8 + random.nextInt(5));
        }
        addFloatText(x, y - 30, "+5", Color.GOLD, 34);
    }

    private void handleMousePressed(double mx, double my) {
        if (gameState == STATE_MENU) {
            int[] selectXs = {WIDTH / 2 - 250, WIDTH / 2 - 30, WIDTH / 2 + 190};
            for (int i = 0; i < 3; i++) {
                if (mx >= selectXs[i] - 40 && mx <= selectXs[i] + 100 && my >= 230 && my <= 380) {
                    if (selectedTankType != i && sound != null) sound.play("button");
                    selectedTankType = i;
                }
            }
            if (mx >= WIDTH / 2 - 100 && mx <= WIDTH / 2 + 100 && my >= 460 && my <= 520) {
                if (sound != null) sound.play("button");
                gameState = STATE_PLAYING;
                startNewLevel(true);
            }
        } else if (gameState == STATE_PLAYING) {
            if (!isCountingDown && !dying) { // 倒计时/阵亡期间不允许射击
                player.shoot(bullets, mousePoint);
            }
        } else if (gameState == STATE_GAMEOVER) {
            if (mx >= WIDTH / 2 - 40 && mx <= WIDTH / 2 + 40 && my >= HEIGHT / 2 + 20 && my <= HEIGHT / 2 + 120) {
                if (sound != null) { sound.play("button"); sound.playBgm("bgm_menu"); }
                gameState = STATE_MENU;
            }
        }
    }

    private void render(GraphicsContext gc) {
        gc.clearRect(0, 0, WIDTH, HEIGHT);
        if (gameState == STATE_MENU) {
            drawMenu(gc);
        } else if (gameState == STATE_PLAYING) {
            drawGame(gc);
        } else if (gameState == STATE_GAMEOVER) {
            drawGameOver(gc);
        }
    }

    private void drawMenu(GraphicsContext gc) {
        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setTextBaseline(VPos.TOP);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 45));
        gc.fillText("TANK BATTLE", WIDTH / 2.0 - 140, 60);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 20));
        gc.fillText("请选择你的坦克形态：", WIDTH / 2.0 - 100, 150);
        int[] selectXs = {WIDTH / 2 - 250, WIDTH / 2 - 30, WIDTH / 2 + 190};
        String[] titles = {"普通坦克", "散弹坦克", "自行火炮"};
        Color[] colors = {Color.GREEN, Color.YELLOW, Color.BLUE};
        for (int i = 0; i < 3; i++) {
            if (selectedTankType == i) {
                gc.setStroke(Color.RED);
                gc.setLineWidth(3);
                gc.strokeRect(selectXs[i] - 40, 230, 140, 150);
            }
            gc.setFill(colors[i]);
            gc.fillRect(selectXs[i], 260, 60, 40);
            gc.setFill(Color.LIGHTGRAY);
            gc.fillRect(selectXs[i] + 15, 240, 30, 20);
            gc.setFill(Color.DARKGRAY);
            gc.fillRect(selectXs[i] + 25, 210, 10, 30);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
            gc.fillText(titles[i], selectXs[i] - 10, 350);
        }

        gc.setFill(Color.DARKGRAY);
        gc.fillRect(WIDTH / 2.0 - 100, 460, 200, 60);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRect(WIDTH / 2.0 - 100, 460, 200, 60);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        gc.fillText("开始游戏", WIDTH / 2.0 - 48, 475);
    }

    /** 绘制一辆坦克：车身(履带,跟随行进方向) + 炮塔炮管(跟随瞄准,带后坐)。 */
    private void drawTankBody(GraphicsContext gc, Tank t, double scale, Color hullColor) {
        gc.save();
        gc.translate(t.x, t.y);
        gc.scale(scale, scale);

        // ---- 车身 + 滚动履带（朝行进方向）----
        gc.save();
        gc.rotate(Math.toDegrees(t.bodyAngle));
        gc.setFill(Color.rgb(35, 35, 35));            // 履带底
        gc.fillRect(-20, -16, 40, 6);
        gc.fillRect(-20, 10, 40, 6);
        gc.setFill(Color.rgb(110, 110, 110));         // 履带齿（随 treadPhase 滚动）
        double off = ((t.treadPhase % 8) + 8) % 8;
        for (double px = -20 + off; px < 18; px += 8) {
            gc.fillRect(px, -16, 3, 6);
            gc.fillRect(px, 10, 3, 6);
        }
        gc.setFill(hullColor);                        // 车体
        gc.fillRect(-18, -11, 36, 22);
        gc.restore();

        // ---- 炮塔 + 炮管（朝瞄准方向，带后坐）----
        gc.save();
        gc.rotate(Math.toDegrees(t.angle));
        gc.translate(-t.recoil, 0);
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(-9, -9, 18, 18);                  // 炮塔
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(5, -4, 25, 8);                    // 炮管
        if (t.muzzleTimer > 0) {                      // 炮口闪光
            double r = 6 + t.muzzleTimer * 2;
            gc.setFill(Color.rgb(255, 230, 120, 0.9));
            gc.fillOval(30 - r / 2, -r / 2, r, r);
        }
        gc.restore();

        gc.restore();
    }

    /** 火炮蓄力聚能球：炮口处逐渐变大变亮，蓄满最亮。 */
    private void drawChargeOrb(GraphicsContext gc, Tank t) {
        double prog = 1.0 - t.chargeTimer / 90.0;     // 0 -> 1
        double bx = t.x + Math.cos(t.angle) * 30;
        double by = t.y + Math.sin(t.angle) * 30;
        double r = 3 + prog * 12;
        gc.setFill(Color.color(0.3, 0.6, 1.0, 0.35));         // 外光
        gc.fillOval(bx - r * 1.7, by - r * 1.7, r * 3.4, r * 3.4);
        gc.setFill(Color.color(0.75, 0.9, 1.0, 0.55 + 0.35 * prog));  // 核心
        gc.fillOval(bx - r, by - r, r * 2, r * 2);
    }

    private void drawGame(GraphicsContext gc) {
        animTick++;
        gc.setFill(Color.rgb(55, 55, 55));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.getCanvas().setFocusTraversable(true);

        // 屏幕震动：偏移整个战场（背景已铺满，露出的也是地面色，无穿帮）
        double shX = 0, shY = 0;
        if (shakeMag > 0) {
            shX = (random.nextDouble() * 2 - 1) * shakeMag;
            shY = (random.nextDouble() * 2 - 1) * shakeMag;
        }
        gc.save();
        gc.translate(shX, shY);

        gc.setFill(Color.GRAY);
        for (Wall w : walls) {
            gc.fillRect(w.x, w.y, w.width, w.height);
        }

        for (ShieldItem s : shields) {
            double ph = animTick * 0.12 + s.x;          // 不同道具相位错开
            double core = 10 + Math.sin(ph) * 1.5;
            double ring = 13 + Math.sin(ph) * 3.5;       // 外圈呼吸更明显
            gc.setFill(Color.GREEN);
            gc.fillOval(s.x - core, s.y - core, core * 2, core * 2);
            gc.setStroke(Color.color(1, 1, 1, 0.5 + 0.4 * Math.abs(Math.sin(ph))));
            gc.setLineWidth(2);
            gc.strokeOval(s.x - ring, s.y - ring, ring * 2, ring * 2);
        }

        if (player.type == TANK_ARTILLERY && player.isCharging) {
            gc.setStroke(player.chargeTimer > 60 ? Color.BLUE : Color.RED);
            // 后 1 秒红线脉动加粗，提示即将打出
            gc.setLineWidth(player.chargeTimer <= 60 ? (2 + Math.abs(Math.sin(player.chargeTimer * 0.5)) * 4) : 2);
            gc.setLineDashes(9);
            double endX = player.x + Math.cos(player.angle) * 1200;
            double endY = player.y + Math.sin(player.angle) * 1200;
            gc.strokeLine(player.x, player.y, endX, endY);
            gc.setLineDashes(null);
        }

        if (!dying) {                                  // 阵亡定格期间玩家已"爆掉"，不再绘制
            boolean pBlink = player.invincibleTimer > 0 && (animTick / 4) % 2 == 0;
            if (pBlink) gc.setGlobalAlpha(0.45);       // 无敌时车身闪烁
            drawTankBody(gc, player, 1.0, player.color);
            if (pBlink) gc.setGlobalAlpha(1.0);
            if (player.type == TANK_ARTILLERY && player.isCharging) drawChargeOrb(gc, player);

            if (player.invincibleTimer > 0) {          // 护盾呼吸脉动
                double pr = 30 + Math.sin(player.invincibleTimer * 0.2) * 3;
                gc.setStroke(Color.rgb(255, 215, 0, 0.75));
                gc.setLineWidth(4);
                gc.strokeOval(player.x - pr, player.y - pr, pr * 2, pr * 2);
            }
        }

        for (EnemyTank enemy : enemies) {
            double base = (enemy instanceof BossTank) ? 1.5 : 1.0;
            double sIn = enemy.spawnMax > 0
                    ? (0.3 + 0.7 * (1.0 - (double) enemy.spawnTimer / enemy.spawnMax)) : 1.0;

            // Boss 狂暴红色脉动光环
            if (enemy instanceof BossTank && ((BossTank) enemy).enraged) {
                double aura = 42 + Math.abs(Math.sin(animTick * 0.2)) * 10;
                gc.setStroke(Color.color(1, 0.1, 0.1, 0.55));
                gc.setLineWidth(3);
                gc.strokeOval(enemy.x - aura, enemy.y - aura, aura * 2, aura * 2);
            }

            Color hull = enemy.hitFlash > 0 ? Color.WHITE : enemy.color;
            drawTankBody(gc, enemy, base * sIn, hull);

            if (enemy.type == TANK_ARTILLERY && enemy.isCharging) {
                gc.setStroke(enemy.chargeTimer > 60 ? Color.BLUE : Color.RED);
                gc.setLineWidth(1);
                gc.setLineDashes(5);
                double exX = enemy.x + Math.cos(enemy.angle) * 1000;
                double exY = enemy.y + Math.sin(enemy.angle) * 1000;
                gc.strokeLine(enemy.x, enemy.y, exX, exY);
                gc.setLineDashes(null);
                drawChargeOrb(gc, enemy);
            }
        }

        for (Bullet b : bullets) {
            // 拖尾：沿速度反方向画几个递减透明的圆
            int tlen = (b.type == TANK_ARTILLERY) ? 6 : 4;
            for (int t = tlen; t >= 1; t--) {
                double ta = 0.30 * (1.0 - (double) t / (tlen + 1));
                double tx = b.x - b.vx * 0.55 * t;
                double ty = b.y - b.vy * 0.55 * t;
                double rr = b.radius * (1.0 - 0.11 * t);
                if (rr <= 0) continue;
                gc.setFill(b.color.deriveColor(0, 1, 1, ta));
                gc.fillOval(tx - rr, ty - rr, rr * 2, rr * 2);
            }
            if (b.type == TANK_ARTILLERY) {   // 火炮弹外发光
                gc.setFill(Color.color(0.35, 0.6, 1.0, 0.30));
                gc.fillOval(b.x - b.radius * 2.2, b.y - b.radius * 2.2, b.radius * 4.4, b.radius * 4.4);
            }
            gc.setFill(b.color);
            gc.fillOval(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
        }

        for (Airstrike air : airstrikes) {
            boolean imminent = air.timer <= 60;
            double pulse = imminent ? (0.2 + 0.3 * Math.abs(Math.sin(air.timer * 0.4))) : 0.15;
            if (air.timer > 120) {
                gc.setStroke(Color.YELLOW);
                gc.setFill(Color.rgb(255, 255, 0, pulse));
            } else {
                gc.setStroke(Color.RED);
                gc.setFill(Color.rgb(255, 0, 0, pulse));
            }
            gc.setLineWidth(imminent ? 5 : 3);
            gc.fillOval(air.x - air.radius, air.y - air.radius, air.radius * 2, air.radius * 2);
            gc.strokeOval(air.x - air.radius, air.y - air.radius, air.radius * 2, air.radius * 2);
            // 收缩的瞄准内圈：越小越接近落地
            double inner = air.radius * (air.timer / 180.0);
            gc.strokeOval(air.x - inner, air.y - inner, inner * 2, inner * 2);
        }

        for (ExplosionEffect exp : explosions) {
            double prog = 1.0 - exp.timer / 90.0;       // 0 -> 1 扩张进度
            double a = Math.max(0, exp.timer / 90.0);    // 1 -> 0 淡出
            double r = 20 + prog * 70;                   // 由小炸大
            // 外层火焰光晕
            gc.setFill(Color.color(1.0, 0.55, 0.1, 0.35 * a));
            gc.fillOval(exp.x - r, exp.y - r, r * 2, r * 2);
            // 内核亮黄
            double rc = r * 0.55;
            gc.setFill(Color.color(1.0, 0.9, 0.5, 0.6 * a));
            gc.fillOval(exp.x - rc, exp.y - rc, rc * 2, rc * 2);
            // 冲击波白环（扩张更快）
            double rw = 30 + prog * 90;
            gc.setStroke(Color.color(1, 1, 1, 0.6 * a));
            gc.setLineWidth(3);
            gc.strokeOval(exp.x - rw, exp.y - rw, rw * 2, rw * 2);
        }

        // 粒子（碎片/火花/火光/烟雾/扬尘/弹壳）
        for (Particle p : particles) {
            gc.setFill(p.color.deriveColor(0, 1, 1, p.alpha()));
            gc.fillOval(p.x - p.size, p.y - p.size, p.size * 2, p.size * 2);
        }

        // 飘字（+1 / +5），随战场一起震动
        gc.setTextBaseline(VPos.CENTER);
        for (FloatText f : floatTexts) {
            gc.setFill(f.color.deriveColor(0, 1, 1, f.alpha()));
            gc.setFont(Font.font("Impact", FontWeight.BOLD, f.size));
            gc.fillText(f.text, f.x - f.size * 0.4, f.y);
        }
        gc.setTextBaseline(VPos.TOP);

        gc.restore();   // 结束屏幕震动偏移，以下为固定 UI 层

        // 全屏染色闪光（死亡红屏 / 大爆炸白闪）
        if (screenFlash > 0.02) {
            gc.setFill(screenFlashColor.deriveColor(0, 1, 1, Math.min(1.0, screenFlash)));
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        }

        gc.setFill(Color.BLACK);
        gc.fillRect(0, HEIGHT - 50, WIDTH, 50);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        gc.fillText("我的得分: " + score, 40, HEIGHT - 40);
        gc.fillText("当前关卡: " + level, WIDTH - 160, HEIGHT - 40);

        if (player.invincibleTimer > 0) {
            gc.setFill(Color.GOLD);
            gc.fillText(String.format("【无敌护盾激活】 剩余时间: %.1fs", player.invincibleTimer / 60.0), 220, HEIGHT - 40);
        } else if (!airstrikes.isEmpty()) {
            gc.setFill(Color.RED);
            gc.fillText("【再次按下 1 键】 -> 全场立刻引爆空中空袭！", 220, HEIGHT - 40);
        } else if (airstrikeCooldownTimer > 0) {
            double remainSeconds = airstrikeCooldownTimer / 60.0;
            gc.setFill(Color.rgb(255, 165, 0));
            gc.fillText(String.format("【空中支援装填中】 剩余时间: %.1fs", remainSeconds), 220, HEIGHT - 40);
        } else {
            gc.setFill(Color.GREENYELLOW);
            gc.fillText("【1 键呼叫空中支援】 状态：就绪 (无限制次)", 220, HEIGHT - 40);
        }

        // Boss 登场预警
        if (bossWarnTimer > 0) {
            double a = 0.25 + 0.2 * Math.abs(Math.sin(bossWarnTimer * 0.2));
            gc.setStroke(Color.color(1, 0, 0, a));
            gc.setLineWidth(12);
            gc.strokeRect(6, 6, WIDTH - 12, HEIGHT - 12);
            gc.setFill(Color.color(1, 0, 0, 0.9));
            gc.setFont(Font.font("Impact", FontWeight.BOLD, 46));
            gc.setTextBaseline(VPos.TOP);
            gc.fillText("! BOSS APPROACHING !", WIDTH / 2.0 - 230, 80);
        }

        // STAGE 横幅：左侧滑入 + 末尾淡出
        if (showLevelTimer > 0) {
            double slide = (showLevelTimer > 60) ? (showLevelTimer - 60) / 30.0 * 220 : 0; // 前 0.5s 滑入
            double alpha = (showLevelTimer < 20) ? showLevelTimer / 20.0 * 0.55 : 0.55;     // 末段淡出
            gc.setFill(Color.rgb(255, 215, 0, alpha));
            gc.setFont(Font.font("Comic Sans MS", FontWeight.BOLD, 40));
            gc.setTextBaseline(VPos.TOP);
            gc.fillText("STAGE " + level, WIDTH / 2.0 - 90 - slide, HEIGHT / 2.0 - 150);
        }

        // 新增：绘制屏幕中央3秒倒计时特效
        if (isCountingDown) {
            gc.save();
            gc.setTextBaseline(VPos.CENTER);
            if (countdownTimer > 30) {
                // 计算当前是第几秒 (3, 2, 1)
                int secondsLeft = (countdownTimer - 31) / 60 + 1;
                
                // 呼吸放大的缩放效果 (每秒的帧数内从大到小)
                int frameInSecond = (countdownTimer - 31) % 60;
                double scale = 1.0 + (frameInSecond / 60.0) * 0.6;
                
                gc.setFont(Font.font("Impact", FontWeight.BOLD, 90 * scale));
                gc.setFill(Color.ORANGE);
                // 阴影
                gc.setFill(Color.BLACK);
                gc.fillText(String.valueOf(secondsLeft), WIDTH / 2.0 - 20, HEIGHT / 2.0 - 20);
                // 主体字
                gc.getCanvas().setFocusTraversable(true);
                gc.setFill(Color.rgb(255, 69, 0));
                gc.fillText(String.valueOf(secondsLeft), WIDTH / 2.0 - 23, HEIGHT / 2.0 - 23);
            } else {
                // 最后 0.5 秒 (30帧) 显示 "GO!" + 全屏闪白冲击
                double fa = countdownTimer / 30.0;       // 1 -> 0
                gc.setFill(Color.color(1, 1, 1, 0.25 * fa));
                gc.fillRect(0, 0, WIDTH, HEIGHT);
                gc.setFont(Font.font("Impact", FontWeight.BOLD, 100));
                gc.setFill(Color.GREENYELLOW);
                gc.fillText("READY? GO!!", WIDTH / 2.0 - 220, HEIGHT / 2.0 - 30);
            }
            gc.restore();
        }
    }

    private void drawGameOver(GraphicsContext gc) {
        gc.setTextBaseline(VPos.TOP);
        gc.setFill(Color.rgb(20, 20, 20, 0.94));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.getCanvas().setFocusTraversable(true);
        gc.setFill(Color.RED);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 50));
        gc.fillText("你已被击败！", WIDTH / 2.0 - 150, HEIGHT / 2.0 - 120);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 24));
        gc.fillText("最终得分: " + score + "   达到关卡: " + level, WIDTH / 2.0 - 180, HEIGHT / 2.0 - 40);
        gc.setFill(Color.GRAY);
        gc.fillRect(WIDTH / 2.0 - 40, HEIGHT / 2.0 + 50, 80, 70);

        gc.setFill(Color.WHITE);
        double[] hx = {WIDTH / 2.0 - 50, WIDTH / 2.0, WIDTH / 2.0 + 50};
        double[] hy = {HEIGHT / 2.0 + 50, HEIGHT / 2.0 + 20, HEIGHT / 2.0 + 50};
        gc.fillPolygon(hx, hy, 3);
        gc.fillRect(WIDTH / 2.0 - 30, HEIGHT / 2.0 + 50, 60, 60);
        gc.setFill(Color.BLACK);
        gc.fillRect(WIDTH / 2.0 - 10, HEIGHT / 2.0 + 80, 20, 30);
    }

    // --- 实体内部类类群 ---

    class Airstrike {
        double x, y;
        double radius = 110;  
        int timer = 180;      
        boolean isExploded = false;

        public Airstrike(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void update() {
            if (isExploded) return; 
            timer--;
            if (timer <= 0) {
                isExploded = true;
            }
        }
    }

    class Tank {
        double x, y;
        double angle;
        int type;
        Color color;
        int size = 34;

        int cooldown = 0;
        int burstCount = 0;
        int burstCoolDown = 0;

        boolean isCharging = false;
        int chargeTimer = 0;
        double chargeTargetX, chargeTargetY;

        int muzzleTimer = 0;   // 炮口闪光计时
        double recoil = 0;     // 开火后坐位移
        int hitFlash = 0;      // 受击白闪计时
        int spawnTimer = 0;    // 出场放大动画剩余帧
        int spawnMax = 0;      // 出场动画总帧

        double prevX, prevY;   // 上一帧位置（判断是否移动）
        double bodyAngle;      // 车身朝向（跟随行进方向，独立于炮塔瞄准）
        double treadPhase;     // 履带滚动相位
        boolean movedThisFrame;// 本帧是否在移动
        int dustTimer;         // 扬尘节流

        public Tank(double x, double y, int type) {
            this.x = x;
            this.y = y;
            this.type = type;
            if (type == TANK_NORMAL) this.color = Color.GREEN;
            else if (type == TANK_SHOTGUN) this.color = Color.YELLOW;
            else this.color = Color.BLUE;
        }

        public void update() {
            if (cooldown > 0) cooldown--;
            if (burstCoolDown > 0) burstCoolDown--;
            if (muzzleTimer > 0) muzzleTimer--;
            if (recoil > 0.1) recoil *= 0.78; else recoil = 0;
            if (hitFlash > 0) hitFlash--;
            if (spawnTimer > 0) spawnTimer--;

            // 移动检测：更新车身朝向、履带相位、扬尘
            double mvx = x - prevX, mvy = y - prevY;
            double dist = Math.sqrt(mvx * mvx + mvy * mvy);
            movedThisFrame = dist > 0.3;
            if (movedThisFrame) {
                bodyAngle = Math.atan2(mvy, mvx);
                treadPhase += dist;
                if (--dustTimer <= 0) {
                    dustTimer = 4;
                    spawnDust(x - Math.cos(bodyAngle) * 16, y - Math.sin(bodyAngle) * 16);
                }
            }
            prevX = x; prevY = y;

            if (isCharging) {
                chargeTimer--;
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
            if (!isCollidingWithWalls(nextX, y, size)) {
                x = nextX;
            }
            if (!isCollidingWithWalls(x, nextY, size)) {
                y = nextY;
            }
        }

        private void fireArtilleryBullet() {
            double angleToTarget = Math.atan2(chargeTargetY - y, chargeTargetX - x);
            Bullet b = new Bullet(x, y, angleToTarget, false, TANK_ARTILLERY, true, 26);
            b.isEnemyBullet = (this instanceof EnemyTank);
            b.color = Color.BLUE;
            Launcher.this.bullets.add(b);
            if (sound != null) sound.play("artillery_fire");
            muzzleTimer = 6;
            recoil = 8;
            shakeMag = Math.max(shakeMag, 4);
        }
    }

    class PlayerTank extends Tank {
        int invincibleTimer = 0; 

        public PlayerTank(double x, double y, int type) {
            super(x, y, type);
        }

        @Override
        public void update() {
            super.update();
            if (invincibleTimer > 0) {
                invincibleTimer--;
            }
        }

        public void shoot(ArrayList<Bullet> bList, Point2D target) {
            if (isCharging) return;

            if (type == TANK_NORMAL) {
                if (burstCoolDown > 0) { if (sound != null) sound.play("empty_click"); return; }
                if (cooldown <= 0) {
                    Bullet b = new Bullet(x, y, angle, true, TANK_NORMAL, true, 6);
                    b.isEnemyBullet = false;
                    b.color = Color.RED;
                    bList.add(b);
                    if (sound != null) sound.play("shoot_normal");
                    muzzleTimer = 4;
                    recoil = 4;
                    spawnCasing(x, y, angle);
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
                        Bullet b = new Bullet(x, y, startAngle + (i * 0.15), false, TANK_SHOTGUN, true, 6);
                        b.isEnemyBullet = false;
                        b.color = Color.YELLOW;
                        b.life = 25;
                        bList.add(b);
                    }
                    if (sound != null) sound.play("shoot_shotgun");
                    muzzleTimer = 5;
                    recoil = 6;
                    spawnCasing(x, y, angle);
                    cooldown = 60;
                }
            } else if (type == TANK_ARTILLERY) {
                if (cooldown <= 0) {
                    isCharging = true;
                    chargeTimer = 90;
                    chargeTargetX = target.getX();
                    chargeTargetY = target.getY();
                    if (sound != null) sound.play("charge");
                    cooldown = 150;
                }
            }
        }
    }

    class Bullet {
        double x, y;
        double vx, vy;
        boolean canBounce;
        int type;
        boolean isEnemyBullet = false;
        Color color = Color.RED;
        int radius = 5;
        int life = 300;
        boolean isActive = true;
        int speed;
        boolean isHarmful;

        public Bullet(double x, double y, double angle, boolean bounce, int type, boolean harmful, int speed) {
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

            if (player != null && player.type == TANK_ARTILLERY && player.isCharging) {
                actualVx *= 0.25; 
                actualVy *= 0.25; 
            }

            x += actualVx;
            y += actualVy;
            life--;
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
                        spawnSparks(x, y, 5);
                        if (sound != null) sound.play("ricochet", 0.4);
                        break;
                    } else {
                        isActive = false;
                        spawnSparks(x, y, 4);            // 打墙火花
                        if (type == TANK_ARTILLERY) spawnSmoke(x, y, 2);
                        break;
                    }
                }
            }
        }
    }

    class Wall {
        int x, y, width, height;
        public Wall(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        public boolean intersects(double rx, double ry, double rw, double rh) {
            return rx < this.x + this.width && rx + rw > this.x && ry < this.y + this.height && ry + rh > this.y;
        }
    }

    class ShieldItem {
        int x, y;
        public ShieldItem(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    class ExplosionEffect {
        double x, y;
        int timer = 90;
        boolean active = true;

        public ExplosionEffect(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void update() {
            timer--;
            if (timer <= 0) active = false;
        }
    }

    // 通用粒子（碎片/火花/火光/烟雾）
    class Particle {
        double x, y, vx, vy, size;
        int life, maxLife;
        Color color;
        double gravity = 0.04;   // 正=下落，负=上飘（烟雾）
        double growth = 0;       // 每帧尺寸变化，负=收缩，正=膨胀

        public Particle(double x, double y, double vx, double vy, int life, double size, Color color) {
            this(x, y, vx, vy, life, size, color, 0.04, 0);
        }

        public Particle(double x, double y, double vx, double vy, int life, double size, Color color,
                        double gravity, double growth) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.maxLife = life; this.size = size; this.color = color;
            this.gravity = gravity; this.growth = growth;
        }

        public void update() {
            x += vx; y += vy;
            vx *= 0.92; vy *= 0.92;   // 阻力减速
            vy += gravity;
            size += growth; if (size < 0) size = 0;
            life--;
        }

        public boolean alive() { return life > 0; }
        public double alpha() { return Math.max(0, (double) life / maxLife); }
    }

    // 飘字：向上飘 + 渐隐（击杀 +1 / +5 等）
    class FloatText {
        double x, y, size;
        int life, maxLife;
        String text;
        Color color;

        public FloatText(double x, double y, String text, Color color, double size) {
            this.x = x; this.y = y; this.text = text; this.color = color; this.size = size;
            this.life = 45; this.maxLife = 45;
        }

        public void update() { y -= 0.9; life--; }
        public boolean alive() { return life > 0; }
        public double alpha() { return Math.max(0, (double) life / maxLife); }
    }

    // 延时爆炸（Boss 连环爆用）
    class DelayedBoom {
        double x, y, power;
        int delay;

        public DelayedBoom(double x, double y, int delay, double power) {
            this.x = x; this.y = y; this.delay = delay; this.power = power;
        }
    }
}