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

public class Launcher extends Application {

    // ========== 内部类：敌方坦克 ==========
    class EnemyTank extends Tank {
        protected int hp = 1;
        protected int maxHp = 1;              // Boss血条用
        protected double displayHp = -1;      // 血条缓降的显示值(懒初始化为hp)
        protected double hitShakeTimer = 0;   // 受击抖动剩余帧
        private int aiState = 0;
        private double stateTimer = 0;
        @SuppressWarnings("unused")
        private double escapeAngle = 0;

        public EnemyTank(double x, double y, int type) {
            super(x, y, type);
            this.color = Color.GREEN;
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
                double slideY = (dy != 0) ? dy : (random.nextBoolean() ? 1.5 * dt : -1.5 * dt);
                if (!isCollidingWithWalls(x, y + slideY, size)) y += slideY;
            }

            if (!hitWallY && !hitEnemyY) {
                y = targetNextY;
            } else if (hitWallY && !hitEnemyY) {
                double slideX = (dx != 0) ? dx : (random.nextBoolean() ? 1.5 * dt : -1.5 * dt);
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
            if (this instanceof BossTank && ((BossTank) this).isTeleporting) {
                return;
            }

            double distToPlayer = getDistance(x, y, player.x, player.y);
            stateTimer -= dt;
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

            double baseMoveSpeed = (aiState == 0) ? 1.0 * SPEED_MULTIPLIER * dt : 1.6 * SPEED_MULTIPLIER * dt;

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

            if (!isCharging && cooldown <= 0 && distToPlayer < 500 && random.nextDouble() < 0.04 * dt) {
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
                cooldown = 85;
            } else if (type == TANK_ARTILLERY) {
                isCharging = true;
                chargeTimer = 90;
                chargeTargetX = p.x;
                chargeTargetY = p.y;
                cooldown = 160;
            }
        }
    }

    // ========== Boss：粉色 ==========
    class PinkBoss extends EnemyTank {
        public PinkBoss(double x, double y) {
            super(x, y, TANK_NORMAL);
            this.color = Color.PINK;
            this.size = 50;
            this.hp = 5;
            this.maxHp = 5;
        }

