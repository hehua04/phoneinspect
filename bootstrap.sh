#!/bin/bash
# 下载并解压 Android SDK 的 build-tools 与 platform，放到 ./sdk
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE" && pwd -W 2>/dev/null || echo "$HERE")"
SDK="$ROOT/sdk"
DL="$ROOT/sdk_dl"
BT_VER="35.0.0"

if [ -f "$SDK/platforms/android-35/android.jar" ] && [ -d "$SDK/build-tools/$BT_VER" ]; then
  echo "SDK 已就绪：$SDK"
  exit 0
fi

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) OS="windows" ;;
  Linux)                OS="linux"   ;;
  *)
    echo "!! 暂不支持自动下载该系统，请手动准备："
    echo "   sdk/build-tools/$BT_VER/"
    echo "   sdk/platforms/android-35/android.jar"
    exit 1
    ;;
esac

mkdir -p "$DL" "$SDK/build-tools" "$SDK/platforms"

BT_ZIP="build-tools_r35_${OS}.zip"
PF_ZIP="platform-35_r02.zip"
BASE="https://dl.google.com/android/repository"

if [ ! -f "$DL/$BT_ZIP" ]; then
  echo "==> 下载 $BT_ZIP（约 58MB）"
  curl -L --fail --progress-bar -o "$DL/$BT_ZIP" "$BASE/$BT_ZIP" || exit 1
fi
if [ ! -f "$DL/$PF_ZIP" ]; then
  echo "==> 下载 $PF_ZIP（约 62MB）"
  curl -L --fail --progress-bar -o "$DL/$PF_ZIP" "$BASE/$PF_ZIP" || exit 1
fi

echo "==> 解压 build-tools"
rm -rf "$DL/bt_tmp" && unzip -q -o "$DL/$BT_ZIP" -d "$DL/bt_tmp" || exit 1
BT_DIR="$(find "$DL/bt_tmp" -maxdepth 1 -mindepth 1 -type d | head -1)"
rm -rf "$SDK/build-tools/$BT_VER"
mv "$BT_DIR" "$SDK/build-tools/$BT_VER"

echo "==> 解压 platform"
rm -rf "$DL/pf_tmp" && unzip -q -o "$DL/$PF_ZIP" -d "$DL/pf_tmp" || exit 1
PF_DIR="$(find "$DL/pf_tmp" -maxdepth 1 -mindepth 1 -type d | head -1)"
rm -rf "$SDK/platforms/android-35"
mv "$PF_DIR" "$SDK/platforms/android-35"

chmod +x "$SDK/build-tools/$BT_VER/aapt2" "$SDK/build-tools/$BT_VER/zipalign" 2>/dev/null || true

echo "==> 完成：$SDK"
