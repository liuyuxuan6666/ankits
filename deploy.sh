#!/bin/bash
set -e

echo "=== 编译中... ==="
./gradlew assembleDebug

echo ""
echo "=== 安装到模拟器... ==="
/home/user/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

echo ""
echo "=== 启动应用 ==="
/home/user/Android/Sdk/platform-tools/adb shell am start -n com.example.ankits/.MainActivity

echo ""
echo "完成!"
