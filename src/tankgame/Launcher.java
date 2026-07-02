package tankgame;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import tankgame.entities.BossTank;
import tankgame.entities.Bullet;
import tankgame.entities.EnemyTank;
import tankgame.entities.OrangeBoss;
import tankgame.entities.PinkBoss;
import tankgame.entities.PlayerArtilleryStrike;
import tankgame.entities.PlayerTank;
import tankgame.entities.SafeZone;
import tankgame.entities.ShieldItem;
import tankgame.entities.Tank;
import tankgame.entities.Wall;
import tankgame.effects.DelayedBoom;
import tankgame.effects.ExplosionEffect;
import tankgame.effects.FloatText;
import tankgame.effects.Particle;

public class Launcher extends Application {

    // ========== 游戏状态常量 ==========
    private static final int STATE_MENU = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_GAMEOVER = 2;
    private static final int STATE_HELP = 3;
    private static final int STATE_SELECT = 4;

    // 坦克类型
    public static final int TANK_NORMAL = 0;
    public static final int TANK_SHOTGUN = 1;
    public static final int TANK_ARTILLERY = 2;

    public static final int WIDTH = 1000;
    public static final int HEIGHT = 700;

    private int selectedTankType = TANK_NORMAL;
    private int score = 0;
    private int level = 1;
    private double showLevelTimer = 0;
    private boolean isCountingDown = false;
    private double countdownTimer = 0;
    private int gameState = STATE_MENU;

    public PlayerTank player;
    private ArrayList<EnemyTank> enemies = new ArrayList<>();
    public ArrayList<Bullet> bullets = new ArrayList<>();
    private ArrayList<Wall> walls = new ArrayList<>();
    private ArrayList<ShieldItem> shields = new ArrayList<>();
    private ArrayList<ExplosionEffect> explosions = new ArrayList<>();

    // ---------- 动画/特效系统 ----------
    private ArrayList<Particle> particles = new ArrayList<>();      // 碎片/火花/火光/烟雾/扬尘/弹壳
    private ArrayList<FloatText> floatTexts = new ArrayList<>();    // 飘字（+1 / +5）
    private ArrayList<DelayedBoom> scheduledBooms = new ArrayList<>(); // 延时爆炸（Boss 连环爆）
    public double shakeMag = 0;               // 屏幕震动强度
    private double bossWarnTimer = 0;         // Boss 登场预警视觉计时
    private long animTick = 0;                // 全局动画时钟（呼吸/闪烁/履带）
    private double hitStop = 0;               // 命中顿帧：暂停 N 帧逻辑
    private double screenFlash = 0;           // 全屏染色强度（0~1）
    private Color screenFlashColor = Color.WHITE; // 全屏染色颜色
    public boolean dying = false;             // 玩家阵亡演出中
    private double dyingTimer = 0;            // 阵亡演出剩余帧
    private static final int MAX_PARTICLES = 600; // 粒子上限（性能兜底）

    // 音频管理器
    public SoundManager sound;

    // ---------- Buff 系统 ----------
    public int selectedBuff = 0;    // 0=无, 1=神佑, 2=工业革命, 3=制空权

    // ---------- 神佑之地 ----------
    public SafeZone safeZone = null;
    private int safeZoneState = 0;          // 0=冷却, 1=激活
    private double safeZoneTimer = 0;
    private static final int SAFE_ZONE_SIZE = 150;
    private static final int SAFE_ZONE_DURATION = 900;   // 15秒
    private static final int SAFE_ZONE_COOLDOWN = 900;   // 15秒

    // ---------- 空袭参数 ----------
    private double airstrikeRadius = 150;
    private int airstrikeCooldownFrames = 1500;   // 默认25秒

    private boolean warningActive = false;
    private double warningX, warningY;
    private double warningTimer = 0;
    private boolean strikeActive = false;
    private double strikeTimer = 0;
    private static final int WARNING_DURATION = 90;
    private static final int STRIKE_DURATION = 30;
    private double airstrikeCooldown = 0;

    // 玩家火炮打击
    public ArrayList<PlayerArtilleryStrike> playerStrikes = new ArrayList<>();

    // ---------- Boss后备队列 ----------
    private ArrayList<EnemyTank> bossQueue = new ArrayList<>();

    // 输入
    private boolean[] keys = new boolean[256];
    private Point2D mousePoint = new Point2D(0, 0);
    public Random random = new Random();
    private long lastShieldSpawnTime = 0;
    private Canvas canvas;

    // ---------- 帧率无关的变步长循环：逐帧 delta，速度不随帧率变化，且支持高刷(165fps) ----------
    private long lastFrameNanos = 0;
    public double dt = 1.0;                                                // 帧增量，1.0 = 一个 60Hz 步
    public static final double SPEED_MULTIPLIER = 1.3;                     // 整体移动提速 30%

