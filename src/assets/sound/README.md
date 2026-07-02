# 音频素材清单（坦克大战）

这套音频是**程序合成的免版权占位音**（MP3 格式，由 44100Hz PCM 用 LAME VBR ~q4 编码），
可直接被 JavaFX 的 `AudioClip`（音效）和 `MediaPlayer`（BGM）播放。
觉得哪个不够"高级"，去下面推荐站点下个同名文件**覆盖**即可，代码不用改。

> 注：短音效用 MP3 在快速连发时可能有极轻微的解码延迟。本套已属可用；
> 若日后觉得射击声"跟不上手"，把对应音效单独换回 WAV 即可（其余保持 MP3）。

> ⚠️ 用 `MediaPlayer` 放 BGM 需要 JavaFX `javafx.media` 模块。运行参数里的
> `--add-modules` 要包含 `javafx.media`（不只是 `javafx.controls`），否则会报错。

---

## 一、音效 SFX（用 AudioClip 播放，短、可重叠）

| 文件 | 用途 | 建议接入位置（Launcher.java 行号） |
|---|---|---|
| `shoot_normal.mp3` | 普通坦克射击 | `PlayerTank.shoot()` 普通分支 ~L911 |
| `shoot_shotgun.mp3` | 散弹坦克射击 | `PlayerTank.shoot()` 散弹分支 ~L926 |
| `charge.mp3` | 火炮蓄力（1.4s，配合 90 帧蓄力） | `isCharging=true` 处 ~L936 |
| `artillery_fire.mp3` | 火炮蓄力完成发射 | `fireArtilleryBullet()` ~L881 |
| `explosion.mp3` | 火炮/空袭范围爆炸 | `triggerExplosionDamage()` L521、`triggerAirstrikeDamage()` L539 |
| `hit_enemy.mp3` | 击毁普通敌人 | 子弹命中 `score++` ~L491 |
| `boss_hit.mp3` | Boss 被打中（扣血未死） | `BossTank.hp--` ~L484 |
| `pickup.mp3` | 拾取无敌护盾 | `invincibleTimer=300` ~L444 |
| `airstrike_call.mp3` | 呼叫/引爆空中支援 | `triggerOrExplodeAirstrike()` ~L274 |
| `levelup.mp3` | 过关进入下一关 | `level++` ~L516 |
| `gameover.mp3` | 游戏失败 | 设 `STATE_GAMEOVER` 的 3 处 |
| `countdown_beep.mp3` | 开局倒计时 3-2-1 每秒一声 | `isCountingDown` 段 ~L756 |
| `go.mp3` | 倒计时结束 "GO!" | 倒计时 `countdownTimer<=30` 时 |
| `button.mp3` | 菜单按钮点击 | `handleMousePressed` 菜单分支 ~L557 |
| `boss_fire.mp3` | **Boss 八方向齐射** | `BossTank.fireScatterBullets()` ~L157 |
| `ricochet.mp3` | **子弹反弹墙壁** | `Bullet.move()` 反弹分支 ~L987（`canBounce`） |
| `boss_warning.mp3` | **Boss 登场预警** | `startNewLevel()` 出 Boss 时 ~L320（`level%4==0`） |
| `airstrike_incoming.mp3` | **空袭来袭呼啸** | 召唤空袭后落地前播放 ~L286（建 Airstrike 时） |
| `empty_click.mp3` | **连发冷却空射** | `shoot()` 普通分支 `burstCoolDown>0` return 处 ~L909 |

### 复用映射（无需新文件，SoundManager 里直接调用已有音效）

| 缺口环节 | 复用文件 | 接入位置 |
|---|---|---|
| 敌人开火（普通/散弹/火炮） | `shoot_normal` / `shoot_shotgun` / `artillery_fire`（音量调低些） | `EnemyTank.enemyFire()` ~L109 |
| 玩家被击毁的冲击声 | `explosion`（死亡瞬间先炸一声，再接 `gameover`） | 设 `STATE_GAMEOVER` 的 3 处 |

## 二、背景音乐 BGM（用 MediaPlayer 循环播放）

| 文件 | 用途 | 建议接入位置 |
|---|---|---|
| `bgm_menu.mp3` | 主菜单循环背景乐（10s 循环） | 进入 `STATE_MENU` |
| `bgm_battle.mp3` | 战斗循环背景乐（~7s 循环） | `startNewLevel()` / 进入 `STATE_PLAYING` |

---

## 三、想换"更高级"的素材，去这些免版权站（注意授权）

- **Kenney** https://kenney.nl/assets?q=audio — CC0，无需署名，最适合做游戏音效
- **Pixabay** https://pixabay.com/sound-effects/ 和 /music/ — 免费可商用
- **Mixkit** https://mixkit.co/free-sound-effects/ — 免费可商用
- **freesound.org** — 多为 CC0/CC-BY，下载需登录，CC-BY 记得在报告里署名

> 替换规则：下载后**重命名成上表里完全相同的文件名**，放进本文件夹覆盖即可。
> WAV 最稳；若用 MP3，JavaFX 也支持，但记得把代码里加载的文件名后缀一并改掉。

## 四、最小可交付（时间紧就先接这 7 个）
`bgm_menu` + `bgm_battle` + `shoot_normal` + `shoot_shotgun` + `explosion` + `pickup` + `gameover`
