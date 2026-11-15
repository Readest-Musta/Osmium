#!/usr/bin/env bash
# Full environment setup + build for Osmium, for a fresh Codespace.
set -euo pipefail
LOG_PREFIX="[osmium]"

echo "$LOG_PREFIX ==> Rust"
if ! command -v cargo >/dev/null; then
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable >/dev/null 2>&1
fi
export PATH="$HOME/.cargo/bin:$PATH"
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

echo "$LOG_PREFIX ==> JDK 17"
JAVA_CAND="$(ls /usr/local/sdkman/candidates/java/ 2>/dev/null | grep -E '^17' | head -n1 || true)"
if [ -n "$JAVA_CAND" ]; then
    JAVA17_HOME="/usr/local/sdkman/candidates/java/$JAVA_CAND"
else
    ARCH=$(uname -m)
    case "$ARCH" in
        x86_64) JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz" ;;
        aarch64) JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.13_11.tar.gz" ;;
        *) echo "unsupported arch $ARCH"; exit 1 ;;
    esac
    curl -sfL -o /tmp/jdk17.tgz "$JDK_URL"
    mkdir -p "$HOME/jdk17"
    tar -xzf /tmp/jdk17.tgz -C "$HOME/jdk17" --strip-components=1
    JAVA17_HOME="$HOME/jdk17"
fi
"$JAVA17_HOME/bin/java" -version 2>&1 | head -1

echo "$LOG_PREFIX ==> Android SDK"
SDK="$HOME/android-sdk"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
    mkdir -p "$SDK/cmdline-tools"
    curl -sfL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q -o /tmp/cmdtools.zip -d "$SDK/cmdline-tools/"
    mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973" >/dev/null 2>&1 || true
export ANDROID_NDK_HOME="$(ls -d "$SDK"/ndk/* | sort | tail -n1)"
echo "$LOG_PREFIX     NDK: $ANDROID_NDK_HOME"

echo "$LOG_PREFIX ==> Native build (riti + uniffi)"
./scripts/build-native.sh

echo "$LOG_PREFIX ==> Gradle build"
export JAVA_HOME="$JAVA17_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
cd /workspaces/Osmium
gradle assembleDebug --no-daemon

echo "$LOG_PREFIX ==> Result"
ls -la /workspaces/Osmium/app/build/outputs/apk/debug/ || true
echo "$LOG_PREFIX BUILD_OK"
