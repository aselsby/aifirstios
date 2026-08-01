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
| G4 Live a11y verify | **PASS (controlled + OEM Messages draft)** | See below |
| G5 Approval→execute | **PASS (control plane)** | See below |
| G6 Money safety | **PARTIAL** | Exact approval enforced; bank UI E2E not live |
| G7 Instant stop | **PASS (harness)** | `autonomy.stopped` + queue clear + ASK_ONLY |
| G8 Real model stream | **OPEN** | Transport still scaffolded |

### G4 evidence (controlled)

`ConductorAgentDemoActivity` + playbook `conductor_demo_live_draft`.

```text
operator.live_verified ...accessibility_live_tree:conductor_demo_live_draft:post_state_receipt
accessibility.queue_resolved ...conductor_demo_live_draft
```

Harness: `node device_live_g4_test.js`

### G4-OEM evidence (Google Messages)

`life_messaging` → `messages_draft_invite` on real `com.google.android.apps.messaging`:

1. `smsto:` deep link opens compose for recipient  
2. Live a11y finds unique editable compose field  
3. `set_text` body + post-state verify  

```text
operator.foreground_assist ...LAUNCHED:...messaging:android.intent.action.SENDTO
operator.live_verified operation_outbound_message_create_draft:accessibility_live_tree:messages_draft_invite:post_state_receipt
accessibility.queue_resolved operation_outbound_message_create_draft:messages_draft_invite
```

Harness: `node device_oem_g4_test.js` → `status: ready`, `gate: G4-OEM`

**Honest limits:** Draft-only (no send). Send still exact-approval + separate playbook. Maps route live verify still open.

### G5 + G7 evidence

Harness: `node device_g5_approval_test.js` → `status: ready`, `gate: G5+G7`.

- `approval.queued` → harness approve → `approval.granted` → `operator.queued` (banking stays ASK_ONLY handoff)
- Deny → `approval.denied` / `pendingApprovals=0`
- Stop → `autonomy.stopped` harness

## What recent loops shipped

- G5/G7 launcher harness actions + AuditLedger logcat
- G4-OEM: Messages `smsto` launch, editable-node finder, phone recipient parse
- Controlled G4 demo surface (prior loop)

## Remaining to full OS test-complete

1. **G4-OEM Maps:** route/search live verify  
2. **G3:** physical device voice  
3. **G8:** real LLM behind token service  
4. **G6:** money dry-run E2E without real funds  

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
node device_g5_approval_test.js
```
