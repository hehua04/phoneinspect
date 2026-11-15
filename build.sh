#!/bin/bash
# PhoneInspect 手工打包脚本（不依赖 Gradle / Android Studio）
# 用法：bash build.sh
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE" && pwd -W 2>/dev/null || echo "$HERE")"

BT_VER="35.0.0"
SDK="$ROOT/sdk"
BT="$SDK/build-tools/$BT_VER"
AJAR="$SDK/platforms/android-35/android.jar"
APP="$ROOT/app"
OUT="$ROOT/build"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
  *)                    EXE=""     ;;
esac

if [ -n "$(command -v python3)" ]; then PY="python3"; else PY="python"; fi

LIBDIR="$ROOT/libs"
JARS=""
for j in "$LIBDIR"/*.jar; do
  [ -f "$j" ] && JARS="$JARS $j"
done
if [ -z "$JARS" ]; then
  echo "!! libs/ 下没有 jar，请先放入 okhttp 与 okio"; exit 1
fi
echo "==> 依赖：$JARS"

if [ ! -f "$AJAR" ] || [ ! -d "$BT" ]; then
  echo "==> SDK 组件缺失，调用 bootstrap.sh"
  bash "$ROOT/bootstrap.sh" || exit 1
fi

echo "==> 清理"
rm -rf "$OUT"
mkdir -p "$OUT/dex"

echo "==> 1/6 编译资源"
"$BT/aapt2$EXE" compile --dir "$APP/res" -o "$OUT/res.zip" || exit 1

echo "==> 2/6 链接资源与清单"
"$BT/aapt2$EXE" link -o "$OUT/base.apk" -I "$AJAR" \
  --manifest "$APP/AndroidManifest.xml" "$OUT/res.zip" || exit 1

echo "==> 3/6 编译 Java"
mkdir -p "$OUT/classes"
find "$APP/java" -name "*.java" > "$OUT/sources.txt"
if [ "$EXE" = ".exe" ]; then SEP=";"; else SEP=":"; fi
CP="$AJAR"
for j in $JARS; do CP="$CP$SEP$j"; done
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -classpath "$CP" -d "$OUT/classes" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1
if [ $? -ne 0 ] || [ ! -f "$OUT/classes/com/phoneinspect/MainActivity.class" ]; then
  echo "!! javac 失败："; cat "$OUT/javac.log"; exit 1
fi

echo "==> 4/6 转 dex"
cd "$OUT/classes" && jar cf "$OUT/classes.jar" . || exit 1
cd "$OUT"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$AJAR" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar" $JARS || exit 1

echo "==> 5/6 合并 dex 并对齐"
"$PY" - "$OUT" <<'PY'
import sys, os, zipfile, shutil
out = sys.argv[1]
src = os.path.join(out, "base.apk")
dst = os.path.join(out, "unsigned.apk")
if os.path.exists(dst):
    os.remove(dst)
shutil.copyfile(src, dst)
z = zipfile.ZipFile(dst, "a", zipfile.ZIP_DEFLATED)
z.write(os.path.join(out, "dex", "classes.dex"), "classes.dex")
z.close()
print("    dex 已合并")
PY
[ -f "$OUT/unsigned.apk" ] || exit 1
"$BT/zipalign$EXE" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk" || exit 1

echo "==> 6/6 签名"
KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=PhoneInspect,O=Debug,C=CN" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1 || exit 1
  echo "    已生成调试签名 debug.keystore"
fi
java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$ROOT/PhoneInspect.apk" "$OUT/aligned.apk" || exit 1

echo "==> 校验"
java -jar "$BT/lib/apksigner.jar" verify --print-certs "$ROOT/PhoneInspect.apk" | head -3
ls -lh "$ROOT/PhoneInspect.apk"
echo "完成"
