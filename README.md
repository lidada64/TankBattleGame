# 坦克大战（JavaFX）

一个基于 JavaFX 的坦克对战游戏：三种坦克形态、随机关卡、敌人 AI、Boss 战、空中支援技能，
含完整的音效/背景乐与打击动画（爆炸、粒子、屏震、后坐、受击反馈等）。

## 运行要求
- **JDK 17+**（开发用 JDK 21）
- **JavaFX 17+**，且**必须包含 `javafx.media` 模块**（背景乐/音效依赖它）

> ⚠️ 最常见的"没有声音"原因只有两个：
> 1. 运行参数 `--add-modules` 漏了 **`javafx.media`**（只写了 `javafx.controls`）；
> 2. `assets/sound/` 目录没有随项目一起存在。
>
> 代码做了容错：即使没有 `javafx.media` 或找不到素材，游戏也**不会崩溃**，只是静音，
> 并在控制台打印一行提示。所以声音问题永远只在"运行配置"，不在游戏本身。

## 如何运行

### 方式 A：命令行（macOS，已附脚本）
```bash
cd TankBattleGame-master
./run.sh
```
`run.sh` 会自动用本机 Maven 缓存里的 JavaFX 编译并运行。

### 方式 B：IntelliJ IDEA
1. 把 JavaFX SDK 加为库，或用 Maven/Gradle 引入 `org.openjfx`。
2. 运行配置 → **VM options**：
   ```
   --module-path /你的/javafx/lib --add-modules javafx.controls,javafx.media
   ```
3. 运行配置 → **Working directory** 设为项目根（含 `assets/` 的那一层），确保能找到音频。
4. 主类：`Launcher`

### 方式 C：纯命令行（通用）
```bash
javac --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.media \
  -d out Launcher.java SoundManager.java
java  --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.media \
  -cp out Launcher
```

## 操作
| 按键 | 功能 |
|---|---|
| W A S D | 移动 |
| 鼠标 | 瞄准 |
| 鼠标左键 | 射击 |
| 1 | 呼叫空中支援 / 再按提前引爆 |
| M | 全局静音切换 |

## 目录结构
```
TankBattleGame-master/
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
