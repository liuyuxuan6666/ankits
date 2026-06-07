#!/bin/bash
#
# Download offline TTS/ASR models and native libraries for Ankits.
# These files are NOT stored in git — each developer runs this script once.
#
# Usage:
#   chmod +x scripts/download-models.sh
#   ./scripts/download-models.sh
#
# The script downloads ~150 MB and extracts files into:
#   app/src/main/assets/tts/    — Piper Chinese TTS model + espeak-ng data
#   app/src/main/assets/asr/    — Zipformer2 CTC ASR model
#   app/src/main/jniLibs/       — onnxruntime + sherpa-onnx JNI libraries

set -euo pipefail

SHERPA_VERSION="1.12.0"
ONNX_VERSION="1.19.2"

REPO="https://github.com/k2-fsa/sherpa-onnx/releases/download"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
JNILIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"

echo "=== Ankits model downloader ==="
echo ""

# ---- onnxruntime JNI libs ----
echo "[1/4] Downloading onnxruntime JNI libraries..."

ONNX_URL="https://github.com/microsoft/onnxruntime/releases/download/v${ONNX_VERSION}/onnxruntime-android-${ONNX_VERSION}.aar"

mkdir -p /tmp/ankits-dl && cd /tmp/ankits-dl

# Download the AAR and extract .so files
if [ ! -f onnxruntime.aar ]; then
    curl -L -o onnxruntime.aar "$ONNX_URL"
fi

unzip -o onnxruntime.aar -d onnxruntime_extracted 2>/dev/null
for arch in arm64-v8a armeabi-v7a; do
    mkdir -p "$JNILIBS_DIR/$arch"
    if [ -f onnxruntime_extracted/jni/$arch/libonnxruntime.so ]; then
        cp onnxruntime_extracted/jni/$arch/libonnxruntime.so "$JNILIBS_DIR/$arch/"
        echo "  ✓ $arch/libonnxruntime.so"
    fi
done

# ---- sherpa-onnx JNI libs ----
echo "[2/4] Downloading sherpa-onnx JNI libraries..."

SHERPA_ANDROID_URL="$REPO/v${SHERPA_VERSION}/sherpa-onnx-android-v${SHERPA_VERSION}.tar.bz2"

if [ ! -f sherpa-android.tar.bz2 ]; then
    curl -L -o sherpa-android.tar.bz2 "$SHERPA_ANDROID_URL"
fi

tar xf sherpa-android.tar.bz2 --strip-components=1 "*/jniLibs/" 2>/dev/null || true

# Find and copy JNI libs — the archive structure varies by version
SHERPA_JNI_DIR=$(find . -path "*/jniLibs" -type d 2>/dev/null | head -1)
if [ -n "$SHERPA_JNI_DIR" ]; then
    for arch in arm64-v8a armeabi-v7a; do
        mkdir -p "$JNILIBS_DIR/$arch"
        if [ -f "$SHERPA_JNI_DIR/$arch/libsherpa-onnx-jni.so" ]; then
            cp "$SHERPA_JNI_DIR/$arch/libsherpa-onnx-jni.so" "$JNILIBS_DIR/$arch/"
            echo "  ✓ $arch/libsherpa-onnx-jni.so"
        fi
    done
else
    echo "  ⚠ Could not find JNI libs in archive — download manually from:"
    echo "    $SHERPA_ANDROID_URL"
fi

# ---- TTS model ----
echo "[3/4] Downloading TTS model (Piper Chinese, ~63 MB)..."

TTS_URL="$REPO/tts-models/vits-piper-zh_CN-huayan-medium.tar.bz2"

if [ ! -f tts-model.tar.bz2 ]; then
    curl -L -o tts-model.tar.bz2 "$TTS_URL"
fi

mkdir -p "$ASSETS_DIR/tts"
tar xf tts-model.tar.bz2 -C "$ASSETS_DIR/tts/" --strip-components=1 2>/dev/null || true
echo "  ✓ $ASSETS_DIR/tts/"

# ---- ASR model ----
echo "[4/4] Downloading ASR model (Zipformer2 CTC int8, ~26 MB)..."

ASR_URL="$REPO/asr-models/sherpa-onnx-zipformer-ctc-multilingual-int8-2025-03-20.tar.bz2"

if [ ! -f asr-model.tar.bz2 ]; then
    curl -L -o asr-model.tar.bz2 "$ASR_URL"
fi

mkdir -p "$ASSETS_DIR/asr"
tar xf asr-model.tar.bz2 -C "$ASSETS_DIR/asr/" --strip-components=1 2>/dev/null || true
echo "  ✓ $ASSETS_DIR/asr/"

# ---- Cleanup ----
rm -rf /tmp/ankits-dl

echo ""
echo "=== Done ==="
echo "Models and libraries installed to:"
echo "  $ASSETS_DIR/tts/"
echo "  $ASSETS_DIR/asr/"
echo "  $JNILIBS_DIR/"
