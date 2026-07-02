# 坦克大战（JavaFX）

一个基于 JavaFX 的坦克对战游戏：三种坦克形态、战略卡牌 Buff、随机关卡与墙体、敌人 AI、
多种 Boss（红/粉/橙，含传送、弹幕、护盾+火焰）、空袭技能与神佑之地，
并带有完整的**音效/背景乐**与**战斗动画**（爆炸、粒子、屏震、炮口闪光、后坐、子弹拖尾、
受击白闪、飘分、命中顿帧、玩家死亡红屏、Boss 登场/狂暴/连环爆、履带滚动与行进扬尘等）。

> 游戏是**帧率无关**的：逻辑按每帧 `dt` 推进，速度不随帧率变化，并支持高刷新率（如 165Hz）。
> 所有动画同样按 `dt` 缩放，高刷下更平滑而不会加速。

## 运行要求
- **JDK 17+**（开发用 JDK 21/26 均可）
- **JavaFX 17+**，且**必须包含 `javafx.media` 模块**（背景乐/音效依赖它）

> ⚠️ "没有声音"通常只有两个原因：
> 1. 运行参数 `--add-modules` 漏了 **`javafx.media`**（只写了 `javafx.controls`）；
> 2. `assets/sound/` 目录没有随项目一起存在。
>
> 代码做了容错：即使缺少 `javafx.media` 或找不到素材，游戏也**不会崩溃**，只是静音，
> 并在控制台打印一行提示。所以声音问题永远只在"运行配置"，不在游戏本身。

## 如何运行

### 方式 A：命令行（通用，推荐）
```bash
javac --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.media \
  -d out Launcher.java SoundManager.java
java  --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.media \
  -cp out Launcher
```
在项目根目录（含 `assets/` 的那一层）运行，确保能找到音频素材。

### 方式 B：`run.sh`（macOS，一键）
```bash
./run.sh
```
`run.sh` 会用本机 Maven 缓存里的 JavaFX 自动编译并运行（仅适配 macOS，其它平台请用方式 A/C）。

### 方式 C：IntelliJ IDEA / VS Code
1. 把 JavaFX SDK 加为库，或用 Maven/Gradle 引入 `org.openjfx`。
2. VM options：
   ```
   --module-path /你的/javafx/lib --add-modules javafx.controls,javafx.media
   ```
3. Working directory 设为项目根（含 `assets/` 的那一层）。
4. 主类：`Launcher`

## 操作
| 按键 | 功能 |
|---|---|
| W A S D | 移动 |
| 鼠标 | 瞄准 |
| 鼠标左键 | 射击（火炮为延时打击） |
| 鼠标右键 | 呼叫空袭（预警后落地，带冷却） |
| M | 全局静音切换 |

进入游戏前先在菜单选择坦克形态，再选择一张战略卡牌（神佑 / 工业革命 / 制空权）。

## 目录结构
```
TankBattleGame/
├── Launcher.java        # 主程序（游戏逻辑 + 渲染 + 动画）
├── SoundManager.java    # 音频管理（容错加载，找不到素材自动静音）
├── assets/sound/        # 音效(.wav) 与背景乐(.mp3)，必须随项目提交
├── run.sh               # macOS 一键编译运行
└── README.md
```

## 音频素材说明
`assets/sound/` 内的音频为**程序合成的免版权资源**（本项目自带，可自由使用/替换）。
音效用 WAV（立体声 PCM，低延迟），背景乐用 MP3（立体声，体积小）。
详见 `assets/sound/README.md`。
