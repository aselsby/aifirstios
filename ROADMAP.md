# Conductor OS Roadmap

## Now — Honesty Sprint (active)

Make the prototype trustworthy as a foundation for a real product.

- [x] Founding product decisions locked (`FOUNDING.md`)
- [x] Wall-clock time for expiry, freshness, sessions, handoffs (`SystemClock`)
- [x] Production app ops queue to AccessibilityService (no false verified receipts)
- [x] Recording bridge labeled as simulation only
- [x] App-skills launcher surface + static invariants green
- [x] One-command monorepo verification (`npm test`)

## Next — Device Alpha

Ship something installable that demonstrates the hero flow on a phone/emulator.

1. [x] Gradle wrapper + assembleDebug on a machine with Android SDK  
2. [x] Real mic permission + speech capture loop (AndroidSpeechCapture/TTS)  
3. [ ] Accessibility enablement UX (service declared; user still enables in Settings)  
4. [ ] Device smoke: package, HOME, a11y service, RECORD_AUDIO (needs connected device)  
5. [x] Outdoor flow with live calendar/weather + queue → a11y handoff UX  
6. [x] Exact approval cards for message send / public post  


## Then — Live Context

Replace mocks where the hero flow needs truth.

1. Google Calendar free/busy (OAuth or device Calendar provider)  
2. Weather API behind credential vault  
3. One events source (Facebook Graph or web-mediated search)  
4. Contacts picker without dumping the full book into the model  
5. Purpose-scoped grant UI remains authoritative  

## Then — Live Agent Apps

1. Harden Messages / Calendar / Maps / Facebook playbooks against real UI trees  
2. Teach-app onboarding with multi-field bindings  
3. Skill-level grants (enable/disable whole app agent)  
4. Per-app autonomy budgets that feel understandable  

## Later — OS Graduation

1. Default assistant registration  
2. Notification listener for task continuity  
3. Partner Action SDK for apps that want first-class agent surfaces  
4. Room/SQLCipher production store  
5. iOS companion hub (intents/shortcuts only)  
6. OEM / AOSP path only after task-success evidence  

## Exit criteria by phase

| Phase | Exit criteria |
|-------|----------------|
| Honesty | Static gates green; production path cannot mark simulated ops as live-verified |
| Device Alpha | Debug APK installs; voice transcript handoff; approval queue visible; a11y declared |
| Live Context | Outdoor recommendation changes with real calendar/weather |
| Live Agent Apps | One third-party app flow succeeds with post-state verify on a real device |
| OS Graduation | Users keep Conductor as home and complete multi-app tasks weekly |
