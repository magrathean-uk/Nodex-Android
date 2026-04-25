#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/../build-env.sh"

AVD_NAME="${NODEX_ANDROID_AVD_NAME:-Nodex_Pixel9Pro_API35}"
SYSTEM_IMAGE="${NODEX_ANDROID_SYSTEM_IMAGE:-system-images;android-35;google_apis;arm64-v8a}"
DEVICE_ID="${NODEX_ANDROID_DEVICE_ID:-pixel_9_pro}"
VM_NAME="${NODEX_VM_NAME:-debian}"
VM_ROOT="${NODEX_VM_ROOT:-$HOME/dev/test/termex-vms}"
PACKAGE_NAME="com.nodex.client.proof"
ACTIVITY_NAME="$PACKAGE_NAME/com.nodex.client.MainActivity"
SMOKE_CLASS="com.nodex.client.ui.qa.VisibleQaSmokeTest"
DEVICE_KEY_PATH="/data/local/tmp/nodex-ui-test-key.pem"
HOST_PORT="${NODEX_UI_TEST_LIVE_PORT:-42231}"

HOST_KEY_TMP=""
RUN_DEMO=0
RUN_LIVE=0
SERIAL=""

cleanup() {
  if [[ -n "$HOST_KEY_TMP" && -f "$HOST_KEY_TMP" ]]; then
    rm -f "$HOST_KEY_TMP"
  fi
}

trap cleanup EXIT

ensure_vm() {
  if nc -z 127.0.0.1 "$HOST_PORT" >/dev/null 2>&1; then
    return
  fi
  "$VM_ROOT/bin/start_vm.sh" "$VM_NAME"
  for _ in $(seq 1 60); do
    if nc -z 127.0.0.1 "$HOST_PORT" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  echo "VM did not start listening on port $HOST_PORT" >&2
  exit 1
}

ensure_avd() {
  if emulator -list-avds | rg -x "$AVD_NAME" >/dev/null 2>&1; then
    return
  fi
  echo "no" | avdmanager create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "$DEVICE_ID" >/dev/null
}

running_emulator_serial() {
  adb devices | awk '/emulator-[0-9]+[[:space:]]+device$/{print $1; exit}'
}

