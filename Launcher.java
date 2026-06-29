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
        }

        @Override
        public void move(double dx, double dy, ArrayList<Wall> walls) {
            if (isCharging) return;
            double targetNextX = x + dx;
            double targetNextY = y + dy;

            // 防卡滑动检测：如果X或Y方向单向被阻挡，允许顺着墙面滑动通过
            boolean hitWallX = isCollidingWithWalls(targetNextX, y, size);
            boolean hitWallY = isCollidingWithWalls(x, targetNextY, size);
            boolean hitEnemyX = isCollidingWithOtherEnemies(this, targetNextX, y);
            boolean hitEnemyY = isCollidingWithOtherEnemies(this, x, targetNextY);

            if (!hitWallX && !hitEnemyX) {
                x = targetNextX;
            } else if (hitWallX && !hitEnemyX) {
                double slideY = (dy != 0) ? dy : (random.nextBoolean() ? 1.5 : -1.5);
                if (!isCollidingWithWalls(x, y + slideY, size)) y += slideY;
            }

            if (!hitWallY && !hitEnemyY) {
                y = targetNextY;
            } else if (hitWallY && !hitEnemyY) {
                double slideX = (dx != 0) ? dx : (random.nextBoolean() ? 1.5 : -1.5);
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
            // 如果是Boss且正在传送引导中，暂停AI常规寻路与射击
            if (this instanceof BossTank && ((BossTank) this).isTeleporting) {
                return;
            }

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

            double baseMoveSpeed = (aiState == 0) ? 1.0 : 1.6;

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

    // Boss 机理
    class BossTank extends EnemyTank {
        int hp = 5;
        int scatterTimer = 180;

        // Boss传送所需核心属性
        int teleportCooldown = 240; // 4秒触发一次 (4 * 60 = 240帧)
        int teleportTimer = 0;      // 2秒传送引导倒计时 (2 * 60 = 120帧)
        boolean isTeleporting = false;
        double tpTargetX, tpTargetY;

        public BossTank(double x, double y) {
            super(x, y, TANK_NORMAL, Color.RED);
            this.size = 50;
        }

        @Override
        public void update() {
            if (isTeleporting) {
                teleportTimer--;
                if (teleportTimer <= 0) {
                    // 引导结束，瞬间转移，彻底现身
                    this.x = tpTargetX;
                    this.y = tpTargetY;
                    this.isTeleporting = false;
                    this.teleportCooldown = 240;
                }
                return; // 传送期间处于虚无状态
            }

            super.update();
            scatterTimer--;
            if (scatterTimer <= 0) {
                fireScatterBullets();
                scatterTimer = 180;
            }

            // 处理传送CD计数
            teleportCooldown--;
            if (teleportCooldown <= 0) {
                startTeleport();
            }
        }

        private void startTeleport() {
            // 随机寻找不会卡在墙体里的安全落点
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
            this.teleportTimer = 120; // 2秒引导时间
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

    // 游戏状态
    private static final int STATE_MENU = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_GAMEOVER = 2;
    private static final int STATE_HELP = 3;
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

    // 开局倒计时变量
    private boolean isCountingDown = false;
    private int countdownTimer = 0;

    private PlayerTank player;
    private ArrayList<EnemyTank> enemies = new ArrayList<>();
    private ArrayList<Bullet> bullets = new ArrayList<>();
    private ArrayList<Wall> walls = new ArrayList<>();
    private ArrayList<ShieldItem> shields = new ArrayList<>();
    private ArrayList<ExplosionEffect> explosions = new ArrayList<>();

    // 玩家火炮落弹任务队列
    class PlayerArtilleryStrike {
        double targetX, targetY;
        int remainingFrames;
        double strikeRadius;

        public PlayerArtilleryStrike(double tx, double ty, int frames, double radius) {
            this.targetX = tx;
            this.targetY = ty;
            this.remainingFrames = frames;
            this.strikeRadius = radius;
        }
    }
    private ArrayList<PlayerArtilleryStrike> playerStrikes = new ArrayList<>();

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
        if (gameState != STATE_PLAYING || isCountingDown) return;

        if (!airstrikes.isEmpty()) {
            for (Airstrike air : airstrikes) {
                air.isExploded = true;
            }
            return;
        }

        if (airstrikeCooldownTimer <= 0) {
            airstrikeCooldownTimer = AIRSTRIKE_CD_MAX;
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

        isCountingDown = true;
        countdownTimer = 210;

        for (int i = 0; i < keys.length; i++) keys[i] = false;

        bullets.clear();
        enemies.clear();
        shields.clear();
        explosions.clear();
        airstrikes.clear();
        playerStrikes.clear();

        generateRandomWalls();

        int px = 100, py = HEIGHT / 2;
        while (isCollidingWithWalls(px, py, 34)) {
            px += 15;
            if (px > WIDTH - 100) { px = 100; py += 15; }
        }
        player = new PlayerTank(px, py, selectedTankType);
        player.invincibleTimer = 300;

        // 修改后的 Boss 关卡逻辑
        if (level % 4 == 0) {
            // 计算当前关卡需要多少个 Boss
            int bossCount = 1 + ((level / 4) - 1);

            for (int i = 0; i < bossCount; i++) {
                // 为了避免 Boss 重叠，分散它们的生成位置
                double startX = (WIDTH / 4.0) + (i % 2) * (WIDTH / 2.0);
                double startY = (HEIGHT / 4.0) + (i / 2) * (HEIGHT / 3.0);

                BossTank boss = new BossTank(startX, startY);
                // 确保 Boss 不卡墙
                while (isCollidingWithWalls(boss.x, boss.y, boss.size)) {
                    boss.x += 20;
                    boss.y += 20;
                }
                enemies.add(boss);
            }
        } else {
            // 原有普通关卡逻辑
            int enemyCount = 0;
            if (level == 1) enemyCount = 1;
            else if (level == 2) enemyCount = 2;
            else if (level == 3) enemyCount = 3;
            else if (level == 5) enemyCount = 5;
            else enemyCount = 6;

            for (int i = 0; i < enemyCount; i++) {
                int ex, ey;
                do {
                    ex = random.nextInt(WIDTH - 250) + 150;
                    ey = random.nextInt(HEIGHT - 200) + 50;
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

        // 大幅暴增随机墙体数量
        int wallCount = random.nextInt(14) + 35;
        for (int i = 0; i < wallCount; i++) {
            int wx = random.nextInt(WIDTH - 300) + 150;
            int wy = random.nextInt(HEIGHT - 250) + 80;

            // 缩小横纵截面厚度为 18 像素
            int w = random.nextBoolean() ? 75 : 18;
            int h = (w == 18) ? 75 : 18;

            // 安全区半径保护：保障玩家出生位置和地图中心枢纽不被强行封堵
            if (getDistance(wx + w/2.0, wy + h/2.0, 100, HEIGHT/2.0) < 95 ||
                    getDistance(wx + w/2.0, wy + h/2.0, WIDTH/2.0, HEIGHT/2.0) < 95) {
                continue;
            }

            Wall newWall = new Wall(wx, wy, w, h);
            boolean overlap = false;
            // 适当缩减墙体间距阈值为 45 像素，生成紧凑密集的战术车道
            for(Wall existing : walls) {
                if (existing.intersects(wx - 45, wy - 45, w + 90, h + 90)) {
                    overlap = true;
                    break;
                }
            }
            if(!overlap) {
                walls.add(newWall);
            }
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

    // 判断射线/线段是否穿过某堵墙，实现掩体动态延时计算
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

    private void updateGame() {
        if (showLevelTimer > 0) showLevelTimer--;

        if (isCountingDown) {
            countdownTimer--;
            if (countdownTimer <= 0) {
                isCountingDown = false;
            }
            return;
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
                explosions.add(new ExplosionEffect(air.x, air.y));
                airIter.remove();
            }
        }

        // 更新玩家火炮定点打击倒计时队列
        Iterator<PlayerArtilleryStrike> strikeIter = playerStrikes.iterator();
        while (strikeIter.hasNext()) {
            PlayerArtilleryStrike strike = strikeIter.next();
            strike.remainingFrames--;
            if (strike.remainingFrames <= 0) {
                explosions.add(new ExplosionEffect(strike.targetX, strike.targetY));
                triggerDynamicExplosionDamage(strike.targetX, strike.targetY, strike.strikeRadius);
                strikeIter.remove();
            }
        }

        double dx = 0, dy = 0;
        if (keys[87]) dy -= 3;
        if (keys[83]) dy += 3;
        if (keys[65]) dx -= 3;
        if (keys[68]) dx += 3;
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
                    explosions.add(new ExplosionEffect(b.x, b.y));
                    triggerExplosionDamage(b.x, b.y);
                }
                bIter.remove();
                continue;
            }

            if (b.isEnemyBullet && getDistance(b.x, b.y, player.x, player.y) < 20) {
                if (player.invincibleTimer <= 0) {
                    gameState = STATE_GAMEOVER;
                }
                bIter.remove();
                continue;
            }

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

                        if (enemy instanceof BossTank) {
                            ((BossTank) enemy).hp--;
                            if (((BossTank) enemy).hp <= 0) {
                                eIter.remove();
                                score++;
                            }
                        } else {
                            eIter.remove();
                            score++;
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
            level++;
            startNewLevel(false);
        }
    }

    private void triggerExplosionDamage(double ex, double ey) {
        triggerDynamicExplosionDamage(ex, ey, 80);
    }

    private void triggerDynamicExplosionDamage(double ex, double ey, double radius) {
        if (getDistance(ex, ey, player.x, player.y) < radius) {
            if (player.invincibleTimer <= 0) {
                gameState = STATE_GAMEOVER;
            }
        }

        Iterator<EnemyTank> eIter = enemies.iterator();
        while (eIter.hasNext()) {
            EnemyTank enemy = eIter.next();

            if (enemy instanceof BossTank && ((BossTank) enemy).isTeleporting) {
                continue;
            }

            if (getDistance(ex, ey, enemy.x, enemy.y) < radius) {
                if (enemy instanceof BossTank) {
                    ((BossTank) enemy).hp--;
                    if (((BossTank) enemy).hp <= 0) {
                        eIter.remove();
                        score++;
                    }
                } else {
                    eIter.remove();
                    score++;
                }
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

            if (enemy instanceof BossTank && ((BossTank) enemy).isTeleporting) {
                continue;
            }

            if (getDistance(ax, ay, enemy.x, enemy.y) < radius) {
                if (enemy instanceof BossTank) {
                    ((BossTank) enemy).hp--;
                    if (((BossTank) enemy).hp <= 0) {
                        eIter.remove();
                        score++;
                    }
                } else {
                    eIter.remove();
                    score++;
                }
            }
        }
    }

    private void handleMousePressed(double mx, double my) {
        if (gameState == STATE_MENU) {
            // Click help button
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
                gameState = STATE_PLAYING;
                startNewLevel(true);
            }
        } else if (gameState == STATE_PLAYING) {
            if (!isCountingDown) {
                player.shoot(bullets, mousePoint);
            }
        } else if (gameState == STATE_HELP) {

            if (mx >= WIDTH / 2.0 - 100 &&
                    mx <= WIDTH / 2.0 + 100 &&
                    my >= 620 &&
                    my <= 670) {

                gameState = STATE_MENU;
            }

        } else if (gameState == STATE_GAMEOVER) {
            if (mx >= WIDTH / 2 - 40 && mx <= WIDTH / 2 + 40 && my >= HEIGHT / 2 + 20 && my <= HEIGHT / 2 + 120) {
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
        }else if (gameState == STATE_HELP) {
            drawHelp(gc);
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
        // Help button
        gc.setFill(Color.DARKBLUE);
        gc.fillOval(WIDTH - 70, 20, 40, 40);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(WIDTH - 70, 20, 40, 40);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        gc.fillText("?", WIDTH - 56, 24);
    }

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
        gc.fillText("Key 1: Call air support / detonate airstrike", 180, 390);

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
            gc.strokeOval(strike.targetX - strike.strikeRadius, strike.targetY - strike.strikeRadius, strike.strikeRadius * 2, strike.strikeRadius * 2);
            gc.setLineDashes(null);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Impact", FontWeight.NORMAL, 14));
            gc.fillText(String.format("%.1fs", strike.remainingFrames / 60.0), strike.targetX - 10, strike.targetY - 25);
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

        // 绘制玩家
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
            if (enemy instanceof BossTank) {
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

        for (Bullet b : bullets) {
            gc.setFill(b.color);
            gc.fillOval(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
        }

        for (Airstrike air : airstrikes) {
            if (air.timer > 120) {
                gc.setStroke(Color.YELLOW);
                gc.setFill(Color.rgb(255, 255, 0, 0.15));
            } else {
                gc.setStroke(Color.RED);
                gc.setFill(Color.rgb(255, 0, 0, 0.25));
            }
            gc.setLineWidth(3);
            gc.fillOval(air.x - air.radius, air.y - air.radius, air.radius * 2, air.radius * 2);
            gc.strokeOval(air.x - air.radius, air.y - air.radius, air.radius * 2, air.radius * 2);
        }

        for (ExplosionEffect exp : explosions) {
            gc.setFill(Color.rgb(0, 150, 255, 0.3));
            gc.fillOval(exp.x - 80, exp.y - 80, 160, 160);
            gc.setStroke(Color.rgb(0, 100, 255, 0.8));
            gc.setLineWidth(2);
            gc.strokeOval(exp.x - 80, exp.y - 80, 160, 160);
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

        if (showLevelTimer > 0) {
            gc.setFill(Color.rgb(255, 215, 0, 0.45));
            gc.setFont(Font.font("Comic Sans MS", FontWeight.BOLD, 40));
            gc.fillText("STAGE " + level, WIDTH / 2.0 - 90, HEIGHT / 2.0 - 150);
        }

        if (isCountingDown) {
            gc.save();
            gc.setTextBaseline(VPos.CENTER);
            if (countdownTimer > 30) {
                int secondsLeft = (countdownTimer - 31) / 60 + 1;
                int frameInSecond = (countdownTimer - 31) % 60;
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

    // 实体内部类类群

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
                if (burstCoolDown > 0) return;
                if (cooldown <= 0) {
                    Bullet b = new Bullet(x, y, angle, true, TANK_NORMAL, true, 6);
                    b.isEnemyBullet = false;
                    b.color = Color.RED;
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
                        bList.add(b);
                    }
                    cooldown = 60;
                }
            } else if (type == TANK_ARTILLERY) {
                if (cooldown <= 0) {
                    double targetX = target.getX();
                    double targetY = target.getY();

                    int wallCount = countWallsBetween(this.x, this.y, targetX, targetY);

                    // 将最快无阻挡攻击速度调整为 0.66秒（40帧)。每多一堵墙增加 0.4 秒 (24帧)
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
}