        @Override
        public void updateAI(PlayerTank player, ArrayList<Bullet> bList, ArrayList<Wall> wList) {
            if (player == null) return;

            double baseMoveSpeed = 1.2 * dt;
            double mx = (player.x > x) ? baseMoveSpeed : -baseMoveSpeed;
            double my = (player.y > y) ? baseMoveSpeed : -baseMoveSpeed;
            if (getDistance(x + mx, y + my, player.x, player.y) > 45) {
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
                        Bullet b = new Bullet(x, y, angle, false, TANK_NORMAL, true, 3);
                        b.isEnemyBullet = true;
                        b.color = Color.PINK;
                        b.life = 180;
                        bList.add(b);
                    }
                }
                cooldown = 180;
            }
        }
    }

    // ========== Boss：橙色 ==========
    class OrangeBoss extends EnemyTank {
        boolean hasShield = true;
        double shieldCooldown = 720;
        double flameCooldown = 0;
        double flameWarningTimer = 0;
        boolean flameActive = false;
        double flameDuration = 0;
        double flameLength = 270;
        double flameWidth;
        double flameDamageTimer = 0;

        public OrangeBoss(double x, double y) {
            super(x, y, TANK_NORMAL);
            this.color = Color.ORANGE;
            this.size = 50;
            this.hp = 5;
            this.maxHp = 5;
            this.flameWidth = this.size;
            hasShield = true;
            shieldCooldown = 720;
        }

        @Override
        public void update() {
            super.update();

            if (!hasShield) {
                shieldCooldown -= dt;
                if (shieldCooldown <= 0) {
                    hasShield = true;
                    shieldCooldown = 720;
                }
            }

            if (flameCooldown > 0) {
                flameCooldown -= dt;
                if (flameCooldown < 0) flameCooldown = 0;
            }

            if (flameWarningTimer > 0) {
                flameWarningTimer -= dt;
                if (flameWarningTimer <= 0) {
                    flameWarningTimer = 0;
                    flameActive = true;
                    flameDuration = 120;
                }
            }

            if (flameActive) {
                flameDuration -= dt;
                if (flameDuration <= 0) {
                    flameActive = false;
                    flameCooldown = 180;
                }
            }

            if (flameActive && player != null && isPointInFlameRect(player.x, player.y)) {
                flameDamageTimer += dt;
                if (flameDamageTimer >= 30) {
                    flameDamageTimer = 0;
                    if (player.invincibleTimer <= 0) {
                        player.takeDamage(1);
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

    // ========== Boss：红色（传送 + 散射） ==========
    class BossTank extends EnemyTank {
        double scatterTimer = 180;
        double teleportCooldown = 240;
        double teleportTimer = 0;
        boolean isTeleporting = false;
        double tpTargetX, tpTargetY;

        public BossTank(double x, double y) {
            super(x, y, TANK_NORMAL);
            this.color = Color.RED;
            this.size = 50;
            this.hp = 5;
            this.maxHp = 5;
        }

        @Override
        public void update() {
            if (isTeleporting) {
                teleportTimer -= dt;
                if (teleportTimer <= 0) {
                    this.x = tpTargetX;
                    this.y = tpTargetY;
                    this.isTeleporting = false;
                    this.teleportCooldown = 240;
                }
                return;
            }

            super.update();
            scatterTimer -= dt;
            if (scatterTimer <= 0) {
                fireScatterBullets();
                scatterTimer = 180;
            }

            teleportCooldown -= dt;
            if (teleportCooldown <= 0) {
                startTeleport();
            }
        }

        private void startTeleport() {
            int attempts = 0;
            double rx = this.x, ry = this.y;
            while (attempts < 50) {
                rx = random.nextInt(WIDTH - 200) + 100;
                ry = random.nextInt(HEIGHT - 200) + 100;
                if (!isCollidingWithWalls(rx, ry, this.size)) {
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
                Bullet b = new Bullet(x, y, angle, true, TANK_SHOTGUN, true, 3);
                b.isEnemyBullet = true;
                b.life = 180;
                Launcher.this.bullets.add(b);
            }
        }
    }

    // ========== 游戏状态常量 ==========
    private static final int STATE_MENU = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_GAMEOVER = 2;
    private static final int STATE_HELP = 3;
    private static final int STATE_SELECT = 4;

    // 坦克类型
    private static final int TANK_NORMAL = 0;
    private static final int TANK_SHOTGUN = 1;
    private static final int TANK_ARTILLERY = 2;

    public static final int WIDTH = 1000;
    public static final int HEIGHT = 700;

    private int selectedTankType = TANK_NORMAL;
    private int score = 0;
    private int level = 1;
    private double showLevelTimer = 0;
    private boolean isCountingDown = false;
    private double countdownTimer = 0;
    private int gameState = STATE_MENU;

    private PlayerTank player;
    private ArrayList<EnemyTank> enemies = new ArrayList<>();
    private ArrayList<Bullet> bullets = new ArrayList<>();
    private ArrayList<Wall> walls = new ArrayList<>();
    private ArrayList<ShieldItem> shields = new ArrayList<>();
    private ArrayList<ExplosionEffect> explosions = new ArrayList<>();

    // ---------- Buff 系统 ----------
    private int selectedBuff = 0;   // 0=无, 1=神佑, 2=工业革命, 3=制空权

    // ---------- 神佑之地 ----------
    class SafeZone {
        double x, y, width, height;
        boolean active = false;
        double remaining = 0;
        public SafeZone(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }
        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
    private SafeZone safeZone = null;
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
    class PlayerArtilleryStrike {
        double targetX, targetY;
        double remainingFrames;
        double strikeRadius;
        public PlayerArtilleryStrike(double tx, double ty, int frames, double radius) {
            this.targetX = tx; this.targetY = ty;
            this.remainingFrames = frames;
            this.strikeRadius = radius;
        }
    }
    private ArrayList<PlayerArtilleryStrike> playerStrikes = new ArrayList<>();

    // ---------- Boss后备队列 ----------
    private ArrayList<EnemyTank> bossQueue = new ArrayList<>();

    // 输入
    private boolean[] keys = new boolean[256];
    private Point2D mousePoint = new Point2D(0, 0);
    private Random random = new Random();
    private long lastShieldSpawnTime = 0;
    private Canvas canvas;

    // ---------- 帧率无关的变步长循环：逐帧 delta，速度不随帧率变化，且支持高刷(165fps) ----------
    private long lastFrameNanos = 0;
    private double dt = 1.0;                                               // 帧增量，1.0 = 一个 60Hz 步
    private static final double SPEED_MULTIPLIER = 1.3;                    // 整体移动提速 30%

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
                if (gameState == STATE_PLAYING && !isCountingDown && airstrikeCooldown <= 0) {
                    warningX = e.getX();
                    warningY = e.getY();
                    warningTimer = WARNING_DURATION;
                    warningActive = true;
                    strikeActive = false;
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
        player = new PlayerTank(px, py, selectedTankType);
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

                    if (bossType == 0) boss = new BossTank(startX, startY);
                    else if (bossType == 1) boss = new PinkBoss(startX, startY);
                    else boss = new OrangeBoss(startX, startY);
                    placed = true;
                }

                if (!placed) {
                    // 保底：使用固定位置
                    double startX = (WIDTH / 4.0) + (i % 2) * (WIDTH / 2.0);
                    double startY = (HEIGHT / 4.0) + (i / 2) * (HEIGHT / 3.0);
                    if (bossType == 0) boss = new BossTank(startX, startY);
                    else if (bossType == 1) boss = new PinkBoss(startX, startY);
                    else boss = new OrangeBoss(startX, startY);
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
                EnemyTank enemy = new EnemyTank(ex, ey, eType);
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

    private int countWallsBetween(double x1, double y1, double x2, double y2) {
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
        if (showLevelTimer > 0) showLevelTimer -= dt;

        if (isCountingDown) {
            countdownTimer -= dt;
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
                explosions.add(new ExplosionEffect(strike.targetX, strike.targetY));
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
                    explosions.add(new ExplosionEffect(b.x, b.y));
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
                        if (enemy instanceof OrangeBoss && ((OrangeBoss) enemy).hasShield) {
                            ((OrangeBoss) enemy).hasShield = false;
                            ((OrangeBoss) enemy).shieldCooldown = 720;
                        } else {
                            enemy.hp -= b.damage;
                            if (enemy.hp <= 0) {
                                eIter.remove();
                                score++;
                                // 若阵亡的是Boss，尝试补充
                                replaceBossIfNeeded(enemy);
                            }
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

        // 爆炸特效
        Iterator<ExplosionEffect> expIter = explosions.iterator();
        while (expIter.hasNext()) {
            ExplosionEffect exp = expIter.next();
            exp.update();
            if (!exp.active) expIter.remove();
        }

        // 关卡结束条件：场上无敌人和后备队列无Boss
        if (enemies.isEmpty() && bossQueue.isEmpty() && showLevelTimer <= 0) {
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
                if (enemy instanceof OrangeBoss && ((OrangeBoss) enemy).hasShield) {
                    ((OrangeBoss) enemy).hasShield = false;
                    ((OrangeBoss) enemy).shieldCooldown = 720;
                } else {
                    enemy.hp -= enemyDamage;
                    if (enemy.hp <= 0) {
                        eIter.remove();
                        score++;
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
                if (enemy instanceof OrangeBoss && ((OrangeBoss) enemy).hasShield) {
                    ((OrangeBoss) enemy).hasShield = false;
                    ((OrangeBoss) enemy).shieldCooldown = 720;
                } else {
                    enemy.hp--;
                    if (enemy.hp <= 0) {
                        it.remove();
                        score++;
                        replaceBossIfNeeded(enemy);
                    }
                }
            }
        }
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
                    selectedTankType = i;
                }
            }
            if (mx >= WIDTH / 2 - 100 && mx <= WIDTH / 2 + 100 && my >= 460 && my <= 520) {
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
                    selectedBuff = i + 1;
                    gameState = STATE_PLAYING;
                    startNewLevel(true);
                    break;
                }
            }
        } else if (gameState == STATE_PLAYING) {
            if (!isCountingDown) {
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

    // ---------- 绘制游戏 ----------
    private void drawGame(GraphicsContext gc) {
        gc.setFill(Color.rgb(55, 55, 55));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.getCanvas().setFocusTraversable(true);
        gc.setFill(Color.GRAY);
        for (Wall w : walls) {
            gc.fillRect(w.x, w.y, w.width, w.height);
        }

        for (ShieldItem s : shields) {
            gc.setFill(Color.GREEN);
            gc.fillOval(s.x - 10, s.y - 10, 20, 20);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1);
            gc.strokeOval(s.x - 12, s.y - 12, 24, 24);
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

        // 玩家坦克
        gc.save();
        gc.translate(player.x, player.y);
        gc.rotate(Math.toDegrees(player.angle));
        gc.setFill(player.color);
        gc.fillRect(-20, -15, 40, 30);
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(-5, -10, 18, 20);
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(5, -4, 25, 8);
        gc.restore();

        if (player.invincibleTimer > 0) {
            gc.setStroke(Color.rgb(255, 215, 0, 0.75));
            gc.setLineWidth(4);
            gc.strokeOval(player.x - 30, player.y - 30, 60, 60);
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

            gc.save();
            gc.translate(enemy.x, enemy.y);
            gc.rotate(Math.toDegrees(enemy.angle));
            gc.setFill(enemy.color);
            if (enemy instanceof BossTank || enemy instanceof PinkBoss || enemy instanceof OrangeBoss) {
                gc.fillRect(-25, -20, 50, 40);
                gc.setFill(Color.LIGHTGRAY);
                gc.fillRect(-8, -15, 22, 30);
                gc.setFill(Color.BLACK);
                gc.fillRect(8, -6, 32, 12);
            } else {
                gc.fillRect(-20, -15, 40, 30);
                gc.setFill(Color.LIGHTGRAY);
                gc.fillRect(-5, -10, 18, 20);
                gc.setFill(Color.DARKGRAY);
                gc.fillRect(5, -4, 25, 8);
            }
            gc.restore();

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
            }
        }

        // 子弹
        for (Bullet b : bullets) {
            gc.setFill(b.color);
            gc.fillOval(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
        }

        // 爆炸特效
        for (ExplosionEffect exp : explosions) {
            gc.setFill(Color.rgb(0, 150, 255, 0.3));
            gc.fillOval(exp.x - 80, exp.y - 80, 160, 160);
            gc.setStroke(Color.rgb(0, 100, 255, 0.8));
            gc.setLineWidth(2);
            gc.strokeOval(exp.x - 80, exp.y - 80, 160, 160);
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

    // ==================== 实体类 ====================

    class Tank {
        double x, y;
        double angle;
        int type;
        Color color;
        int size = 34;

        double cooldown = 0;
        int burstCount = 0;
        double burstCoolDown = 0;

        boolean isCharging = false;
        double chargeTimer = 0;
        double chargeTargetX, chargeTargetY;

        public Tank(double x, double y, int type) {
            this.x = x;
            this.y = y;
            this.type = type;
            if (type == TANK_NORMAL) this.color = Color.GREEN;
            else if (type == TANK_SHOTGUN) this.color = Color.YELLOW;
            else this.color = Color.BLUE;
        }

        public void update() {
            if (cooldown > 0) cooldown -= dt;
            if (burstCoolDown > 0) burstCoolDown -= dt;
            if (isCharging) {
                chargeTimer -= dt;
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
        }
    }

    class PlayerTank extends Tank {
        double invincibleTimer = 0;
        int health = 3;

        public PlayerTank(double x, double y, int type) {
            super(x, y, type);
        }

        @Override
        public void update() {
            super.update();
            if (invincibleTimer > 0) {
                invincibleTimer -= dt;
            }
            if (health <= 0) {
                gameState = STATE_GAMEOVER;
            }
        }

        public void takeDamage(int amount) {
            if (invincibleTimer > 0) return;
            if (selectedBuff == 1 && safeZone != null && safeZone.active && safeZone.contains(this.x, this.y)) {
                return;
            }
            health -= amount;
            if (health < 0) health = 0;
            if (health == 0) {
                gameState = STATE_GAMEOVER;
            } else {
                invincibleTimer = 90; // 受击后短暂金身，避免连续判定（Lidada）
            }
        }

        public void shoot(ArrayList<Bullet> bList, Point2D target) {
            if (isCharging) return;

            int bulletDamage = (selectedBuff == 2) ? 2 : 1;

            if (type == TANK_NORMAL) {
                if (burstCoolDown > 0) return;
                if (cooldown <= 0) {
                    Bullet b = new Bullet(x, y, angle, true, TANK_NORMAL, true, 6);
                    b.isEnemyBullet = false;
                    b.color = Color.RED;
                    b.damage = bulletDamage;
                    bList.add(b);
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
                        b.damage = bulletDamage;
                        bList.add(b);
                    }
                    cooldown = 60;
                }
            } else if (type == TANK_ARTILLERY) {
                if (cooldown <= 0) {
                    double targetX = target.getX();
                    double targetY = target.getY();

                    int wallCount = countWallsBetween(this.x, this.y, targetX, targetY);
                    int totalFrames = 40 + (wallCount * 24);
                    if (totalFrames > 210) totalFrames = 210;

                    double addedRadius = wallCount * 10;
                    if (addedRadius > 40) addedRadius = 40;
                    double finalRadius = 80 + addedRadius;

                    Launcher.this.playerStrikes.add(new PlayerArtilleryStrike(targetX, targetY, totalFrames, finalRadius));
                    cooldown = 120;
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
        double life = 300;
        boolean isActive = true;
        int speed;
        boolean isHarmful;
        int damage = 1;

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

            x += actualVx * dt;
            y += actualVy * dt;
            life -= dt;
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
                        break;
                    } else {
                        isActive = false;
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
        double timer = 90;
        boolean active = true;

        public ExplosionEffect(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void update() {
            timer -= dt;
            if (timer <= 0) active = false;
        }
    }
}