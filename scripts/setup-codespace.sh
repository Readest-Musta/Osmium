#!/usr/bin/env bash
# One-shot environment setup for building Osmium (designed for Codespaces/Ubuntu).
set -euo pipefail

echo "==> Checking required tools"
need_rust() {
    if ! command -v cargo >/dev/null; then
        echo "==> Installing Rust"
        curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable
        export PATH="$HOME/.cargo/bin:$PATH"
    fi
}
need_rust

if ! rustup target list --installed 2>/dev/null | grep -q aarch64-linux-android; then
    echo "==> Adding Android Rust targets"
    rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
fi

echo "==> Checking JDK 17"
if ! java -version 2>&1 | grep -q 'version "17\|version "21'; then
    echo "WARNING: JDK 17 not found on PATH — install Temurin 17 or use actions/setup-java."
fi

export ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -d "$ANDROID_HOME" ]; then
    echo "==> Ensuring NDK + SDK 35 are installed"
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;27.0.12077973" "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1 || \
        echo "WARNING: sdkmanager failed — install NDK + platform 35 manually."
    export ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort | tail -n1 || true)"
else
    echo "WARNING: ANDROID_HOME ($ANDROID_HOME) does not exist."
fi

echo "==> Environment ready:"
echo "    PATH includes: $(command -v cargo || true)"
echo "    ANDROID_NDK_HOME: ${ANDROID_NDK_HOME:-unset}"
echo "Next: ./scripts/build-native.sh && ./gradlew assembleDebug"