ensure_emulator() {
  local serial
  serial="$(running_emulator_serial || true)"
  if [[ -n "$serial" ]]; then
    printf '%s\n' "$serial"
    return
  fi

  nohup emulator "@$AVD_NAME" -no-snapshot -netdelay none -netspeed full >/tmp/nodex-emulator.log 2>&1 &

  for _ in $(seq 1 120); do
    serial="$(running_emulator_serial || true)"
    if [[ -n "$serial" ]]; then
      adb -s "$serial" wait-for-device >/dev/null
      until [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 2
      done
      adb -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
      printf '%s\n' "$serial"
      return
    fi
    sleep 2
  done

  echo "Emulator did not boot" >&2
  exit 1
}

has_live_fixture() {
  [[ -n "${NODEX_UI_TEST_LIVE_HOST:-}" && -n "${NODEX_UI_TEST_LIVE_USERNAME:-}" ]]
}

smoke_mode_requested() {
  [[ -n "${NODEX_UI_TEST_SCENARIO:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_LIVE_HOST:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_LIVE_USERNAME:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_KEY_TEXT:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_KEY_PATH:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_PASSWORD:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_SUDO_PASSWORD:-}" ]] \
    || [[ -n "${NODEX_UI_TEST_DIRECT_KEY_IMPORT:-}" ]]
}

prepare_key_fixture() {
  if [[ -n "${NODEX_UI_TEST_KEY_PATH:-}" ]]; then
    HOST_KEY_TMP="$(mktemp /tmp/nodex-ui-test-key.XXXXXX)"
    cp "${NODEX_UI_TEST_KEY_PATH}" "$HOST_KEY_TMP"
    return
  fi

  if [[ -n "${NODEX_UI_TEST_KEY_TEXT:-}" ]]; then
    HOST_KEY_TMP="$(mktemp /tmp/nodex-ui-test-key.XXXXXX)"
    printf '%s\n' "${NODEX_UI_TEST_KEY_TEXT}" > "$HOST_KEY_TMP"
  fi
}

build_gradle_args() {
  local scenario="$1"
  GRADLE_ARGS=(
    "-Pandroid.testInstrumentationRunnerArguments.class=$SMOKE_CLASS"
    "-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TESTING=true"
    "-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_SCENARIO=$scenario"
    "-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_RESET=true"
  )

  if [[ -n "${NODEX_UI_TEST_LIVE_HOST:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_LIVE_HOST=$NODEX_UI_TEST_LIVE_HOST")
  fi
  if [[ -n "${NODEX_UI_TEST_LIVE_NAME:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_LIVE_NAME=$NODEX_UI_TEST_LIVE_NAME")
  fi
  if [[ -n "${NODEX_UI_TEST_LIVE_PORT:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_LIVE_PORT=$NODEX_UI_TEST_LIVE_PORT")
  fi
  if [[ -n "${NODEX_UI_TEST_LIVE_USERNAME:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_LIVE_USERNAME=$NODEX_UI_TEST_LIVE_USERNAME")
  fi
  if [[ -n "${NODEX_UI_TEST_PASSWORD:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_PASSWORD=$NODEX_UI_TEST_PASSWORD")
  fi
  if [[ -n "${NODEX_UI_TEST_SUDO_PASSWORD:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_SUDO_PASSWORD=$NODEX_UI_TEST_SUDO_PASSWORD")
  fi
  if [[ -n "${NODEX_UI_TEST_KEY_NAME:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_KEY_NAME=$NODEX_UI_TEST_KEY_NAME")
  fi

  if [[ -n "$HOST_KEY_TMP" ]]; then
    adb -s "$SERIAL" push "$HOST_KEY_TMP" "$DEVICE_KEY_PATH" >/dev/null
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_KEY_PATH=$DEVICE_KEY_PATH")
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_DIRECT_KEY_IMPORT=true")
  elif [[ "${NODEX_UI_TEST_DIRECT_KEY_IMPORT:-}" != "" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.NODEX_UI_TEST_DIRECT_KEY_IMPORT=$NODEX_UI_TEST_DIRECT_KEY_IMPORT")
  fi
}

run_smoke() {
  local scenario="$1"
  adb -s "$SERIAL" shell pm clear com.nodex.client >/dev/null 2>&1 || true
  build_gradle_args "$scenario"
  (
    cd "$ROOT"
    ./gradlew connectedDebugAndroidTest "${GRADLE_ARGS[@]}"
  )
}

fallback_launch() {
  (
    cd "$ROOT"
    ./gradlew installReleaseProof
  )

  adb -s "$SERIAL" shell am start -n "$ACTIVITY_NAME" >/dev/null
  printf 'Emulator: %s\n' "$SERIAL"
  printf 'App: %s\n' "$PACKAGE_NAME"
}

if smoke_mode_requested; then
  if ! has_live_fixture; then
    RUN_DEMO=1
  else
    case "${NODEX_UI_TEST_SCENARIO:-auto}" in
      demo)
        RUN_DEMO=1
        ;;
      live)
        RUN_LIVE=1
        ;;
      both)
        RUN_DEMO=1
        RUN_LIVE=1
        ;;
      *)
        RUN_DEMO=1
        RUN_LIVE=1
        ;;
    esac
  fi
fi

SERIAL="$(ensure_emulator)"

if [[ "$RUN_DEMO" -eq 1 || "$RUN_LIVE" -eq 1 ]]; then
  if [[ "$RUN_LIVE" -eq 1 ]]; then
    ensure_vm
    prepare_key_fixture
    adb -s "$SERIAL" reverse "tcp:$HOST_PORT" "tcp:$HOST_PORT"
  fi

  if [[ "$RUN_DEMO" -eq 1 ]]; then
    run_smoke demo
  fi
  if [[ "$RUN_LIVE" -eq 1 ]]; then
    run_smoke live
  fi
  fallback_launch
else
  fallback_launch
fi
