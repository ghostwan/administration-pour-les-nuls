#!/usr/bin/env bash
#
# run.sh - Build, install and launch PassportSlotNotifier
#
# Usage:
#   ./run.sh              # Build debug, install and launch
#   ./run.sh debug        # Same as above
#   ./run.sh release      # Build release, install and launch
#   ./run.sh --build-only # Build only (no install/launch)
#   ./run.sh --help       # Show help
#

set -euo pipefail

APP_ID="fr.music.passportslot"
MAIN_ACTIVITY="${APP_ID}.ui.MainActivity"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    echo "Usage: $0 [debug|release] [OPTIONS]"
    echo ""
    echo "Build, install and launch PassportSlotNotifier on a connected Android device."
    echo ""
    echo "Arguments:"
    echo "  debug       Build in debug mode (default)"
    echo "  release     Build in release mode"
    echo ""
    echo "Options:"
    echo "  --build-only    Build the APK without installing or launching"
    echo "  --no-launch     Install but don't launch the app"
    echo "  --clean         Run a clean build"
    echo "  --help, -h      Show this help message"
    echo ""
    echo "Prerequisites:"
    echo "  - Android SDK installed (ANDROID_HOME set)"
    echo "  - A connected device or running emulator (adb devices)"
    echo "  - Java 17+ installed"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Defaults
BUILD_TYPE="debug"
BUILD_ONLY=false
NO_LAUNCH=false
CLEAN=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        debug)
            BUILD_TYPE="debug"
            shift
            ;;
        release)
            BUILD_TYPE="release"
            shift
            ;;
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --no-launch)
            NO_LAUNCH=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown argument: $1"
            usage
            exit 1
            ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check prerequisites
log_info "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    log_error "Java is not installed. Please install JDK 17+."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 17 ]]; then
    log_warn "Java $JAVA_VERSION detected. JDK 17+ is recommended."
fi

if [[ -z "${ANDROID_HOME:-}" ]] && [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    # Try common locations
    if [[ -d "$HOME/Library/Android/sdk" ]]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [[ -d "$HOME/Android/Sdk" ]]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        log_error "ANDROID_HOME is not set and Android SDK not found in common locations."
        exit 1
    fi
    log_info "Auto-detected ANDROID_HOME: $ANDROID_HOME"
fi

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ADB="${SDK_DIR}/platform-tools/adb"

if [[ ! -x "$ADB" ]]; then
    if command -v adb &> /dev/null; then
        ADB="adb"
    else
        log_error "adb not found. Please install Android SDK platform-tools."
        exit 1
    fi
fi

# Use gradlew if available, otherwise try gradle
if [[ -x "./gradlew" ]]; then
    GRADLE="./gradlew"
elif command -v gradle &> /dev/null; then
    GRADLE="gradle"
else
    log_error "Gradle wrapper (gradlew) not found and gradle is not installed."
    log_info "You can generate the wrapper with: gradle wrapper --gradle-version 8.5"
    exit 1
fi

# Clean if requested
if [[ "$CLEAN" == true ]]; then
    log_info "Cleaning build..."
    $GRADLE clean
    log_success "Clean complete."
fi

# Determine Gradle task and APK path
if [[ "$BUILD_TYPE" == "release" ]]; then
    GRADLE_TASK="assembleRelease"
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    APK_UNSIGNED_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
else
    GRADLE_TASK="assembleDebug"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

# Build
log_info "Building $BUILD_TYPE APK..."
$GRADLE "$GRADLE_TASK" --warning-mode all

# Find the APK
if [[ -f "$APK_PATH" ]]; then
    APK="$APK_PATH"
elif [[ "$BUILD_TYPE" == "release" ]] && [[ -f "$APK_UNSIGNED_PATH" ]]; then
    APK="$APK_UNSIGNED_PATH"
    log_warn "Using unsigned release APK. Sign it before distribution."
else
    # Try to find the APK in the build directory
    APK=$(find app/build/outputs/apk -name "*.apk" -type f 2>/dev/null | head -n 1)
    if [[ -z "$APK" ]]; then
        log_error "APK not found after build."
        exit 1
    fi
fi

APK_SIZE=$(du -h "$APK" | cut -f1)
log_success "Build complete: $APK ($APK_SIZE)"

if [[ "$BUILD_ONLY" == true ]]; then
    log_info "Build-only mode. Skipping install and launch."
    exit 0
fi

# Check for connected device
log_info "Checking for connected device..."
DEVICE_COUNT=$("$ADB" devices 2>/dev/null | grep -c "device$" || true)

if [[ "$DEVICE_COUNT" -eq 0 ]]; then
    log_error "No Android device connected."
    log_info "Connect a device via USB or start an emulator."
    log_info "  Emulator: ${SDK_DIR}/emulator/emulator -list-avds"
    log_info "  USB: enable USB debugging in Developer options"
    exit 1
fi

if [[ "$DEVICE_COUNT" -gt 1 ]]; then
    log_warn "Multiple devices connected. Using the first one."
    log_info "Available devices:"
    "$ADB" devices -l
fi

DEVICE=$("$ADB" devices | grep "device$" | head -n 1 | cut -f1)
log_info "Target device: $DEVICE"

# Install
log_info "Installing APK on device..."
"$ADB" -s "$DEVICE" install -r "$APK"
log_success "Installation complete."

if [[ "$NO_LAUNCH" == true ]]; then
    log_info "No-launch mode. App installed but not started."
    exit 0
fi

# Launch
log_info "Launching app..."
"$ADB" -s "$DEVICE" shell am start -n "${APP_ID}/${MAIN_ACTIVITY}" \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER
log_success "App launched on device $DEVICE"

echo ""
echo -e "${GREEN}Done!${NC} RDV Passeport is running on your device."
