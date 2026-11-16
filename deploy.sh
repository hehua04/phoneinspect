#!/bin/bash
# 构建 + 校验 + 安装，一条命令走完。
# 存在的理由：`bash build.sh | tail -3 && adb install ...` 这种写法里，
# 管道的退出码是 tail 的，构建失败照样会往下走，于是把仓库里上一次残留的
# APK 装到手机上——看起来像"先装了旧包"。
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE" && pwd -W 2>/dev/null || echo "$HERE")"
BT_VER="35.0.0"
BT="$ROOT/sdk/build-tools/$BT_VER"
APK="$ROOT/PhoneInspect.apk"
VP="$ROOT/version.properties"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
  *)                    EXE=""     ;;
esac

LOG="$(mktemp)"

echo "==> 构建"
if ! bash "$ROOT/build.sh" > "$LOG" 2>&1; then
  echo "!! 构建失败，已中止，不会安装旧包："
  tail -25 "$LOG"
  exit 1
fi
tail -3 "$LOG"

echo "==> 校验 APK 版本"
if [ ! -f "$APK" ]; then echo "!! 产物不存在：$APK"; exit 1; fi
EXPECT_C="$(grep -E '^versionCode=' "$VP" | head -1 | cut -d= -f2 | tr -d ' \r')"
EXPECT_N="$(grep -E '^versionName=' "$VP" | head -1 | cut -d= -f2 | tr -d ' \r')"
BADGE="$("$BT/aapt2$EXE" dump badging "$APK" 2>/dev/null | head -1)"
ACTUAL_C="$(echo "$BADGE" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")"
ACTUAL_N="$(echo "$BADGE" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
if [ "$EXPECT_C" != "$ACTUAL_C" ] || [ "$EXPECT_N" != "$ACTUAL_N" ]; then
  echo "!! APK 是 $ACTUAL_N($ACTUAL_C)，但 version.properties 是 $EXPECT_N($EXPECT_C)"
  echo "   说明产物没更新，中止"
  exit 1
fi
echo "    APK = $ACTUAL_N ($ACTUAL_C)"

echo "==> 安装"
# mdns 在 Windows 上经常什么也发现不了（后台服务偶发失效），
# 而且一旦 adb server 被重启（比如构建期间别的 adb 命令），已有的无线连接会直接掉。
# 所以允许手动指定：ADB_TARGET=192.168.3.56:42883 bash deploy.sh
TARGET="${ADB_TARGET:-}"
if [ -z "$TARGET" ]; then
  TARGET="$(adb mdns services 2>/dev/null \
            | grep -oE '[0-9]{1,3}(\.[0-9]{1,3}){3}:[0-9]+' | head -1)"
fi
if [ -n "$TARGET" ]; then
  adb connect "$TARGET" >/dev/null 2>&1
  echo "    设备 $TARGET"
fi
if ! adb devices 2>/dev/null | tr -d '\r' | grep -qE '[[:space:]]device$'; then
  echo "!! 没有可用设备。无线调试端口每次打开都会变，可以显式指定："
  echo "   ADB_TARGET=<手机IP>:<端口> bash deploy.sh"
  exit 1
fi
OUT="$(adb install -r "$APK" 2>&1 | tail -1)"
echo "    $OUT"
case "$OUT" in
  *Success*) ;;
  *) echo "!! 安装失败"; exit 1 ;;
esac

echo "==> 校验设备上的版本"
INSTALLED="$(adb shell dumpsys package com.phoneinspect 2>/dev/null \
             | grep -m1 versionCode | tr -d '\r' | sed 's/^ *//')"
echo "    $INSTALLED"
case "$INSTALLED" in
  *"$EXPECT_C"*) echo "完成" ;;
  *) echo "!! 设备上的 versionCode 不是 $EXPECT_C，安装可能没生效"; exit 1 ;;
esac
