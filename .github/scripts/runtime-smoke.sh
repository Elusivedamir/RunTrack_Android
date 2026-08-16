#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p ci-logs

APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.runtrack.app"
COMPONENT="com.runtrack.app/.MainActivity"

echo "============================================================"
echo "RUNTRACK ANDROID RUNTIME SMOKE TEST"
echo "============================================================"

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found: $APK"
    exit 1
fi

echo
echo "[1/7] Waiting for Android Emulator"
adb wait-for-device

echo
echo "[2/7] Running Android instrumentation tests"
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest \
    2>&1 | tee ci-logs/07-connected-android-tests.log

echo
echo "[3/7] Clearing old logcat"
adb logcat -c

echo
echo "[4/7] Installing APK"
adb install -r "$APK" \
    | tee ci-logs/07-runtime-install.txt

echo
echo "[5/7] Starting MainActivity"
adb shell am force-stop "$PACKAGE"

set +e
START_OUTPUT="$(adb shell am start -W -n "$COMPONENT" 2>&1)"
START_CODE=$?
set -e

{
    echo "============================================================"
    echo "RUNTRACK RUNTIME START"
    echo "============================================================"
    echo "$START_OUTPUT"
    echo
    echo "am start exit code: $START_CODE"
} | tee ci-logs/08-runtime-start.txt

if [[ "$START_CODE" -ne 0 ]]; then
    echo "ERROR: MainActivity launch command failed."
    exit 1
fi

echo
echo "[6/7] Waiting for delayed startup crashes"
sleep 12

adb logcat -d -v threadtime \
    > ci-logs/09-runtime-logcat.txt

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r' || true)"

{
    echo "============================================================"
    echo "RUNTRACK RUNTIME STATE"
    echo "============================================================"

    echo
    echo "PACKAGE:"
    echo "$PACKAGE"

    echo
    echo "PID:"
    echo "${PID:-<none>}"

    echo
    echo "ACTIVITY STATE:"
    adb shell dumpsys activity activities \
        | grep -E \
            'mResumedActivity|topResumedActivity|mFocusedApp|com\.runtrack\.app' \
        || true

    echo
    echo "WINDOW STATE:"
    adb shell dumpsys window windows \
        | grep -E \
            'mCurrentFocus|mFocusedApp|com\.runtrack\.app' \
        || true

} | tee ci-logs/10-runtime-state.txt

if [[ -z "$PID" ]]; then
    echo "ERROR: RunTrack process is not alive after startup."
    exit 1
fi

echo
echo "[7/7] Checking logcat for fatal RunTrack failures"

if grep -E \
    'FATAL EXCEPTION.*|Process: com\.runtrack\.app|Unable to start activity.*com\.runtrack\.app|RuntimeException: Unable to.*com\.runtrack\.app|ANR in com\.runtrack\.app|Force finishing activity.*com\.runtrack\.app' \
    ci-logs/09-runtime-logcat.txt; then

    echo
    echo "ERROR: Runtime crash/ANR detected in RunTrack logcat."
    exit 1
fi

if grep -q \
    'com\.runtrack\.app.*CrashReportActivity\|CrashReportActivity.*com\.runtrack\.app' \
    ci-logs/10-runtime-state.txt; then

    echo
    echo "ERROR: Built-in CrashReportActivity became active."
    exit 1
fi

echo
echo "============================================================"
echo "SUCCESS: RunTrack survived startup smoke-test."
echo "PID: $PID"
echo "============================================================"