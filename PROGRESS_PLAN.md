# Conductor OS Progress

Repo: https://github.com/aselsby/aifirstios  
Updated: 2026-08-01

## Goal

AI-first Android OS layer: voice, multi-app context, apps as subagents, autonomy + exact approval, honest live a11y ops.

## Gate status (self-improvement loop)

| Gate | Status | Evidence |
|------|--------|----------|
| G1 Build/install/smoke | **PASS** | `assembleDebug`, `device_smoke_test.js --strict` ready |
| G2 Outdoor multi-app plan | **PASS** | Live weather/places; Free Riverfront Park Walk |
| G3 Voice on hardware | **OPEN** | Needs physical phone STT path |
| G4 Live a11y verify | **PASS (demo + OEM Messages + OEM Maps)** | See below |
| G5 Approval→execute | **PASS (control plane)** | See below |
| G6 Money safety | **PARTIAL** | Exact approval enforced; bank UI E2E not live |
| G7 Instant stop | **PASS (harness)** | `autonomy.stopped` + queue clear + ASK_ONLY |
| G8 Real model stream | **OPEN** | Transport still scaffolded |

### G4 evidence (controlled)

`ConductorAgentDemoActivity` + `conductor_demo_live_draft` — `node device_live_g4_test.js`

### G4-OEM Messages

`life_messaging` → `messages_draft_invite` on Google Messages (`smsto` + editable `set_text`).

```text
operator.live_verified ...accessibility_live_tree:messages_draft_invite:post_state_receipt
```

Harness: `node device_oem_g4_test.js`

### G4-OEM Maps

`life_maps` → `maps_open_route` on Google Maps (`geo:0,0?q=` + destination text `verify`).

```text
operator.live_verified operation_maps_route_open:accessibility_live_tree:maps_open_route:post_state_receipt
accessibility.queue_resolved operation_maps_route_open:maps_open_route
```

Harness: `node device_oem_maps_g4_test.js` → `status: ready`, `gate: G4-OEM-Maps`

**Honest limits:** Place/destination sheet verify (not full turn-by-turn start). Recovery dismisses SKIP/Got it/Dismiss interstitials when present.

### G5 + G7 evidence

`node device_g5_approval_test.js` → `status: ready`, `gate: G5+G7`

## Remaining to full OS test-complete

1. **G3:** physical device voice  
2. **G8:** real LLM behind token service  
3. **G6:** money dry-run E2E without real funds  
4. Optional: Messages **send** after exact approval on real UI  

## Verify

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
npm test
cd conductor-android-prototype
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
node device_smoke_test.js --strict
node device_live_g4_test.js
node device_oem_g4_test.js
node device_oem_maps_g4_test.js
node device_g5_approval_test.js
```
