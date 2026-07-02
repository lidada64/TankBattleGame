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
    // 战机专用变量
    private double planeX = -200; // 初始位置在屏幕外
    private double planeY = 150;  // 默认飞行高度
    private boolean planeFlying = false; // 是否正在飞行
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
    private Tank menuDisplayTank;
    private int menuStage = 0; // 0: 主菜单(开始/退出), 1: 选坦克/选Buff
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
    public static long animTick = 0;                // 全局动画时钟（呼吸/闪烁/履带）
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
        this.menuDisplayTank = new Tank(this, 0, 0, 0);
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
        // ==================== 🛠️ 空袭逻辑与战机动画触发 ====================
        if (warningActive) {
            warningTimer -= dt;
            if (warningTimer <= 0) {
                warningActive = false;
                strikeActive = true;
                strikeTimer = STRIKE_DURATION;

                // --- 【新增】触发战机飞过特效 ---
                planeX = -200;                                     // 从左侧屏幕外进入
                planeY = 50 + new java.util.Random().nextInt(200); // 在屏幕上半区随机高度
                planeFlying = true;                                // 激活飞机动画
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

        if (planeFlying) {
            planeX += 15; // 飞行速度（可以根据手感调整）
            if (planeX > WIDTH + 300) { // 飞出右侧屏幕后停止
                planeFlying = false;
            }
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
            // ==================== 🏠 主界面第一层：纯粹的主菜单 ====================
            if (menuStage == 0) {
                double btnW = 200;
                double btnH = 45;
                double btnX = WIDTH / 2.0 - btnW / 2.0;

                double startBtnY = HEIGHT / 2.0 - 10; // 【开始游戏】按钮的Y范围
                double exitBtnY = HEIGHT / 2.0 + 50;   // 【退出游戏】按钮的Y范围

                // 点了【开始游戏】 -> 平滑进入选坦克界面
                if (mx >= btnX && mx <= btnX + btnW && my >= startBtnY && my <= startBtnY + btnH) {
                    if (sound != null) sound.play("button");
                    menuStage = 1;
                    return;
                }
                // 点了【退出游戏】 -> 直接干净利落关闭程序
                else if (mx >= btnX && mx <= btnX + btnW && my >= exitBtnY && my <= exitBtnY + btnH) {
                    System.exit(0);
                    return;
                }
            }
            // ==================== 🛠️ 主界面第二层：你原本的选坦克逻辑 ====================
            else if (menuStage == 1) {
                // 右上角的帮助说明按钮（保持你原本的逻辑）
                if (mx >= WIDTH - 70 && mx <= WIDTH - 30 && my >= 20 && my <= 60) {
                    gameState = STATE_HELP;
                    return;
                }
                // 选择三辆坦克型号（0, 1, 2）的逻辑
                int[] selectXs = {WIDTH / 2 - 250, WIDTH / 2 - 30, WIDTH / 2 + 190};
                for (int i = 0; i < 3; i++) {
                    if (mx >= selectXs[i] - 40 && mx <= selectXs[i] + 100 && my >= 230 && my <= 380) {
                        if (selectedTankType != i && sound != null) sound.play("button");
                        selectedTankType = i;
                    }
                }
                // 点击底部的“确认选择”（原本写着去 STATE_SELECT 的那个按钮）
                if (mx >= WIDTH / 2 - 100 && mx <= WIDTH / 2 + 100 && my >= 460 && my <= 520) {
                    if (sound != null) sound.play("button");
                    gameState = STATE_SELECT; // 平滑切入下一步：选 Buff 状态
                }
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
                    menuStage = 0; // 【核心重置】开局时把主菜单状态重置回第一层，方便死后重新进来
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
            // 这一块顺便把原本偏右不准的重启按钮范围，和我们刚才改好的“绝对时间电荷按钮”完全对齐
            double btnX = WIDTH / 2.0 - 120;
            double btnY = HEIGHT / 2.0 + 75;
            double btnW = 240;
            double btnH = 42;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                if (sound != null) { sound.play("button"); sound.playBgm("bgm_menu"); }
                gameState = STATE_MENU;
                menuStage = 0; // 死后返回主界面，依然进入最开始的第一层
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

    private void drawSelectScene(GraphicsContext gc) {
        // 1. 底色
        gc.setFill(Color.rgb(10, 15, 25));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // 2. 动态时间因子
        double timeTick = System.currentTimeMillis() * 0.05;
        double floatY = Math.sin(timeTick * 0.1) * 8; // 让所有卡牌一起缓慢上下浮动

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 36));
        gc.fillText("选择你的战略卡牌", WIDTH / 2.0 - 150, 80);

        String[] titles = {"神佑", "工业革命", "制空权"};
        String[] descs = {
                "信徒们，虔诚的祈愿吧，\n战斗过程中随机刷新神佑之地",
                "铁与火铸就传奇，\n所有坦克的子弹伤害加1",
                "我方已消灭敌方空军，\n空袭范围增加，但飞机整备时间增加"
        };
        Color[] cardColors = {Color.GREEN, Color.YELLOW, Color.CYAN};

        int cardWidth = 200, cardHeight = 260;
        int gap = 30;
        int startX = (WIDTH - (cardWidth * 3 + gap * 2)) / 2;

        int cardY = (int) Math.round((HEIGHT - cardHeight) / 2 + 20 + floatY);

        for (int i = 0; i < 3; i++) {
            int x = startX + i * (cardWidth + gap);
            int y = (int)cardY;

            // 【新增呼吸光圈效果】
            double breath = 0.6 + 0.2 * Math.sin(timeTick * 0.15);
            gc.setStroke(Color.color(1, 1, 1, breath * 0.5));
            gc.setLineWidth(4);
            gc.strokeRoundRect(x - 5, y - 5, cardWidth + 10, cardHeight + 10, 20, 20);

            // 卡牌渲染
            gc.setFill(Color.rgb(30, 40, 50, 0.9));
            gc.fillRoundRect(x, y, cardWidth, cardHeight, 15, 15);

            // 边框
            gc.setStroke(cardColors[i]);
            gc.setLineWidth(2);
            gc.strokeRoundRect(x, y, cardWidth, cardHeight, 15, 15);

            // 标题与文字
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
            gc.fillText(titles[i], x + 20, y + 40);

            gc.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 14));
            gc.setFill(Color.rgb(200, 220, 255));
            String[] lines = descs[i].split("\n");
            for (int j = 0; j < lines.length; j++) {
                gc.fillText(lines[j], x + 15, y + 80 + j * 25);
            }
        }
    }

    // ---------- 菜单 ----------
    private void drawMenu(GraphicsContext gc) {
        gc.setTextBaseline(VPos.TOP);

        // 1. 【重工业暗夜底色】
        gc.setFill(Color.rgb(10, 14, 20));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        double timeTick = System.currentTimeMillis() * 0.05;

        // ==================== STAGE 0: 主菜单 ====================
        if (menuStage == 0) {
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 54));
            gc.setFill(Color.rgb(255, 0, 0, 0.12));
            gc.fillText("TANK BATTLE", WIDTH / 2.0 - 183, HEIGHT / 2.0 - 138);
            gc.setFill(Color.rgb(240, 255, 255));
            gc.fillText("TANK BATTLE", WIDTH / 2.0 - 185, HEIGHT / 2.0 - 140);

            double btnW = 200, btnH = 45;
            double btnX = WIDTH / 2.0 - btnW / 2.0;

            // 按钮 A：开始游戏
            double startY = HEIGHT / 2.0 - 10;
            gc.setFill(Color.rgb(0, 220, 255, 0.08));
            gc.fillRect(btnX, startY, btnW, btnH);
            gc.setStroke(Color.color(0.0, 0.8, 1.0, 0.5 + 0.3 * Math.sin(timeTick * 0.1)));
            gc.strokeRect(btnX, startY, btnW, btnH);
            gc.setFill(Color.rgb(0, 230, 255));
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
            gc.fillText("⚡ 开始游戏", btnX + 52, startY + 11);

            // 按钮 B：退出游戏
            double exitY = HEIGHT / 2.0 + 50;
            gc.setFill(Color.rgb(255, 60, 70, 0.06));
            gc.fillRect(btnX, exitY, btnW, btnH);
            gc.setStroke(Color.color(1.0, 0.2, 0.3, 0.4 + 0.2 * Math.sin(timeTick * 0.1 + Math.PI)));
            gc.strokeRect(btnX, exitY, btnW, btnH);
            gc.setFill(Color.rgb(255, 70, 80));
            gc.fillText("❌ 退出游戏", btnX + 52, exitY + 11);

        }
        // ==================== STAGE 1: 坦克选人 ====================
        else if (menuStage == 1) {
            gc.setFill(Color.rgb(100, 150, 180));
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
            gc.fillText("⚙️ 战术终端：请指派重装坦克装甲型号...", 40, 25);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 45));
            gc.fillText("TANK BATTLE", WIDTH / 2.0 - 140, 60);

            int[] selectXs = {WIDTH / 2 - 250, WIDTH / 2 - 30, WIDTH / 2 + 190};
            String[] titles = {"普通坦克", "散弹坦克", "自行火炮"};
            // 赋予三种坦克专属识别色
            Color[] colors = {Color.rgb(0, 255, 128), Color.rgb(255, 215, 0), Color.rgb(0, 191, 255)};

            for (int i = 0; i < 3; i++) {
                // 选框
                gc.setStroke(selectedTankType == i ? Color.RED : Color.rgb(255, 255, 255, 0.1));
                gc.setLineWidth(selectedTankType == i ? 3 : 1);
                gc.strokeRect(selectXs[i] - 40, 230, 140, 150);

                // 核心渲染逻辑
                menuDisplayTank.type = i;
                menuDisplayTank.x = selectXs[i] + 30;
                menuDisplayTank.y = 295;
                menuDisplayTank.bodyAngle = Math.PI / 2; // 车身朝下
                menuDisplayTank.angle = -Math.PI / 2 + Math.sin(System.currentTimeMillis() * 0.003) * 0.5; // 炮管随时间摆动
                menuDisplayTank.treadPhase = System.currentTimeMillis() * 0.05; // 履带滚动

                // 调用战斗渲染 (缩放 1.5 倍)
                drawTankBody(gc, menuDisplayTank, 1.5, colors[i]);

                // 文字标注
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
                gc.fillText(titles[i], selectXs[i] - 10, 350);
            }

            // 下一步确认按钮
            gc.setFill(Color.DARKGRAY);
            gc.fillRect(WIDTH / 2.0 - 100, 460, 200, 60);
            gc.setStroke(Color.WHITE);
            gc.strokeRect(WIDTH / 2.0 - 100, 460, 200, 60);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
            gc.fillText("开始游戏", WIDTH / 2.0 - 48, 475);
        }
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
// 1. 【高辨识深色底色】极深的冷矿物青，能把坦克、子弹和亮色全息雪花衬托得非常极其鲜明
        gc.setFill(Color.rgb(12, 16, 22));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // ========== 🌌 科技结合：缓慢呼吸全息拐角 + 动态像素电子雪 ==========
        int gridSize = 60; // 全息地砖的尺寸

        // 【部分 A：让背景全息拐角缓慢闪动（波浪呼吸动效）】
        int cornerSize = 4;
        for (int x = 0; x <= WIDTH; x += gridSize) {
            for (int y = 0; y <= HEIGHT - 50; y += gridSize) {

                // 🛠️ 核心优化：利用正弦波计算每个坐标点独特的闪烁相位
                // animTick * 0.03 控制闪动速度，数值越小闪得越慢、越柔和
                // (x + y) * 0.005 让不同的拐角错开时间亮起，形成缓慢流动的科技微光波纹
                double cornerWave = Math.sin(animTick * 0.03 + (x + y) * 0.005);

                // 将波形映射到一个舒适的透明度区间（最暗 0.04，最亮 0.22），保证绝对不抢子弹风头
                double cornerAlpha = 0.13 + 0.09 * cornerWave;

                gc.setStroke(Color.color(0.0, 0.7, 1.0, cornerAlpha)); // 动态透明度的青蓝色
                gc.setLineWidth(1);

                // 绘制左上角 L 拐角
                gc.strokeLine(x, y, x + cornerSize, y);
                gc.strokeLine(x, y, x, y + cornerSize);

                // 绘制右下角 L 拐角
                if (x - gridSize >= 0 && y - gridSize >= 0) {
                    gc.strokeLine(x, y, x - cornerSize, y);
                    gc.strokeLine(x, y, x, y - cornerSize);
                }
            }
        }

        // 【部分 B：10行代码搞定全息动态落雪（带摇摆动效，无需多余变量）】
        for (int i = 0; i < 45; i++) {
            // 利用数学哈希算出每片雪花独一无二的 X 和 Y 轴轨迹
            // animTick * 1.5 控制下落速度，加 Math.sin 让雪花随风左右微微摇摆
            double x = (Math.abs(Math.sin(i * 99)) * WIDTH + Math.sin(animTick * 0.03 + i) * 25) % WIDTH;
            double y = (Math.abs(Math.cos(i * 45)) * (HEIGHT - 50) + animTick * (1.1 + (i % 3) * 0.4)) % (HEIGHT - 50);
            double size = 2 + (i % 3); // 像素雪花大小 2~4 像素

            // 赋予雪花“全息电子”的色彩：核心是高亮白，外圈带一点霓虹蓝的微光晕
            if (i % 2 == 0) {
                gc.setFill(Color.rgb(0, 220, 255, 0.4)); // 全息蓝雪花
            } else {
                gc.setFill(Color.rgb(240, 250, 255, 0.8)); // 炫白晶体雪花
            }

            // 绘制像素风方形电子雪
            gc.fillRect(x, y, size, size);
        }
        // ===================================================================
        gc.getCanvas().setFocusTraversable(true);

        // 屏幕震动：整体偏移战场（背景已铺满，露出的也是地面色，无穿帮）
        double shX = 0, shY = 0;
        if (shakeMag > 0) {
            shX = (random.nextDouble() * 2 - 1) * shakeMag;
            shY = (random.nextDouble() * 2 - 1) * shakeMag;
        }
        gc.save();
        gc.translate(shX, shY);

        // ========== 替换开始：古城墙花纹升级版 ==========
        for (Wall w : walls) {
            // 1. 填底色：使用复古的暗青灰色
            gc.setFill(Color.rgb(80, 85, 90));
            gc.fillRect(w.x, w.y, w.width, w.height);

            // 2. 绘制每一块砖的砖缝线
            gc.setStroke(Color.rgb(40, 43, 45)); // 砖缝颜色（暗色）
            gc.setLineWidth(1);                  // 细线表示砖缝

            int brickH = 12; // 每层砖的高度
            int brickW = 24; // 每块砖的宽度

            // 【横向砖缝】从城墙顶部开始，每隔 brickH 画一条横线
            for (int rowY = w.y + brickH; rowY < w.y + w.height; rowY += brickH) {
                gc.strokeLine(w.x, rowY, w.x + w.width, rowY);
            }

            // 【纵向交错砖缝】奇数行和偶数行错开半块砖的宽度，形成“工”字形砌砖感
            int rowIndex = 0;
            for (int rowY = w.y; rowY < w.y + w.height; rowY += brickH) {
                int shift = (rowIndex % 2 == 0) ? 0 : brickW / 2;
                for (int colX = w.x + shift; colX < w.x + w.width; colX += brickW) {
                    double nextY = Math.min(rowY + brickH, w.y + w.height);
                    gc.strokeLine(colX, rowY, colX, nextY);
                }
                rowIndex++;
            }

            // 3. 增强立体感：给整面城墙加一个更深的外部大描边
            gc.setStroke(Color.rgb(30, 32, 35));
            gc.setLineWidth(2);
            gc.strokeRect(w.x, w.y, w.width, w.height);

            // 4. 给城墙顶部边缘加一层浅色“高光”，假装有上方光照
            gc.setStroke(Color.rgb(120, 125, 130));
            gc.setLineWidth(1.5);
            gc.strokeLine(w.x, w.y, w.x + w.width, w.y);
        }
        // ========== 替换结束 ==========

        for (ShieldItem s : shields) {
            double ph = animTick * 0.12 + s.x;           // 不同道具相位错开

            // 每一个数字代表一个点的颜色：0-透明，1-深黑边框，2-银灰色盾面，3-护盾核心
            int[][] shieldSprite = {
                    {0, 0, 1, 1, 1, 1, 1, 1, 0, 0},
                    {0, 1, 2, 2, 2, 2, 2, 2, 1, 0},
                    {1, 2, 2, 2, 2, 2, 2, 2, 2, 1},
                    {1, 2, 2, 3, 3, 3, 3, 2, 2, 1},
                    {1, 2, 2, 3, 3, 3, 3, 2, 2, 1},
                    {1, 2, 2, 3, 3, 3, 3, 2, 2, 1},
                    {0, 1, 2, 2, 3, 3, 2, 2, 1, 0},
                    {0, 0, 1, 2, 2, 2, 2, 1, 0, 0},
                    {0, 0, 0, 1, 2, 2, 1, 0, 0, 0},
                    {0, 0, 0, 0, 1, 1, 0, 0, 0, 0}
            };

            // 【动态呼吸：大小缩放】
            // 基础像素大小是 2.0，利用正弦波让它在 1.7 ~ 2.3 之间微微呼吸膨胀
            double pixelSize = 2.0 + Math.sin(ph) * 0.3;

            // 计算左上角起始点，使呼吸缩放时始终保持正中心对齐 s.x 和 s.y
            double startX = s.x - (shieldSprite[0].length * pixelSize) / 2.0;
            double startY = s.y - (shieldSprite.length * pixelSize) / 2.0;

            for (int i = 0; i < shieldSprite.length; i++) {
                for (int j = 0; j < shieldSprite[i].length; j++) {
                    int colorType = shieldSprite[i][j];
                    if (colorType == 0) continue; // 透明，不绘制

                    // 1. 绘制黑色复古边框
                    if (colorType == 1) {
                        gc.setFill(Color.rgb(30, 30, 30));
                    }
                    // 2. 绘制银灰色金属盾面
                    else if (colorType == 2) {
                        gc.setFill(Color.rgb(170, 175, 180));
                    }
                    // 3. 绘制亮蓝色护盾核心【动态颜色：闪烁】
                    // 利用你原本的 Math.sin(ph) 机制，让核心部分在亮蓝和纯白之间循环渐变，充满能量感！
                    else if (colorType == 3) {
                        double pulse = 0.5 + 0.5 * Math.sin(ph); // 算出 0.0 到 1.0 的变化值
                        gc.setFill(Color.rgb(
                                (int)(0 + pulse * 255),    // R 从 0 到 255 (变白)
                                (int)(220 + pulse * 35),   // G 从 220 到 255 (变白)
                                255                        // B 保持最高
                        ));
                    }

                    // 绘制像素方块
                    gc.fillRect(startX + j * pixelSize, startY + i * pixelSize, pixelSize, pixelSize);
                }
            }

            // 4. 【保留原版外圈特效】在像素盾牌外面加一层你原本酷炫的动态呼吸能量环
            double ring = 15 + Math.sin(ph) * 3.5; // 外圈半径稍稍加大一点防止挡住盾牌
            gc.setStroke(Color.color(0, 0.8, 1, 0.3 + 0.3 * Math.abs(Math.sin(ph)))); // 改为科技蓝色的呼吸光环
            gc.setLineWidth(1.5);
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
        // 神佑之地 UI 终极进化：星河旋涡神圣法阵
        if (selectedBuff == 1 && safeZone != null && safeZone.active) {
            // 1. 自动转换完美的圆形半径与圆心
            double cx = safeZone.x + safeZone.width / 2.0;
            double cy = safeZone.y + safeZone.height / 2.0;
            double r = (safeZone.width + safeZone.height) / 4.0;

            // 2. 【圣地极淡核心流光】（透明度压到 0.03，确保子弹像在镜面上飞一样清晰）
            gc.setFill(Color.rgb(0, 255, 180, 0.03));
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);

            // 3. 【双层神圣内敛几何圆环】
            double alpha = 0.5 + 0.2 * Math.sin(animTick * 0.05);
            gc.setStroke(Color.color(0.0, 1.0, 0.6, alpha));
            gc.setLineWidth(1.2);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2); // 主外环

            gc.setStroke(Color.color(0.8, 1.0, 0.4, alpha * 0.4)); // 金绿色内环
            gc.strokeOval(cx - r + 4, cy - r + 4, (r - 4) * 2, (r - 4) * 2);

            // 4. 【新添：圣光能量脉冲】（隔一段时间从中心向外平滑扩散一圈淡金色光环）
            double pulseProgress = (animTick * 0.5) % r; // 脉冲半径随时间扩大
            double pulseAlpha = (1.0 - (pulseProgress / r)) * 0.25; // 越往外越淡
            if (pulseAlpha > 0) {
                gc.setStroke(Color.color(0.9, 1.0, 0.5, pulseAlpha));
                gc.setLineWidth(1);
                gc.strokeOval(cx - pulseProgress, cy - pulseProgress, pulseProgress * 2, pulseProgress * 2);
            }

            // 5. 【缓慢自转的神圣刻度】
            gc.save();
            gc.translate(cx, cy);
            gc.rotate(animTick * 0.15); // 调慢旋转速度，更显沉稳、神圣
            gc.setStroke(Color.color(0.0, 1.0, 0.7, alpha * 0.3));
            for (int i = 0; i < 4; i++) {
                gc.rotate(90);
                gc.strokeLine(0, -r + 12, 0, -r + 2); // 悬空的刻度线
            }
            gc.restore();

            // 6. 【重构：星河旋涡向心粒子（去臃肿，变轻盈）】
            // 100% 纯数学公式计算，不加新变量，生成星河凝聚特效
            for (int i = 0; i < 20; i++) {
                // 利用哈希公式让每个粒子有不同的生命周期和凝聚速度
                double pSpeed = 0.4 + (i % 3) * 0.2;
                double currentProgress = (animTick * pSpeed + i * 35) % r;

                // 【核心改变】：半径从 r（边缘）向 0（中心）缩减，实现向心凝聚！
                double currentR = r - currentProgress;

                // 给粒子加上随半径缩小的旋转角度，形成完美的“黄金螺旋旋涡”轨迹
                double angle = (animTick * 0.02) + (currentProgress * 0.04) + (i * 1.5);

                double pX = cx + Math.cos(angle) * currentR;
                double pY = cy + Math.sin(angle) * currentR;

                // 计算闪烁的透明度（在边缘和中心时自动淡出，中间最亮）
                double pAlpha = Math.sin((currentProgress / r) * Math.PI) * 0.65;

                if (pAlpha > 0) {
                    // 升级为极细的 1.5 像素微型纯白/金光星芒点
                    if (i % 2 == 0) {
                        gc.setFill(Color.color(1.0, 1.0, 1.0, pAlpha)); // 纯白圣光
                    } else {
                        gc.setFill(Color.color(0.9, 1.0, 0.4, pAlpha * 0.8)); // 圣洁金芒
                    }
                    // 渲染极其轻盈的像素点
                    gc.fillRect(pX - 0.75, pY - 0.75, 1.5, 1.5);
                }
            }

            // 7. 【优雅的艺术字标】
            gc.setFill(Color.color(0.9, 1.0, 0.9, 0.6 + 0.2 * Math.sin(animTick * 0.05)));
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
            gc.fillText("✨ SHINE SANCTUARY", cx - 60, cy - r - 10); // 换成英文或者你喜欢的字，排版更大气
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
        if (planeFlying) {
            gc.save();
            // 简单绘制一个机身
            gc.setFill(Color.rgb(50, 60, 70));
            gc.fillPolygon(new double[]{planeX, planeX - 40, planeX - 40},
                    new double[]{planeY, planeY - 20, planeY + 20}, 3);
            gc.fillRect(planeX - 50, planeY - 5, 60, 10);

            // 绘制尾焰，让它看起来有动力
            gc.setFill(Color.ORANGE);
            gc.fillRect(planeX - 60, planeY - 3, 10, 6);
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

        // 1. 【重工业暗夜底色】
        gc.setFill(Color.rgb(10, 14, 20, 0.96));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // 使用系统绝对毫秒数作为动画因子，确保即使游戏暂停/结束，动效也绝不断电！
        double timeTick = System.currentTimeMillis() * 0.05;

        // 2. 【全息雷达战损扫描线（绝对时间驱动 - 强制旋转！）】
        gc.save();
        gc.translate(WIDTH / 2.0, HEIGHT / 2.0);
        gc.rotate(timeTick * 0.5); // 绝对时间驱动旋转，每毫秒都在疯狂自转！

        // 使用渐变色，营造出扫描线掠过时留下的暗红残影
        gc.setStroke(new javafx.scene.paint.LinearGradient(
                0, 0, WIDTH, 0, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.rgb(255, 0, 0, 0.15)),
                new javafx.scene.paint.Stop(1.0, Color.rgb(255, 0, 0, 0.0))
        ));
        gc.setLineWidth(3.0);
        gc.strokeLine(0, 0, WIDTH, 0); // 绘制旋转半径
        gc.restore();

        // 3. 【四角重装铁甲警告边框】
        gc.setStroke(Color.rgb(255, 60, 60, 0.25));
        gc.setLineWidth(2.0);
        gc.strokeRect(20, 20, WIDTH - 40, HEIGHT - 40);

        // 四角加粗工业切角，显得厚重、粗犷
        gc.setFill(Color.rgb(255, 60, 60, 0.6));
        int edgeSize = 12;
        gc.fillRect(20, 20, edgeSize, 3); gc.fillRect(20, 20, 3, edgeSize); // 左上
        gc.fillRect(WIDTH - 20 - edgeSize, 20, edgeSize, 3); gc.fillRect(WIDTH - 20, 20, 3, edgeSize); // 右上
        gc.fillRect(20, HEIGHT - 20, edgeSize, 3); gc.fillRect(20, HEIGHT - 20 - edgeSize, 3, edgeSize); // 左下
        gc.fillRect(WIDTH - 20 - edgeSize, HEIGHT - 20, edgeSize, 3); gc.fillRect(WIDTH - 20, HEIGHT - 20 - edgeSize, 3, edgeSize); // 右下

        gc.getCanvas().setFocusTraversable(true);

        // 4. 【战损标题：坦克失联】 —— 带红色全息重影
        // 红色背影重影
        gc.setFill(Color.rgb(255, 0, 0, 0.12));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 52));
        gc.fillText("坦克失联 / CONNECTION LOST", WIDTH / 2.0 - 343, HEIGHT / 2.0 - 123);

        // 正面主大字
        gc.setFill(Color.rgb(255, 65, 65));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 50));
        gc.fillText("坦克失联 / CONNECTION LOST", WIDTH / 2.0 - 345, HEIGHT / 2.0 - 125);

        // 5. 【工业战绩舱】—— 绘制一个精致的金属装甲数据框包裹战绩
        double panelX = WIDTH / 2.0 - 200;
        double panelY = HEIGHT / 2.0 - 40;
        double panelW = 400;
        double panelH = 72;

        // 数据板微弱半透明底色
        gc.setFill(Color.rgb(20, 30, 40, 0.4));
        gc.fillRect(panelX, panelY, panelW, panelH);

        // 银白细线外框
        gc.setStroke(Color.rgb(255, 255, 255, 0.2));
        gc.setLineWidth(1.0);
        gc.strokeRect(panelX, panelY, panelW, panelH);

        // 数据文本：采用科技感的等宽 Courier New 字体
        gc.setFill(Color.rgb(220, 240, 255, 0.9));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 20));

        String finalScore = "FINAL SCORE : " + String.format("%06d", score);
        String finalLevel = "MAX STAGE   : " + String.format("%02d", level);

        gc.fillText(finalScore, panelX + 45, panelY + 12);
        gc.fillText(finalLevel, panelX + 45, panelY + 38);

        // 6. 【重工业重新连接按钮（绝对时间流动）】
        double btnX = WIDTH / 2.0 - 120;
        double btnY = HEIGHT / 2.0 + 75;
        double btnW = 240;
        double btnH = 42;

        // 按钮淡青底色
        gc.setFill(Color.rgb(0, 220, 255, 0.06));
        gc.fillRect(btnX, btnY, btnW, btnH);

        // 绝对时间驱动：边框随时间呈正弦波平滑呼吸闪烁
        double btnAlpha = 0.4 + 0.3 * Math.sin(timeTick * 0.15);
        gc.setStroke(Color.color(0.0, 0.85, 1.0, btnAlpha));
        gc.setLineWidth(1.5);
        gc.strokeRect(btnX, btnY, btnW, btnH);

        // 绝对时间驱动：在按钮两侧绘制高速向内侧滚动的科技能量电荷线！
        gc.setStroke(Color.color(0.0, 0.9, 1.0, btnAlpha * 0.5));
        double flowOffset = (timeTick * 0.6) % 15;
        gc.strokeLine(btnX + 5 + flowOffset, btnY + 4, btnX + 5 + flowOffset, btnY + btnH - 4);
        gc.strokeLine(btnX + btnW - 5 - flowOffset, btnY + 4, btnX + btnW - 5 - flowOffset, btnY + btnH - 4);

        // 按钮内部提示文字：点击屏幕重新连接
        gc.setFill(Color.rgb(0, 240, 255, 0.9));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 15));
        gc.fillText("⚡ 点击屏幕 重新连接", btnX + 38, btnY + 11);
    }
}
