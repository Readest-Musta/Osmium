#!/usr/bin/env bash
# Builds riti for all Android ABIs, generates UniFFI Kotlin bindings,
# and places everything where Gradle expects it.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_ROOT="$(pwd)"
NATIVE="$REPO_ROOT/native"
JNI_LIBS="$REPO_ROOT/app/src/main/jniLibs"
BINDINGS="$REPO_ROOT/app/src/uniffi"

if ! command -v cargo >/dev/null; then
    echo "error: Rust toolchain not found (cargo)." >&2
    exit 1
fi

# Host build is used only for generating the Kotlin bindings (uniffi-bindgen
# needs to read the metadata out of a library for the current platform).
echo "==> Building host library for bindings generation"
cargo build --release --manifest-path "$NATIVE/Cargo.toml"

mkdir -p "$BINDINGS"
echo "==> Generating Kotlin bindings"
(cd "$NATIVE" && cargo run --bin uniffi-bindgen -- \
    generate --library target/release/libosmium_native.so \
    --language kotlin --out-dir "$BINDINGS")

# Force the generated Kotlin to load our .so name.
GEN_FILE=$(find "$BINDINGS" -name '*.kt' | head -n1)
if [ -f "$GEN_FILE" ]; then
    echo "==> Patching library load name in $GEN_FILE"
    # Show what's there for transparency, then normalize.
    grep -n 'loadLibrary\|uniffi' "$GEN_FILE" | head -20 || true
    sed -i -E 's/System\.loadLibrary\("[^"]*"\)/System.loadLibrary("osmium_native")/g' "$GEN_FILE"
fi

if [ "${1:-}" = "bindings-only" ]; then
    echo "==> Done (bindings only)."
    exit 0
fi

if ! command -v cargo-ndk >/dev/null; then
    echo "==> Installing cargo-ndk"
    cargo install cargo-ndk
fi
if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
    export ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort | tail -n1 || true)"
fi
if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "error: ANDROID_NDK_HOME not set or NDK missing." >&2
    exit 1
fi
echo "==> Using NDK at $ANDROID_NDK_HOME"

echo "==> Building riti for Android (arm64-v8a, armeabi-v7a, x86_64)"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -p 24 build --release \
    --manifest-path "$NATIVE/Cargo.toml"

echo "==> Copying .so files into jniLibs"
for abi in arm64-v8a armeabi-v7a x86_64; do
    case "$abi" in
        arm64-v8a)   triple="aarch64-linux-android" ;;
        armeabi-v7a) triple="armv7-linux-androideabi" ;;
        x86_64)      triple="x86_64-linux-android" ;;
    esac
    mkdir -p "$JNI_LIBS/$abi"
    cp "$NATIVE/target/$triple/release/libosmium_native.so" "$JNI_LIBS/$abi/"
done

echo "==> Done. Now run: ./gradlew assembleDebug"
