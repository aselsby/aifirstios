# Conductor OS Progress

Repo: https://github.com/aselsby/aifirstios  
Updated: 2026-07-27

## Goal

Ship an AI-first mobile OS layer where voice + multi-app context + configurable autonomy + logged-in app agents complete real user outcomes.

## Achieved (Device Alpha)

### Platform / GitHub

- Monorepo live at https://github.com/aselsby/aifirstios
- Root `npm test` verification
- GitHub Actions CI (JS/static + Android assemble)
- Gradle wrapper checked in
- **Debug APK builds** (`./gradlew :app:assembleDebug`)
- **Native preflight: ready** (SDK, adb, emulator, gradle)
- **Device smoke: ready (strict)** on emulator-5554
- Package installed and launcher activity starts on emulator

### Honesty / control plane

- `SystemClock` wall time
- Production `AccessibilityQueueingLiveBridge` (no false verified app ops)
- Recording simulation labeled
- AccessibilityService consumes receipts + autonomy budgets after live verify
- App skills UI grouped by package
- Accessibility settings deep-link from launcher

### Live context (hero outdoor flow)

- Device calendar free/busy via CalendarContract
- Open-Meteo live weather
- Nearby outdoor places via OpenStreetMap Nominatim
- Device contacts preferred-invite lookup
- Permission prompts + adb-grantable runtime permissions

### Safety retained

- Autonomy modes + exact approval for send/post
- Purpose-scoped graph grants
- Handoff queue for login/grants/inputs
- Instant stop → ASK_ONLY

## Remaining to full goal

| Priority | Item |
|----------|------|
| P0 | Realtime model websocket (token service exists; stream scaffolded) |
| P0 | Enable a11y on device + verify one live playbook end-to-end |
| P1 | Facebook Graph OAuth events (Nominatim is interim outdoor source) |
| P1 | Harden Messages/Calendar/Maps playbooks on real UI trees |
| P2 | Room/SQLCipher; shared JS/Android contracts |

## Verify now

```bash
npm test
cd conductor-android-prototype
export ANDROID_HOME=... # your SDK
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
node device_smoke_test.js --strict
```
