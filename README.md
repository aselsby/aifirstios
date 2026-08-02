# Conductor OS (`aifirstios`)

**AI-first Android operating layer** — voice chat, multi-app context, user-controlled autonomy, apps as agents.

Repo: https://github.com/aselsby/aifirstios

## Product promise

You speak (or type) a goal:

> “What should I do outside this afternoon and draft an invite to Maya?”

Conductor:

1. Gathers **calendar** (device provider), **weather** (Open-Meteo), events, contacts, maps  
2. Plans an outdoor option under your autonomy mode  
3. Drafts an invite without sending  
4. **Requires exact approval** before message send / public post  
5. Queues live app operation for AccessibilityService (no fake verified receipts)  
6. Audits the whole path  

## Quick start

### Requirements

- Node.js ≥ 20  
- JDK 17  
- Android SDK (API 35) + platform-tools  

### Install & verify (all packages + static gates)

```bash
# Full local verify (includes optional device smoke when adb/device available)
npm test

# PR/CI-equivalent gates (JS packages + Android static checks; no device)
npm run test:ci
```

### Build the Android debug APK

```bash
cd conductor-android-prototype
# one-time: point Gradle at your SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device / emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p app.conductor.prototype 1
node device_smoke_test.js --strict
```

Enable **Conductor Accessibility Service** in system settings for live app operation.  
Grant **microphone**, **calendar**, **contacts**, and **location** when prompted.

## Monorepo map

| Path | Role |
|------|------|
| `conductor-android-prototype/` | Product surface (launcher, voice, policy, app operator) |
| `conductor-action-sdk/` | Action manifests + exact-approval policy |
| `conductor-app-operator/` | Synthetic a11y operator lab |
| `conductor-personal-graph/` | Purpose-scoped grants |
| `conductor-connectors/` | Connector boundary lab |
| `conductor-voice-runtime/` | Voice session state machine |
| `conductor-os-orchestrator/` | JS end-to-end composition |
| `conductor-evals/` | Scenario suite |
| `conductor-realtime-token-service/` | Ephemeral realtime tokens |
| `conductor-runtime-core/` | Monolithic outdoor workflow lab |
| `conductor-os-simulator/` | Browser demo |

## Life app subagents

See [LIFE_APP_AGENTS.md](LIFE_APP_AGENTS.md) and [AGENTS.md](AGENTS.md).

Domains: Messages, Calendar, Contacts, Maps, Gmail, Shopping (Amazon/Walmart/Target), Banking (Chase/BofA/PayPal/Wallet), Social, Browser.

Money-moving actions always require exact approval.

Demo intents via adb:

```bash
adb shell 'am start -n app.conductor.prototype/app.conductor.launcher.ConductorLauncherActivity --es conductor_intent life_shopping --es conductor_utterance "Search Amazon for headphones and add to cart"'
```

## Docs

- [FOUNDING.md](FOUNDING.md) — product decisions  
- [ROADMAP.md](ROADMAP.md) — phases  
- [PROGRESS_PLAN.md](PROGRESS_PLAN.md) — checkpoint status  

## Safety non-negotiables

- Model output is never authority to act  
- Sensitive side effects need exact approval  
- Production live ops queue through AccessibilityService  
- Recording/simulation bridges are labeled and test-only  
- Instant stop forces `ASK_ONLY` and clears app queues  
