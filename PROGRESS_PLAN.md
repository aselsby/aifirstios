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
| G4 Live a11y verify | **PASS (controlled)** | Demo agent surface; OEM still open |
| G5 Approval→execute | **PASS (control plane)** | See below |
| G6 Money safety | **PARTIAL** | Exact approval enforced; bank UI E2E not live |
| G7 Instant stop | **PASS (harness)** | `autonomy.stopped` + queue clear + ASK_ONLY |
| G8 Real model stream | **OPEN** | Transport still scaffolded |

### G4 evidence (2026-08-01)

Controlled live demo surface `ConductorAgentDemoActivity` + playbook `conductor_demo_live_draft`.

Logcat:

```text
operator.live_verified operation_demo_app_draft:accessibility_live_tree:conductor_demo_live_draft:post_state_receipt
accessibility.queue_resolved operation_demo_app_draft:conductor_demo_live_draft
```

Harness: `node device_live_g4_test.js`

**Note:** G4 is proven on a **deterministic in-app agent surface**, not yet on OEM Messages/Maps.

### G5 + G7 evidence (2026-08-01)

Harness: `node device_g5_approval_test.js` → `status: ready`, `gate: G5+G7`.

Proven path:

1. `approval.queued` + `pendingApprovals=1` for `banking.transfer.create`
2. `conductor_action=approve_all_pending` → `approval.harness_approved` → `approval.granted` → `operator.queued` (ASK_ONLY handoff, not Chase UI)
3. Deny path → `approval.harness_denied` → `approval.denied` → `pendingApprovals=0`
4. G7: `conductor_action=stop_autonomy` → `autonomy.stopped` harness (ASK_ONLY + queue cleared); subsequent policy **BLOCK**

**Honest limits:** Post-approve banking stays **handoff/ASK_ONLY** (no live bank app automation). Money dry-run on real banking UI remains G6.

## What this loop shipped

- Launcher harness extras: `approve_all_pending`, `deny_all_pending`, `stop_autonomy`
- AuditLedger logcat for approval/autonomy/operator.queued
- `device_g5_approval_test.js` + static invariants for harness hooks
- UI approve path records `approval.ui_approved` and normalizes stepId prefix

## Remaining to full OS test-complete

1. **G4-OEM:** Messages draft or Maps route live verify on real app UI  
2. **G3:** physical device voice  
3. **G8:** real LLM behind token service  
4. **G6:** money dry-run E2E without real funds (or richer bank agent surface)

## Verify

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
npm test
cd conductor-android-prototype
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
node device_smoke_test.js --strict
node device_live_g4_test.js
node device_g5_approval_test.js
```
