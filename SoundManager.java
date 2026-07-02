import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一管理游戏所有音频。
 * - 短音效（射击/爆炸/命中等）用 {@link AudioClip}，可低延迟重叠播放。
 * - 背景音乐与 8 秒 Boss 登场曲用 {@link MediaPlayer}，可循环、可调音量。
 *
 * 设计为"容错优先"：若 assets 缺失、或运行时没有 javafx.media 模块，
 * 会自动把声音整体禁用，游戏照常运行而不会崩溃。
 */
public class SoundManager {

    private boolean enabled = true;
    private boolean muted = false;
    private String base = "assets/sound/";

    private double sfxVolume = 0.85;
    private double bgmVolume = 0.45;

    private final Map<String, AudioClip> clips = new HashMap<>();
    private MediaPlayer bgmPlayer;       // 当前循环背景乐
    private String bgmName = null;
    private MediaPlayer warningPlayer;   // Boss 登场曲（一次性，播放时压低 BGM）

    // 所有短音效文件名（不含扩展名）
    private static final String[] SFX = {
        "shoot_normal", "shoot_shotgun", "charge", "artillery_fire",
        "explosion", "hit_enemy", "boss_hit", "boss_fire",
        "pickup", "airstrike_call", "airstrike_incoming",
        "levelup", "gameover", "countdown_beep", "go", "button",
        "ricochet", "empty_click"
    };

    public SoundManager() {
        try {
            base = resolveBase();
            for (String name : SFX) {
                // 音效优先用 WAV（PCM 立体声，AudioClip 最稳），找不到再退回 MP3
                File f = new File(base + name + ".wav");
                if (!f.exists()) f = new File(base + name + ".mp3");
                if (f.exists()) {
                    AudioClip c = new AudioClip(f.toURI().toString());
                    clips.put(name, c);
                }
            }
            if (clips.isEmpty()) {
                System.err.println("[SoundManager] 警告：未找到任何音效。请确认 assets/sound 目录"
                        + "随项目一起存在（当前查找基准: " + new File(base).getAbsolutePath() + "）。");
            } else {
                System.out.println("[SoundManager] 音频就绪：已加载 " + clips.size() + "/" + SFX.length
                        + " 个音效，目录 " + new File(base).getAbsolutePath());
            }
        } catch (Throwable t) {
            enabled = false;
            System.err.println("[SoundManager] 音频已禁用（缺少 javafx.media 或素材）: " + t);
        }
    }

    // 多策略定位 assets/sound：既试常见工作目录，也按 class/jar 所在位置逐级向上找，
    // 这样无论从哪个目录、用 IDE 还是命令行启动，都能找到素材。
    private String resolveBase() {
        List<String> cands = new ArrayList<>();
        cands.add("assets/sound/");
        cands.add("TankBattleGame-master/assets/sound/");
        cands.add("../assets/sound/");
        cands.add("../TankBattleGame-master/assets/sound/");

        // 相对于编译产物/jar 的位置（与运行时工作目录无关，最稳）
        try {
            File loc = new File(SoundManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = loc.isFile() ? loc.getParentFile() : loc;   // jar→所在目录；目录→自身
            for (File d = dir; d != null; d = d.getParentFile()) {
                cands.add(new File(d, "assets/sound/").getPath() + File.separator);
            }
        } catch (Throwable ignore) { /* 取不到就用上面的相对路径 */ }

        for (String c : cands) {
            if (new File(c + "bgm_menu.mp3").exists()) return c;
        }
        return "assets/sound/";
    }

    // ---------------- 短音效 ----------------

    public void play(String name) { play(name, sfxVolume); }

    public void play(String name, double volume) {
        if (!enabled || muted) return;
        AudioClip c = clips.get(name);
        if (c != null) c.play(volume);
    }

    // ---------------- 背景音乐 ----------------

    public void playBgm(String name) {
        if (!enabled) return;
        if (name.equals(bgmName) && bgmPlayer != null) return; // 已在播放同一首
        stopBgm();
        try {
            File f = new File(base + name + ".mp3");
            if (!f.exists()) return;
            bgmPlayer = new MediaPlayer(new Media(f.toURI().toString()));
            bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            bgmPlayer.setVolume(muted ? 0 : bgmVolume);
            bgmPlayer.setOnError(() -> System.err.println("[SoundManager] BGM 播放错误: " + bgmPlayer.getError()));
            bgmPlayer.play();
            bgmName = name;
        } catch (Throwable t) {
            System.err.println("[SoundManager] BGM 播放失败: " + t);
        }
    }

    public void stopBgm() {
        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer.dispose();
            bgmPlayer = null;
            bgmName = null;
        }
    }

    /** Boss 登场：播放 8 秒登场曲，期间把战斗 BGM 压低，播完恢复。 */
    public void playBossWarning() {
        if (!enabled || muted) { return; }
        try {
            File f = new File(base + "boss_warning.mp3");
            if (!f.exists()) return;
            if (bgmPlayer != null) bgmPlayer.setVolume(bgmVolume * 0.18);
            if (warningPlayer != null) { warningPlayer.stop(); warningPlayer.dispose(); }
            warningPlayer = new MediaPlayer(new Media(f.toURI().toString()));
            warningPlayer.setVolume(Math.min(1.0, bgmVolume + 0.4));
            warningPlayer.setOnEndOfMedia(() -> {
                if (bgmPlayer != null && !muted) bgmPlayer.setVolume(bgmVolume);
                warningPlayer.dispose();
                warningPlayer = null;
            });
            warningPlayer.play();
        } catch (Throwable t) {
            System.err.println("[SoundManager] Boss 登场曲播放失败: " + t);
        }
    }

    // ---------------- 控制 ----------------

    public void toggleMute() {
        muted = !muted;
        if (bgmPlayer != null) bgmPlayer.setVolume(muted ? 0 : bgmVolume);
        if (warningPlayer != null) warningPlayer.setVolume(muted ? 0 : Math.min(1.0, bgmVolume + 0.4));
    }

    public boolean isMuted() { return muted; }
}
