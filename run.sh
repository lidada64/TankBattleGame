#!/bin/bash
# 一键编译并运行坦克大战（使用本机 Maven 缓存里的 JavaFX 21.0.5）
set -e
cd "$(dirname "$0")"

JAVA="${JAVA:-$HOME/.jdks/current/bin/java}"
JAVAC="${JAVAC:-$HOME/.jdks/current/bin/javac}"
M="$HOME/.m2/repository/org/openjfx"

# 收集 JavaFX 模块（mac-aarch64）
MP="$(pwd)/.fxmods"
mkdir -p "$MP"
for m in base graphics controls media; do
  cp -f "$M/javafx-$m/21.0.5/javafx-$m-21.0.5-mac-aarch64.jar" "$MP/" 2>/dev/null || true
done

# 编译
mkdir -p out_run
"$JAVAC" --module-path "$MP" --add-modules javafx.controls,javafx.media \
  -encoding UTF-8 -d out_run $(find src -name '*.java')

# 运行（工作目录为项目根，确保能找到 assets/sound）
exec "$JAVA" --module-path "$MP" --add-modules javafx.controls,javafx.media \
  -cp out_run tankgame.Launcher