    public static void main(String[] args) {
        // 解除 JavaFX 默认 60fps 上限，让 AnimationTimer 按高刷新率(如165Hz)推进
        System.setProperty("javafx.animation.pulse", "165");
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
            if (gameState == STATE_HELP && e.getCode() == KeyCode.ESCAPE) {
                gameState = STATE_MENU;
            }
        });

        scene.setOnKeyReleased(e -> {
            int code = getKeyCodeValue(e.getCode());
            if (code > 0 && code < keys.length) keys[code] = false;
        });

        canvas.setOnMouseMoved(e -> mousePoint = new Point2D(e.getX(), e.getY()));
        canvas.setOnMouseDragged(e -> mousePoint = new Point2D(e.getX(), e.getY()));

        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                handleMousePressed(e.getX(), e.getY());
            } else if (e.getButton() == MouseButton.SECONDARY) {
                if (gameState == STATE_PLAYING && !isCountingDown && !dying && airstrikeCooldown <= 0) {
                    warningX = e.getX();
                    warningY = e.getY();
                    warningTimer = WARNING_DURATION;
                    warningActive = true;
                    strikeActive = false;
                    if (sound != null) { sound.play("airstrike_call"); sound.play("airstrike_incoming"); }
                }
            }
        });

        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 变步长：dt = 距上帧的时间(以 60Hz 步为单位)。逻辑每帧推进 dt，
                // 移动/计时/概率都乘 dt，因此速度不随帧率变化；高刷(165fps)下动作更平滑。
                if (lastFrameNanos == 0) lastFrameNanos = now;
                dt = (now - lastFrameNanos) / 1_000_000_000.0 * 60.0;
                lastFrameNanos = now;
                if (dt > 3.0) dt = 3.0;   // 防卡顿/切后台后一次性大跳
                if (gameState == STATE_PLAYING) {
                    updateGame();
                }
                render(gc);
            }
        };
        gameLoop.start();

        lastShieldSpawnTime = System.currentTimeMillis();

        // 初始化音频并播放主菜单背景乐（缺素材/缺 javafx.media 时 SoundManager 自动静音，不崩）
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

    // ---------- 开始新关卡 ----------
    private void startNewLevel(boolean resetScoreAndLevel) {
        if (resetScoreAndLevel) {
            score = 0;
            level = 1;
            // selectedBuff 保留，不重置
        }
        showLevelTimer = 90;
        isCountingDown = true;
        countdownTimer = 210;
        for (int i = 0; i < keys.length; i++) keys[i] = false;

        bullets.clear();
        enemies.clear();
        shields.clear();
        explosions.clear();
        playerStrikes.clear();
        bossQueue.clear();  // 清空后备队列

        // 清空上一关残留的动画/特效状态
        particles.clear();
        floatTexts.clear();
        scheduledBooms.clear();
        shakeMag = 0;
        screenFlash = 0;
        bossWarnTimer = 0;
        hitStop = 0;
        dying = false;
        dyingTimer = 0;

        // 进入战斗背景乐（已在播放则 SoundManager 自动忽略）
        if (sound != null) sound.playBgm("bgm_battle");

        warningActive = false;
        strikeActive = false;
        warningTimer = 0;
        strikeTimer = 0;
        airstrikeCooldown = 0;

        if (selectedBuff == 3) {
            airstrikeRadius = 180;
            airstrikeCooldownFrames = 2100;  // 35秒
        } else {
            airstrikeRadius = 150;
            airstrikeCooldownFrames = 1500;  // 25秒
        }

        generateRandomWalls();

        int px = 100, py = HEIGHT / 2;
        while (isCollidingWithWalls(px, py, 34)) {
            px += 15;
            if (px > WIDTH - 100) { px = 100; py += 15; }
        }
        player = new PlayerTank(this, px, py, selectedTankType);
        player.invincibleTimer = 300;
        player.health = 3;

        // 生成敌人（Boss关或普通关）
        if (level % 4 == 0) {
            int bossCount = 1 + ((level / 4) - 1);
            for (int i = 0; i < bossCount; i++) {
                int bossType = random.nextInt(3);
                EnemyTank boss = null;
                boolean placed = false;
                int attempts = 0;
                while (!placed && attempts < 100) {
                    double startX = random.nextInt(WIDTH - 100) + 50;
                    double startY = random.nextInt(HEIGHT - 100) + 50;
                    if (isCollidingWithWalls(startX, startY, 50)) {
                        attempts++;
                        continue;
                    }
                    // 检测与已有敌人（此时enemies为空，但为安全保留）
                    boolean overlap = false;
                    for (EnemyTank existing : enemies) {
                        if (getDistance(startX, startY, existing.x, existing.y) < 70) {
                            overlap = true;
                            break;
                        }
                    }
                    if (overlap) {
                        attempts++;
                        continue;
                    }

                    if (bossType == 0) boss = new BossTank(this, startX, startY);
                    else if (bossType == 1) boss = new PinkBoss(this, startX, startY);
                    else boss = new OrangeBoss(this, startX, startY);
                    placed = true;
                }

                if (!placed) {
                    // 保底：使用固定位置
                    double startX = (WIDTH / 4.0) + (i % 2) * (WIDTH / 2.0);
                    double startY = (HEIGHT / 4.0) + (i / 2) * (HEIGHT / 3.0);
                    if (bossType == 0) boss = new BossTank(this, startX, startY);
                    else if (bossType == 1) boss = new PinkBoss(this, startX, startY);
                    else boss = new OrangeBoss(this, startX, startY);
                    while (isCollidingWithWalls(boss.x, boss.y, boss.size)) {
                        boss.x += 20;
                        boss.y += 20;
                    }
                }
                if (boss != null) {
                    bossQueue.add(boss);
                }
            }

            // 从后备队列中取出最多4个加入战场
            int spawnCount = Math.min(4, bossQueue.size());
            for (int i = 0; i < spawnCount; i++) {
                enemies.add(bossQueue.remove(0));
            }

            // Boss 登场预警视觉（约 3 秒）+ 登场曲（压低战斗 BGM）
            bossWarnTimer = 180;
            if (sound != null) sound.playBossWarning();
        } else {
            int enemyCount = (level == 1) ? 1 : (level == 2 ? 2 : (level == 3 ? 3 : (level == 5 ? 5 : 6)));
            for (int i = 0; i < enemyCount; i++) {
                int ex, ey;
                do {
                    ex = random.nextInt(WIDTH - 250) + 150;
                    ey = random.nextInt(HEIGHT - 200) + 50;
                } while (getDistance(px, py, ex, ey) < 200 || isCollidingWithWalls(ex, ey, 34));

                int eType = random.nextInt(3);
                Color eColor = (eType == TANK_NORMAL) ? Color.GREEN : (eType == TANK_SHOTGUN ? Color.YELLOW : Color.BLUE);
                EnemyTank enemy = new EnemyTank(this, ex, ey, eType);
                enemy.color = eColor;
                enemies.add(enemy);
            }
        }
        lastShieldSpawnTime = System.currentTimeMillis();

        safeZone = null;
        safeZoneState = 0;
        safeZoneTimer = 0;
    }

    // ---------- 生成墙体 ----------
    private void generateRandomWalls() {
        walls.clear();
        walls.add(new Wall(0, 0, WIDTH, 20));
        walls.add(new Wall(0, HEIGHT - 50, WIDTH, 20));
        walls.add(new Wall(0, 0, 20, HEIGHT));
        walls.add(new Wall(WIDTH - 20, 0, 20, HEIGHT));

        int wallCount = random.nextInt(15) + 25;
        for (int i = 0; i < wallCount; i++) {
            int wx = random.nextInt(WIDTH - 300) + 150;
            int wy = random.nextInt(HEIGHT - 250) + 80;
            int w = random.nextBoolean() ? 60 : 16;
            int h = (w == 16) ? 60 : 16;

            if (getDistance(wx + w/2.0, wy + h/2.0, 100, HEIGHT/2.0) < 95 ||
                    getDistance(wx + w/2.0, wy + h/2.0, WIDTH/2.0, HEIGHT/2.0) < 95) {
                continue;
            }

            Wall newWall = new Wall(wx, wy, w, h);
            boolean tooClose = false;
            for (Wall existing : walls) {
                if (existing.intersects(wx - 50, wy - 50, w + 100, h + 100)) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                walls.add(newWall);
            }
        }
    }

    // ---------- 碰撞辅助 ----------
    public boolean isCollidingWithWalls(double x, double y, int size) {
        double rX = x - size / 2.0;
        double rY = y - size / 2.0;
        for (Wall w : walls) {
            if (w.intersects(rX, rY, size, size)) return true;
        }
        return false;
    }

    public boolean isCollidingWithOtherEnemies(EnemyTank current, double nextX, double nextY) {
        for (EnemyTank other : enemies) {
            if (other == current) continue;
            if (getDistance(nextX, nextY, other.x, other.y) < 32) {
                return true;
            }
        }
        return false;
    }

    public double getDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    // 变步长下判断某个递减计时是否"跨越"了阈值（本帧从阈值上方降到阈值或以下）
    private static boolean crossed(double before, double after, double threshold) {
        return before > threshold && after <= threshold;
    }

    public int countWallsBetween(double x1, double y1, double x2, double y2) {
        int count = 0;
        for (Wall w : walls) {
            if (w.x == 0 || w.y == 0 || w.x + w.width == WIDTH || w.y + w.height == HEIGHT) {
                continue;
            }
            if (lineIntersectsRect(x1, y1, x2, y2, w.x, w.y, w.width, w.height)) {
                count++;
            }
        }
        return count;
    }

    private boolean lineIntersectsRect(double x1, double y1, double x2, double y2, double rx, double ry, double rw, double rh) {
        if (lineIntersectsLine(x1, y1, x2, y2, rx, ry, rx + rw, ry)) return true;
        if (lineIntersectsLine(x1, y1, x2, y2, rx, ry + rh, rx + rw, ry + rh)) return true;
        if (lineIntersectsLine(x1, y1, x2, y2, rx, ry, rx, ry + rh)) return true;
        if (lineIntersectsLine(x1, y1, x2, y2, rx + rw, ry, rx + rw, ry + rh)) return true;
        return false;
    }

    private boolean lineIntersectsLine(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        double d = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3);
        if (d == 0) return false;
        double u = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / d;
        double v = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / d;
        return (u >= 0 && u <= 1 && v >= 0 && v <= 1);
    }

    // ---------- 当Boss阵亡时，从后备队列补充 ----------
    private void replaceBossIfNeeded(EnemyTank removedEnemy) {
        // 判断是否为Boss
        boolean isBoss = (removedEnemy instanceof BossTank) ||
                (removedEnemy instanceof PinkBoss) ||
                (removedEnemy instanceof OrangeBoss);
        if (!isBoss || bossQueue.isEmpty()) {
            return;
        }

        EnemyTank newBoss = bossQueue.remove(0);
        boolean placed = false;
        int attempts = 0;
        while (!placed && attempts < 100) {
            double sx = random.nextInt(WIDTH - 100) + 50;
            double sy = random.nextInt(HEIGHT - 100) + 50;
            if (!isCollidingWithWalls(sx, sy, newBoss.size)) {
                boolean overlap = false;
                for (EnemyTank other : enemies) {
                    if (getDistance(sx, sy, other.x, other.y) < 70) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    newBoss.x = sx;
                    newBoss.y = sy;
                    placed = true;
                }
            }
            attempts++;
        }
        if (!placed) {
            // 保底位置
            newBoss.x = WIDTH / 2.0;
            newBoss.y = HEIGHT / 4.0;
        }
        enemies.add(newBoss);
    }

    // ---------- 游戏更新 ----------
    private void updateGame() {
        // 命中顿帧：短暂冻结全部逻辑，制造打击定格（仅几帧）
        if (hitStop > 0) { hitStop -= dt; return; }

        if (showLevelTimer > 0) showLevelTimer -= dt;

        // 动画状态推进：屏震衰减、闪屏淡出、Boss 预警、粒子/飘字/延时爆炸（帧率无关）
        if (shakeMag > 0.1) shakeMag *= Math.pow(0.88, dt); else shakeMag = 0;
        if (screenFlash > 0.02) screenFlash *= Math.pow(0.85, dt); else screenFlash = 0;
        if (bossWarnTimer > 0) bossWarnTimer -= dt;
        updateParticles();
        updateFloatTexts();
        updateScheduledBooms();

        // 玩家阵亡演出：定格播放死亡爆炸/碎片后再进入结算界面
        if (dying) {
            Iterator<ExplosionEffect> di = explosions.iterator();
            while (di.hasNext()) { ExplosionEffect e = di.next(); e.update(); if (!e.active) di.remove(); }
            dyingTimer -= dt;
            if (dyingTimer <= 0) { dying = false; gameState = STATE_GAMEOVER; }
            return;
        }

        if (isCountingDown) {
            // 3-2-1 每秒一声、结束 GO：变步长下用"跨越阈值"判断，避免错过整点帧
            double before = countdownTimer;
            countdownTimer -= dt;
            if (crossed(before, countdownTimer, 205) || crossed(before, countdownTimer, 145)
                    || crossed(before, countdownTimer, 85)) {
                if (sound != null) sound.play("countdown_beep");
            }
            if (crossed(before, countdownTimer, 30) && sound != null) sound.play("go");
            if (countdownTimer <= 0) isCountingDown = false;
            return;
        }

        if (airstrikeCooldown > 0) airstrikeCooldown -= dt;

        // 空袭预警/打击
        if (warningActive) {
            warningTimer -= dt;
            if (warningTimer <= 0) {
                warningActive = false;
                strikeActive = true;
                strikeTimer = STRIKE_DURATION;
            }
        }
        if (strikeActive) {
            strikeTimer -= dt;
            if (strikeTimer <= 0) {
                boom(warningX, warningY, 12);
                executeAirstrike(warningX, warningY, airstrikeRadius);
                strikeActive = false;
                airstrikeCooldown = airstrikeCooldownFrames;
            }
        }

        // 玩家火炮打击
        Iterator<PlayerArtilleryStrike> strikeIter = playerStrikes.iterator();
        while (strikeIter.hasNext()) {
            PlayerArtilleryStrike strike = strikeIter.next();
            strike.remainingFrames -= dt;
            if (strike.remainingFrames <= 0) {
                boom(strike.targetX, strike.targetY, 8);
                triggerDynamicExplosionDamage(strike.targetX, strike.targetY, strike.strikeRadius);
                strikeIter.remove();
            }
        }

        // 玩家移动
        double dx = 0, dy = 0;
        if (keys[87]) dy -= 3 * SPEED_MULTIPLIER * dt;
        if (keys[83]) dy += 3 * SPEED_MULTIPLIER * dt;
        if (keys[65]) dx -= 3 * SPEED_MULTIPLIER * dt;
        if (keys[68]) dx += 3 * SPEED_MULTIPLIER * dt;
        if (dx != 0 || dy != 0) {
            player.move(dx, dy, walls);
        }
        player.update();
        player.angle = Math.atan2(mousePoint.getY() - player.y, mousePoint.getX() - player.x);

        // 护盾生成
        long nowTime = System.currentTimeMillis();
        if (nowTime - lastShieldSpawnTime > 10000 && shields.size() < 2) {
            if (random.nextDouble() < 0.03 * dt) {
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

        // ---------- 神佑之地更新 ----------
        if (selectedBuff == 1) {
            if (safeZoneState == 0) {
                safeZoneTimer -= dt;
                if (safeZoneTimer <= 0) {
                    int sz = SAFE_ZONE_SIZE;
                    int sx = random.nextInt(WIDTH - sz);
                    int sy = random.nextInt(HEIGHT - 80 - sz);
                    safeZone = new SafeZone(sx, sy, sz, sz);
                    safeZone.active = true;
                    safeZone.remaining = SAFE_ZONE_DURATION;
                    safeZoneState = 1;
                    safeZoneTimer = SAFE_ZONE_DURATION;
                }
            } else {
                safeZone.remaining -= dt;
                if (safeZone.remaining <= 0) {
                    safeZone.active = false;
                    safeZoneState = 0;
                    safeZoneTimer = SAFE_ZONE_COOLDOWN;
                }
            }
        } else {
            safeZone = null;
            safeZoneState = 0;
            safeZoneTimer = 0;
        }

        // 敌人 AI
        for (EnemyTank enemy : enemies) {
            enemy.updateAI(player, bullets, walls);
            enemy.update();
        }

        // 子弹更新
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

            // 玩家被敌人子弹击中
            if (b.isEnemyBullet && getDistance(b.x, b.y, player.x, player.y) < 20) {
                if (player.invincibleTimer <= 0) {
                    player.takeDamage(1);
                }
                bIter.remove();
                continue;
            }

            // 玩家子弹击中敌人
            if (!b.isEnemyBullet) {
                boolean hitEnemy = false;
                Iterator<EnemyTank> eIter = enemies.iterator();
                while (eIter.hasNext()) {
                    EnemyTank enemy = eIter.next();
                    if (enemy instanceof BossTank && ((BossTank) enemy).isTeleporting) {
                        continue;
                    }
                    if (getDistance(b.x, b.y, enemy.x, enemy.y) < 20) {
                        hitEnemy = true;
                        enemy.hitShakeTimer = 12;   // Boss血条受击抖动
                        enemy.hitFlash = 6;         // 受击白闪
                        boolean isBoss = (enemy instanceof BossTank) || (enemy instanceof PinkBoss) || (enemy instanceof OrangeBoss);
                        if (enemy instanceof OrangeBoss && ((OrangeBoss) enemy).hasShield) {
                            ((OrangeBoss) enemy).hasShield = false;
                            ((OrangeBoss) enemy).shieldCooldown = 720;
                            if (sound != null) sound.play("boss_hit");
                            spawnSparks(enemy.x, enemy.y, 6);
                        } else {
                            if (isBoss) { if (sound != null) sound.play("boss_hit"); spawnSparks(enemy.x, enemy.y, 6); }
                            enemy.hp -= b.damage;
                            if (enemy.hp <= 0) {
                                eIter.remove();
                                score++;
                                enemyKilledFx(enemy);       // 击杀演出（Boss 连环爆 / 普通白闪碎片）
                                // 若阵亡的是Boss，尝试补充
                                replaceBossIfNeeded(enemy);
                            }
                        }
                        if (b.type == TANK_ARTILLERY) {
                            boom(b.x, b.y, 6);
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

        // 爆炸特效
        Iterator<ExplosionEffect> expIter = explosions.iterator();
        while (expIter.hasNext()) {
            ExplosionEffect exp = expIter.next();
            exp.update();
            if (!exp.active) expIter.remove();
        }

        // 关卡结束条件：场上无敌人和后备队列无Boss
        if (enemies.isEmpty() && bossQueue.isEmpty() && showLevelTimer <= 0) {
            if (sound != null) sound.play("levelup");
            level++;
            startNewLevel(false);
        }
    }

    // ---------- 爆炸伤害（含工业革命加成） ----------
    private void triggerExplosionDamage(double ex, double ey) {
        triggerDynamicExplosionDamage(ex, ey, 80);
    }

    private void triggerDynamicExplosionDamage(double ex, double ey, double radius) {
        int enemyDamage = 1 + (selectedBuff == 2 ? 1 : 0);

        if (getDistance(ex, ey, player.x, player.y) < radius) {
            if (player.invincibleTimer <= 0) {
                player.takeDamage(1);
            }
        }
        Iterator<EnemyTank> eIter = enemies.iterator();
        while (eIter.hasNext()) {
            EnemyTank enemy = eIter.next();
            if (enemy instanceof BossTank && ((BossTank) enemy).isTeleporting) continue;
            if (getDistance(ex, ey, enemy.x, enemy.y) < radius) {
                enemy.hitShakeTimer = 12;   // Boss血条受击抖动
                enemy.hitFlash = 6;         // 受击白闪
                if (enemy instanceof OrangeBoss && ((OrangeBoss) enemy).hasShield) {
                    ((OrangeBoss) enemy).hasShield = false;
                    ((OrangeBoss) enemy).shieldCooldown = 720;
                } else {
                    enemy.hp -= enemyDamage;
                    if (enemy.hp <= 0) {
                        eIter.remove();
                        score++;
                        enemyKilledFx(enemy);
                        replaceBossIfNeeded(enemy);
                    }
                }
            }
        }
    }

    // 空袭伤害固定1（不受工业革命影响）
    private void executeAirstrike(double cx, double cy, double radius) {
        if (getDistance(cx, cy, player.x, player.y) < radius) {
            if (player.invincibleTimer <= 0) {
                player.takeDamage(1);
            }
        }
        Iterator<EnemyTank> it = enemies.iterator();
        while (it.hasNext()) {
            EnemyTank enemy = it.next();
            if (enemy instanceof BossTank && ((BossTank) enemy).isTeleporting) continue;
            if (getDistance(cx, cy, enemy.x, enemy.y) < radius) {
                enemy.hitShakeTimer = 12;   // Boss血条受击抖动
                enemy.hitFlash = 6;         // 受击白闪
                if (enemy instanceof OrangeBoss && ((OrangeBoss) enemy).hasShield) {
                    ((OrangeBoss) enemy).hasShield = false;
                    ((OrangeBoss) enemy).shieldCooldown = 720;
                } else {
                    enemy.hp--;
                    if (enemy.hp <= 0) {
                        it.remove();
                        score++;
                        enemyKilledFx(enemy);
                        replaceBossIfNeeded(enemy);
                    }
                }
            }
        }
    }

    // ==================== 动画辅助（粒子/飘字/爆炸/演出） ====================

    /** 玩家被击毁：死亡爆炸 + 碎片 + 红屏定格 + 结算音效，之后进入结算界面。 */
    public void killPlayer() {
        if (dying) return;                                                   // 防重复触发
        boom(player.x, player.y, 13);
        spawnDebris(player.x, player.y, 20, player.color);
        screenFlash = 0.85; screenFlashColor = Color.color(1, 0.12, 0.12);   // 死亡红屏
        hitStop = 4;                                                         // 短暂定格
        if (sound != null) sound.play("gameover");
        dying = true; dyingTimer = 45;                                       // 先播死亡动画再结算
    }

    /** 敌人被击毁时的视觉/音效：Boss 走连环爆演出，普通敌走白闪+碎片+飘分。 */
    private void enemyKilledFx(EnemyTank enemy) {
        boolean isBoss = (enemy instanceof BossTank) || (enemy instanceof PinkBoss) || (enemy instanceof OrangeBoss);
        if (isBoss) {
            bossDeathSequence(enemy.x, enemy.y);
        } else {
            if (sound != null) sound.play("hit_enemy");
            spawnKillBurst(enemy.x, enemy.y);
            spawnDebris(enemy.x, enemy.y, 12, enemy.color);
            hitStop = Math.max(hitStop, 2);
            addFloatText(enemy.x, enemy.y - 20, "+1", Color.GOLD, 22);
        }
    }

    /** 触发一次爆炸：视觉光圈 + 音效 + 屏幕震动 + 火光/烟雾粒子。 */
    private void boom(double x, double y, double power) {
        explosions.add(new ExplosionEffect(this, x, y));
        if (sound != null) sound.play("explosion");
        shakeMag = Math.max(shakeMag, power);
        int count = (int) (8 + power);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 1.5 + random.nextDouble() * 4.5;
            Color c = (random.nextBoolean()) ? Color.ORANGE : Color.rgb(255, 220, 80);
            double life = 18 + random.nextInt(16);
            double sz = 2 + random.nextInt(3);
            particles.add(new Particle(this, x, y, Math.cos(a) * sp, Math.sin(a) * sp,
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
            double life = 16 + random.nextInt(18);
            double sz = 2 + random.nextInt(3);
            particles.add(new Particle(this, x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    life, sz, c, 0.06, -sz / life));   // 碎片边飞边缩
        }
    }

    /** 小火花（子弹反弹/打墙用）。 */
    public void spawnSparks(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 1.0 + random.nextDouble() * 3.0;
            double life = 8 + random.nextInt(8);
            double sz = 1 + random.nextInt(2);
            particles.add(new Particle(this, x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    life, sz, Color.rgb(255, 240, 180), 0.05, -sz / life));
        }
    }

    /** 灰色烟雾：上飘、膨胀、缓慢淡出（爆炸残留 / 击毁余烟）。 */
    public void spawnSmoke(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 0.3 + random.nextDouble() * 0.9;
            double gray = 0.35 + random.nextDouble() * 0.2;
            particles.add(new Particle(this, x, y, Math.cos(a) * sp, Math.sin(a) * sp - 0.5,
                    36 + random.nextInt(24), 4 + random.nextInt(4),
                    Color.color(gray, gray, gray, 0.5), -0.012, 0.22));
        }
    }

    /** 击毁瞬间的白色闪光 + 余烟（普通击杀用）。 */
    private void spawnKillBurst(double x, double y) {
        particles.add(new Particle(this, x, y, 0, 0, 6, 16, Color.color(1, 1, 1, 1), 0, -2.2));
        spawnSmoke(x, y, 4);
    }

    /** 坦克行进扬尘：车尾掉落的淡灰尘土。 */
    public void spawnDust(double x, double y) {
        double a = random.nextDouble() * Math.PI * 2;
        particles.add(new Particle(this, x, y, Math.cos(a) * 0.4, Math.sin(a) * 0.4 - 0.2,
                14 + random.nextInt(10), 2 + random.nextInt(2),
                Color.color(0.55, 0.5, 0.45, 0.4), -0.01, 0.08));
    }

    /** 开火弹壳：向侧后方弹出的小铜壳。 */
    public void spawnCasing(double x, double y, double angle) {
        double side = angle + Math.PI / 2 * (random.nextBoolean() ? 1 : -1);
        double sp = 1.5 + random.nextDouble() * 1.5;
        particles.add(new Particle(this, x, y, Math.cos(side) * sp, Math.sin(side) * sp - 1.0,
                18 + random.nextInt(10), 2, Color.rgb(200, 160, 60), 0.14, -0.05));
    }

    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (!p.alive()) it.remove();
        }
        if (particles.size() > MAX_PARTICLES) {   // 性能兜底：超上限丢弃最旧的
            particles.subList(0, particles.size() - MAX_PARTICLES).clear();
        }
    }

    /** 飘字（击杀 +1 / +5 等），向上飘并渐隐。 */
    private void addFloatText(double x, double y, String text, Color color, double size) {
        floatTexts.add(new FloatText(this, x, y, text, color, size));
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
    private void scheduleBoom(double x, double y, double delay, double power) {
        scheduledBooms.add(new DelayedBoom(x, y, delay, power));
    }

    private void updateScheduledBooms() {
        Iterator<DelayedBoom> it = scheduledBooms.iterator();
        while (it.hasNext()) {
            DelayedBoom d = it.next();
            d.delay -= dt;
            if (d.delay <= 0) {
                boom(d.x, d.y, d.power);
                it.remove();
            }
        }
    }

    /** Boss 死亡演出：顿帧 + 白闪 + 一连串延时爆炸 + 大量碎片 + 大字飘分。 */
    private void bossDeathSequence(double x, double y) {
        hitStop = Math.max(hitStop, 8);
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

    // ---------- 鼠标处理 ----------
    private void handleMousePressed(double mx, double my) {
        if (gameState == STATE_MENU) {
            if (mx >= WIDTH - 70 && mx <= WIDTH - 30 && my >= 20 && my <= 60) {
                gameState = STATE_HELP;
                return;
            }
            int[] selectXs = {WIDTH / 2 - 250, WIDTH / 2 - 30, WIDTH / 2 + 190};
            for (int i = 0; i < 3; i++) {
                if (mx >= selectXs[i] - 40 && mx <= selectXs[i] + 100 && my >= 230 && my <= 380) {
                    if (selectedTankType != i && sound != null) sound.play("button");
                    selectedTankType = i;
                }
            }
            if (mx >= WIDTH / 2 - 100 && mx <= WIDTH / 2 + 100 && my >= 460 && my <= 520) {
                if (sound != null) sound.play("button");
                gameState = STATE_SELECT;
            }
        } else if (gameState == STATE_SELECT) {
            int cardWidth = 200, cardHeight = 250;
            int gap = 30;
            int startX = (WIDTH - (cardWidth * 3 + gap * 2)) / 2;
            int[] cardX = {startX, startX + cardWidth + gap, startX + 2 * (cardWidth + gap)};
            int cardY = (HEIGHT - cardHeight) / 2;
            for (int i = 0; i < 3; i++) {
                if (mx >= cardX[i] && mx <= cardX[i] + cardWidth &&
                        my >= cardY && my <= cardY + cardHeight) {
                    if (sound != null) sound.play("button");
                    selectedBuff = i + 1;
                    gameState = STATE_PLAYING;
                    startNewLevel(true);
                    break;
                }
            }
        } else if (gameState == STATE_PLAYING) {
            if (!isCountingDown && !dying) {
                player.shoot(bullets, mousePoint);
            }
        } else if (gameState == STATE_HELP) {
            if (mx >= WIDTH / 2.0 - 100 && mx <= WIDTH / 2.0 + 100 &&
                    my >= 620 && my <= 670) {
                gameState = STATE_MENU;
            }
        } else if (gameState == STATE_GAMEOVER) {
            if (mx >= WIDTH / 2 - 40 && mx <= WIDTH / 2 + 40 &&
                    my >= HEIGHT / 2 + 20 && my <= HEIGHT / 2 + 120) {
                if (sound != null) { sound.play("button"); sound.playBgm("bgm_menu"); }
                gameState = STATE_MENU;
                selectedBuff = 0;
            }
        }
    }

    // ---------- 渲染 ----------
    private void render(GraphicsContext gc) {
        gc.clearRect(0, 0, WIDTH, HEIGHT);
        if (gameState == STATE_MENU) drawMenu(gc);
        else if (gameState == STATE_SELECT) drawSelectScene(gc);
        else if (gameState == STATE_PLAYING) drawGame(gc);
        else if (gameState == STATE_GAMEOVER) drawGameOver(gc);
        else if (gameState == STATE_HELP) drawHelp(gc);
    }

    // ---------- 选择场景 ----------
    private void drawSelectScene(GraphicsContext gc) {
        gc.setFill(Color.rgb(20, 20, 40));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 36));
        gc.fillText("选择你的战略卡牌", WIDTH / 2.0 - 150, 80);

        String[] titles = {"神佑", "工业革命", "制空权"};
        String[] descs = {
                "信徒们，虔诚的祈愿吧，\n战斗过程中随机刷新神佑之地",
                "铁与火铸就传奇，\n所有坦克的子弹伤害加1",
                "我方已消灭敌方空军，\n空袭范围增加，但飞机整备时间增加"
        };
        Color[] cardColors = {Color.GREEN, Color.ORANGE, Color.CYAN};

        int cardWidth = 200, cardHeight = 260;
        int gap = 30;
        int startX = (WIDTH - (cardWidth * 3 + gap * 2)) / 2;
        int cardY = (HEIGHT - cardHeight) / 2 + 20;

        double mouseX = mousePoint.getX();
        double mouseY = mousePoint.getY();

        for (int i = 0; i < 3; i++) {
            int x = startX + i * (cardWidth + gap);
            int y = cardY;
            boolean hover = (mouseX >= x && mouseX <= x + cardWidth &&
                    mouseY >= y && mouseY <= y + cardHeight);

            Color base = cardColors[i];
            Color fillColor = hover ? base.deriveColor(0, 1, 1.3, 1) : base.deriveColor(0, 1, 0.6, 1);
            gc.setFill(fillColor);
            gc.fillRoundRect(x, y, cardWidth, cardHeight, 15, 15);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeRoundRect(x, y, cardWidth, cardHeight, 15, 15);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 28));
            gc.fillText(titles[i], x + 50, y + 40);

            gc.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 16));
            gc.setFill(Color.rgb(220, 220, 220));
            String[] lines = descs[i].split("\n");
            for (int j = 0; j < lines.length; j++) {
                gc.fillText(lines[j], x + 15, y + 90 + j * 28);
            }

            gc.setFill(Color.rgb(255, 255, 255, 0.15));
            gc.fillOval(x + cardWidth - 40, y + 10, 25, 25);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            gc.fillText(String.valueOf(i+1), x + cardWidth - 30, y + 32);
        }

        gc.setFill(Color.rgb(200, 200, 200, 0.7));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 16));
        gc.fillText("点击卡片选择，之后将开始游戏", WIDTH / 2.0 - 120, HEIGHT - 50);
    }

    // ---------- 菜单 ----------
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
        gc.setFill(Color.DARKBLUE);
        gc.fillOval(WIDTH - 70, 20, 40, 40);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(WIDTH - 70, 20, 40, 40);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        gc.fillText("?", WIDTH - 56, 24);
    }

    // ---------- 帮助 ----------
    private void drawHelp(GraphicsContext gc) {
        gc.setFill(Color.rgb(25, 25, 25));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setTextBaseline(VPos.TOP);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        gc.fillText("Game Introduction", WIDTH / 2.0 - 190, 60);

        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 22));
        gc.fillText("Objective:", 180, 150);
        gc.fillText("Control your tank, defeat all enemy tanks, and advance to the next stage.", 180, 185);

        gc.fillText("Controls:", 180, 250);
        gc.fillText("W / A / S / D: Move the tank", 180, 285);
        gc.fillText("Mouse Movement: Aim the cannon", 180, 320);
        gc.fillText("Left Mouse Button: Fire", 180, 355);
        gc.fillText("Right Mouse Button: Call airstrike (warning then strike, 25s cooldown)", 180, 390);

        gc.fillText("Tank Types:", 180, 455);
        gc.fillText("Normal Tank: Continuous fire. Bullets can bounce once.", 180, 490);
        gc.fillText("Shotgun Tank: Fires multiple bullets at once for close-range combat.", 180, 525);
        gc.fillText("Artillery Tank: Launches delayed long-range strikes with area damage.", 180, 560);

        gc.setFill(Color.DARKGRAY);
        gc.fillRect(WIDTH / 2.0 - 100, 620, 200, 50);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRect(WIDTH / 2.0 - 100, 620, 200, 50);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        gc.fillText("Back", WIDTH / 2.0 - 28, 630);
    }

    /** 绘制一辆坦克：车身(履带,跟随行进方向) + 炮塔炮管(跟随瞄准,带后坐 + 炮口闪光)。 */
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

    // ---------- 绘制游戏 ----------
    private void drawGame(GraphicsContext gc) {
        animTick++;
        gc.setFill(Color.rgb(55, 55, 55));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.getCanvas().setFocusTraversable(true);

        // 屏幕震动：整体偏移战场（背景已铺满，露出的也是地面色，无穿帮）
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
            double ph = animTick * 0.12 + s.x;           // 不同道具相位错开
            double core = 10 + Math.sin(ph) * 1.5;
            double ring = 13 + Math.sin(ph) * 3.5;       // 外圈呼吸更明显
            gc.setFill(Color.GREEN);
            gc.fillOval(s.x - core, s.y - core, core * 2, core * 2);
            gc.setStroke(Color.color(1, 1, 1, 0.5 + 0.4 * Math.abs(Math.sin(ph))));
            gc.setLineWidth(2);
            gc.strokeOval(s.x - ring, s.y - ring, ring * 2, ring * 2);
        }

        for (PlayerArtilleryStrike strike : playerStrikes) {
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.setLineDashes(4);
            gc.strokeOval(strike.targetX - strike.strikeRadius, strike.targetY - strike.strikeRadius,
                    strike.strikeRadius * 2, strike.strikeRadius * 2);
            gc.setLineDashes(null);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Impact", FontWeight.NORMAL, 14));
            gc.fillText(String.format("%.1fs", strike.remainingFrames / 60.0),
                    strike.targetX - 10, strike.targetY - 25);
        }

        if (player.type == TANK_ARTILLERY) {
            gc.save();
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeOval(mousePoint.getX() - 16, mousePoint.getY() - 16, 32, 32);
            gc.strokeLine(mousePoint.getX() - 22, mousePoint.getY(), mousePoint.getX() + 22, mousePoint.getY());
            gc.strokeLine(mousePoint.getX(), mousePoint.getY() - 22, mousePoint.getX(), mousePoint.getY() + 22);
            gc.restore();
        }

        if (warningActive) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(3);
            gc.setLineDashes(8);
            gc.strokeOval(warningX - airstrikeRadius, warningY - airstrikeRadius,
                    airstrikeRadius * 2, airstrikeRadius * 2);
            gc.setLineDashes(null);
            gc.setFill(Color.YELLOW);
            gc.fillOval(warningX - 4, warningY - 4, 8, 8);
        }
        if (strikeActive) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(4);
            gc.setFill(Color.rgb(255, 0, 0, 0.2));
            gc.fillOval(warningX - airstrikeRadius, warningY - airstrikeRadius,
                    airstrikeRadius * 2, airstrikeRadius * 2);
            gc.strokeOval(warningX - airstrikeRadius, warningY - airstrikeRadius,
                    airstrikeRadius * 2, airstrikeRadius * 2);
        }

        // 神佑之地
        if (selectedBuff == 1 && safeZone != null && safeZone.active) {
            gc.setFill(Color.rgb(0, 200, 0, 0.35));
            gc.fillRect(safeZone.x, safeZone.y, safeZone.width, safeZone.height);
            gc.setStroke(Color.rgb(0, 255, 0, 0.8));
            gc.setLineWidth(2);
            gc.strokeRect(safeZone.x, safeZone.y, safeZone.width, safeZone.height);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            gc.fillText("神佑", safeZone.x + 4, safeZone.y - 4);
        }

        // 玩家坦克（阵亡定格期间玩家已"爆掉"，不再绘制）
        if (!dying) {
            boolean pBlink = player.invincibleTimer > 0 && (animTick / 4) % 2 == 0;
            if (pBlink) gc.setGlobalAlpha(0.45);       // 无敌时车身闪烁
            drawTankBody(gc, player, 1.0, player.color);
            if (pBlink) gc.setGlobalAlpha(1.0);

            if (player.invincibleTimer > 0) {          // 护盾呼吸脉动
                double pr = 30 + Math.sin(player.invincibleTimer * 0.2) * 3;
                gc.setStroke(Color.rgb(255, 215, 0, 0.75));
                gc.setLineWidth(4);
                gc.strokeOval(player.x - pr, player.y - pr, pr * 2, pr * 2);
            }
        }

        // 敌人
        for (EnemyTank enemy : enemies) {
            if (enemy instanceof BossTank && ((BossTank) enemy).isTeleporting) {
                gc.save();
                gc.setGlobalAlpha(0.25);
                gc.translate(enemy.x, enemy.y);
                gc.rotate(Math.toDegrees(enemy.angle));
                gc.setFill(Color.DARKRED);
                gc.fillRect(-25, -20, 50, 40);
                gc.restore();

                BossTank bTank = (BossTank) enemy;
                gc.save();
                gc.setFill(Color.rgb(0, 0, 0, 0.4));
                gc.fillOval(bTank.tpTargetX - 25, bTank.tpTargetY - 25, 50, 50);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(3);
                gc.strokeOval(bTank.tpTargetX - 25, bTank.tpTargetY - 25, 50, 50);
                gc.restore();
                continue;
            }

            boolean isBoss = (enemy instanceof BossTank) || (enemy instanceof PinkBoss) || (enemy instanceof OrangeBoss);
            double base = isBoss ? 1.5 : 1.0;
            double sIn = enemy.spawnMax > 0
                    ? (0.3 + 0.7 * (1.0 - enemy.spawnTimer / enemy.spawnMax)) : 1.0;
            if (sIn < 0.3) sIn = 0.3;

            // Boss 血量 ≤ 2 的狂暴红色脉动光环
            if (isBoss && enemy.hp <= 2 && enemy.hp > 0) {
                double aura = 42 + Math.abs(Math.sin(animTick * 0.2)) * 10;
                gc.setStroke(Color.color(1, 0.1, 0.1, 0.55));
                gc.setLineWidth(3);
                gc.strokeOval(enemy.x - aura, enemy.y - aura, aura * 2, aura * 2);
            }

            Color hull = enemy.hitFlash > 0 ? Color.WHITE : enemy.color;
            drawTankBody(gc, enemy, base * sIn, hull);

            if (enemy instanceof OrangeBoss) {
                ((OrangeBoss) enemy).draw(gc);
            }

            if (enemy.type == TANK_ARTILLERY && enemy.isCharging) {
                gc.setStroke(Color.RED);
                gc.setLineWidth(1);
                gc.setLineDashes(5);
                double exX = enemy.x + Math.cos(enemy.angle) * 1000;
                double exY = enemy.y + Math.sin(enemy.angle) * 1000;
                gc.strokeLine(enemy.x, enemy.y, exX, exY);
                gc.setLineDashes(null);

                gc.save();
                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                gc.setFill(Color.rgb(255, 0, 0, 0.2));
                gc.fillOval(enemy.chargeTargetX - 80, enemy.chargeTargetY - 80, 160, 160);
                gc.strokeOval(enemy.chargeTargetX - 80, enemy.chargeTargetY - 80, 160, 160);
                gc.restore();
                drawChargeOrb(gc, enemy);
            }
        }

        // 子弹（拖尾 + 火炮弹外发光）
        for (Bullet b : bullets) {
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
            if (b.type == TANK_ARTILLERY) {
                gc.setFill(Color.color(0.35, 0.6, 1.0, 0.30));
                gc.fillOval(b.x - b.radius * 2.2, b.y - b.radius * 2.2, b.radius * 4.4, b.radius * 4.4);
            }
            gc.setFill(b.color);
            gc.fillOval(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
        }

        // 爆炸特效（放大淡出的火焰核心 + 冲击波白环）
        for (ExplosionEffect exp : explosions) {
            double prog = 1.0 - exp.timer / 90.0;        // 0 -> 1 扩张进度
            double a = Math.max(0, exp.timer / 90.0);    // 1 -> 0 淡出
            double r = 20 + prog * 70;                    // 由小炸大
            gc.setFill(Color.color(1.0, 0.55, 0.1, 0.35 * a));   // 外层火焰光晕
            gc.fillOval(exp.x - r, exp.y - r, r * 2, r * 2);
            double rc = r * 0.55;                          // 内核亮黄
            gc.setFill(Color.color(1.0, 0.9, 0.5, 0.6 * a));
            gc.fillOval(exp.x - rc, exp.y - rc, rc * 2, rc * 2);
            double rw = 30 + prog * 90;                    // 冲击波白环（扩张更快）
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

        // ---------- HUD ----------
        gc.setFill(Color.BLACK);
        gc.fillRect(0, HEIGHT - 50, WIDTH, 50);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        gc.fillText("得分: " + score, 40, HEIGHT - 40);
        gc.fillText("关卡: " + level, WIDTH - 160, HEIGHT - 40);
        gc.setFill(Color.RED);
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < player.health; i++) hearts.append("♥");
        gc.fillText(hearts.toString(), 200, HEIGHT - 40);

        String buffStr = "";
        if (selectedBuff == 1) buffStr = "【神佑】";
        else if (selectedBuff == 2) buffStr = "【工业革命】";
        else if (selectedBuff == 3) buffStr = "【制空权】";
        gc.setFill(Color.GOLD);
        gc.fillText(buffStr, 320, HEIGHT - 40);

        if (player.invincibleTimer > 0) {
            gc.setFill(Color.CYAN);
            gc.fillText("护盾: " + String.format("%.1fs", player.invincibleTimer / 60.0), 440, HEIGHT - 40);
        }

        if (warningActive) {
            gc.setFill(Color.YELLOW);
            gc.fillText(String.format("【空袭预警中】 %.1fs", warningTimer / 60.0), 560, HEIGHT - 40);
        } else if (strikeActive) {
            gc.setFill(Color.RED);
            gc.fillText(String.format("【空袭进行中！】 %.1fs", strikeTimer / 60.0), 560, HEIGHT - 40);
        } else if (airstrikeCooldown > 0) {
            gc.setFill(Color.ORANGE);
            gc.fillText(String.format("【空袭冷却中】 %.1fs", airstrikeCooldown / 60.0), 560, HEIGHT - 40);
        } else {
            gc.setFill(Color.GREENYELLOW);
            gc.fillText("【鼠标右键】 呼叫空袭 (就绪)", 560, HEIGHT - 40);
        }

        // Boss 登场预警：红色描边脉动 + 大字
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

        if (showLevelTimer > 0) {
            gc.setFill(Color.rgb(255, 215, 0, 0.45));
            gc.setFont(Font.font("Comic Sans MS", FontWeight.BOLD, 40));
            gc.fillText("STAGE " + level, WIDTH / 2.0 - 90, HEIGHT / 2.0 - 150);
        }

        if (isCountingDown) {
            gc.save();
            gc.setTextBaseline(VPos.CENTER);
            if (countdownTimer > 30) {
                int secondsLeft = (int)((countdownTimer - 31) / 60) + 1;
                int frameInSecond = (int)((countdownTimer - 31) % 60);
                double scale = 1.0 + (frameInSecond / 60.0) * 0.6;
                gc.setFont(Font.font("Impact", FontWeight.BOLD, 90 * scale));
                gc.setFill(Color.BLACK);
                gc.fillText(String.valueOf(secondsLeft), WIDTH / 2.0 - 20, HEIGHT / 2.0 - 20);
                gc.getCanvas().setFocusTraversable(true);
                gc.setFill(Color.rgb(255, 69, 0));
                gc.fillText(String.valueOf(secondsLeft), WIDTH / 2.0 - 23, HEIGHT / 2.0 - 23);
            } else {
                gc.setFont(Font.font("Impact", FontWeight.BOLD, 100));
                gc.setFill(Color.GREENYELLOW);
                gc.fillText("READY? GO!!", WIDTH / 2.0 - 220, HEIGHT / 2.0 - 30);
            }
            gc.restore();
        }

        // Boss 顶部血条（横向排列、缓降 + 受击抖动）
        drawBossBars(gc);
    }

    // ---------- Boss 顶部横向血条 ----------
    private void drawBossBars(GraphicsContext gc) {
        ArrayList<EnemyTank> bosses = new ArrayList<>();
        for (EnemyTank e : enemies) {
            if (e instanceof BossTank || e instanceof PinkBoss || e instanceof OrangeBoss) {
                bosses.add(e);
            }
        }
        int n = bosses.size();
        if (n == 0) return;

        double margin = 40, gap = 15, top = 18, barH = 22;
        double barW = (WIDTH - margin * 2 - gap * (n - 1)) / n;   // 长度随 Boss 数量自适应
        gc.setTextBaseline(VPos.TOP);

        for (int i = 0; i < n; i++) {
            EnemyTank b = bosses.get(i);
            if (b.displayHp < 0) b.displayHp = b.hp;
            // 缓降：显示值平滑追向真实血量
            b.displayHp += (b.hp - b.displayHp) * Math.min(1.0, 0.15 * dt);
            if (Math.abs(b.displayHp - b.hp) < 0.01) b.displayHp = b.hp;

            // 受击抖动偏移
            double sx = 0, sy = 0;
            if (b.hitShakeTimer > 0) {
                double mag = 4;
                sx = (random.nextDouble() * 2 - 1) * mag;
                sy = (random.nextDouble() * 2 - 1) * mag;
                b.hitShakeTimer -= dt;
            }

            double x = margin + i * (barW + gap) + sx;
            double y = top + sy;
            double maxHp = Math.max(1, b.maxHp);
            double hpRatio = Math.max(0, b.hp) / maxHp;
            double ghostRatio = Math.max(0, b.displayHp) / maxHp;

            // 底框
            gc.setFill(Color.rgb(0, 0, 0, 0.55));
            gc.fillRoundRect(x - 3, y - 3, barW + 6, barH + 6, 8, 8);
            gc.setFill(Color.rgb(50, 50, 50));
            gc.fillRect(x, y, barW, barH);
            // 白色残影（缓降）
            gc.setFill(Color.rgb(255, 255, 255, 0.55));
            gc.fillRect(x, y, barW * ghostRatio, barH);
            // 实血（Boss 颜色）
            gc.setFill(b.color);
            gc.fillRect(x, y, barW * hpRatio, barH);
            // 边框
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeRect(x, y, barW, barH);
            // HP 文本
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
            gc.fillText((int) Math.ceil(Math.max(0, b.hp)) + "/" + (int) maxHp, x + 5, y + 5);
        }
    }

    // ---------- 游戏结束 ----------
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
}
