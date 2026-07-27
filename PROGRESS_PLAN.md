# Conductor OS Progress

Repo: https://github.com/aselsby/aifirstios  
Updated: 2026-07-27

## Goal

Ship an AI-first mobile OS layer where voice + multi-app context + configurable autonomy + logged-in app agents complete real user outcomes.

## Achieved

### Platform

- Monorepo published to GitHub (`aselsby/aifirstios`)
- Root `npm test` verification
- GitHub Actions CI (JS/static + Android assemble)
- Gradle wrapper checked in
- **Debug APK builds successfully** (`./gradlew :app:assembleDebug`)

### Honesty / control plane

- `SystemClock` wall time
- Production `AccessibilityQueueingLiveBridge` (no false verified app ops)
- Recording simulation explicitly labeled
- AccessibilityService consumes receipts + autonomy budgets after live verify
- App skills UI grouped by package

### Live context (hero outdoor flow)

- **Device calendar** free/busy via CalendarContract (permission-aware)
- **Open-Meteo weather** live HTTP (location-aware when permitted)
- **Device contacts** preferred-invite lookup (permission-aware)
- Nearby outdoor places via OpenStreetMap Nominatim (events source until Facebook OAuth)
- Accessibility settings deep-link from launcher
- Launcher requests calendar / location / contacts permissions on first launch

### Safety retained

- Autonomy modes + exact approval for send/post
- Purpose-scoped graph grants
- Handoff queue for login/grants/inputs
- Instant stop → ASK_ONLY

## Remaining to goal

| Priority | Item |
|----------|------|
| P0 | Device install smoke on hardware/emulator; enable a11y; end-to-end outdoor demo video |
| P0 | Realtime model websocket (token service exists; stream still scaffold) |
| P1 | Facebook/events real connector (or alternative event source) |
| P1 | Harden Messages/Calendar/Maps playbooks against real UI trees |
| P2 | Room/SQLCipher store; shared JS/Android contracts package |
| P2 | iOS companion hub (not OS claim) |

## Verify now

```bash
npm test
cd conductor-android-prototype && ./gradlew :app:assembleDebug
```
