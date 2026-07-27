# Conductor Android Prototype

Android-first native surface for **Conductor OS**: an AI launcher and agent runtime that gathers cross-app context, plans goals, enforces autonomy policy, pauses sensitive actions for approval, and operates apps through typed tools and AccessibilityService playbooks.

This is a substantial control-plane prototype, not a finished OS image. Production app operations **queue for live AccessibilityService verification** — recording/simulation bridges are test-only and labeled as such. Wall-clock time is provided by `SystemClock`.

It mirrors safety ideas from the JavaScript packages while owning the product launcher, voice, durability, and live operator path.

## What This Contains

- Launcher manifest and home-screen entry point.
- Native account-session boundary for the logged-in mobile user and realtime bearer auth.
- Jetpack Compose-style launcher screen for voice, global autonomy, per-app autonomy, instant autonomy stop, data access grants, context, plan, approvals, app handoff queue, and audit.
- Launcher app receipts section that turns durable live-operation audit events into reviewable verified app-operation receipts.
- Launcher operation timeline section that turns each voice task into a durable job view across planning, policy, approval, app handoffs, source scope, and verification.
- Launcher plan review rows show each intended tool, action type, risk, side-effect status, required data scope, concrete inputs, policy reason, and execution status before app operation.
- Voice session service, controller, runtime microphone permission flow, Android speech-capture adapter, Android text-to-speech output adapter, realtime model transport boundary, bearer-authenticated HTTP ephemeral session-token provider with recording fallback, and handoff runner for push-to-talk state, transcript streaming, spoken assistant deltas, interruption, and intent handoff.
- Voice handoff classifies final transcripts into app-task, outdoor-activity, or general mobile intent types before model response and runtime planning.
- Voice control commands can set global autonomy or require/clear exact approval for supported app actions such as message sends and public posts.
- Runtime mobile-intent routing sends app-task voice handoffs to a taught-app `tasks.add` plan, outdoor handoffs to cross-app activity planning, and general mobile intents to a safe no-side-effect answer path instead of inventing outdoor context.
- Runtime general mobile intent routing can target a discovery-taught app-agent playbook when one exists, while still falling back to the safe answer path when no capable app route has been granted.
- Classified mobile intent type persists with each task and is visible in the launcher, so the user can audit which route Conductor chose after restart.
- Accessibility service boundary for user-enabled, logged-in, whitelist-only app operation.
- Live app-operation bridge boundary: recording mode keeps deterministic tests, while the AccessibilityService bridge validates account proof, selectors, and post-state against the active window before resolving queued app work.
- AccessibilityService captures bounded foreground app discovery snapshots for user teaching, including visible label counts and account-proof candidates, without enabling execution for ungranted apps.
- Live app-operation execution can bring the target package to the foreground through an Android launcher boundary, then pauses in the app-operation queue until the AccessibilityService observes the requested package and verifies account proof.
- Live app-operation execution supports bounded recovery hints for changed app screens: a playbook may declare up to a few unique recovery labels, Conductor may click one, refresh the active tree, and retry the original target before handing off.
- Live app-operation execution materializes `input.*` selectors, performs explicit playbook `set_text` or `click` operations on a unique enabled visible node, refreshes the active window after each step, and verifies the expected post-state before continuing.
- Live app-operation execution checks the persisted `ASK_ONLY` stop state before and after each accessibility action, and system interruption drops autonomy to `ASK_ONLY` and clears queued app work.
- Native app-operation playbooks for Messages send, Calendar hold, Maps route, and exact-approved Facebook public post.
- Persisted custom app-operation playbooks with declared required inputs so additional apps can be added as user-granted agent surfaces.
- App-operation playbooks can declare invocation phrases so general voice requests route to the intended taught app-agent instead of the first available custom app.
- General voice requests with multiple matching app-agent playbooks produce a clarification step with candidate app/action options instead of silently choosing one.
- Voice follow-up can answer that app-agent clarification, selecting only from the audited candidate list and replaying the original request with the chosen playbook.
- App-agent onboarding service that validates a user-taught playbook, dry-runs it against observed app UI labels, persists it, and creates the logged-in app session plus bounded app-agent source grants needed for routing and source authorization.
- Launcher teach-app control lets the user author an observed app-agent playbook draft with action type, risk/exact-approval posture, one or more input keys bound to observed field labels, observed-label chips for visible account proof, UI targets, and recovery labels, known-source chips for required cross-app scope, text target/verifier label, and optional click target/verifier label before saving it through the signed-in account, observed UI dry-run, bounded app-agent source grant, and durable record-store path required for user-taught apps.
- Planner and tool registry support generic taught-app actions: task/reminder goals can emit a `tasks.add` app-agent step, and `ToolRegistry` routes any current stored playbook action through app operation rather than a hardcoded built-in list.
- Native app-operation session store and queue for per-app login state, autonomy grants, and handoffs that require user action.
- App-operation sessions include persisted login proof, so a `LOGGED_IN` app-agent grant is not just a boolean flag.
- Native app-agent routing that chooses a logged-in capable app by action type, source grant, playbook grant, and per-app autonomy before operating UI.
- The outdoor plan's agent-capable steps, including draft invite, tentative calendar hold, route preview, send invite, and public post, route through app-agent playbooks before falling back to non-agent tool execution.
- Launcher app-autonomy controls for updating each logged-in app session independently from the global mode.
- Launcher app-autonomy controls show each app session's login proof before the user raises or lowers autonomy.
- App-agent autonomy is bounded by a per-session autonomous action budget that pauses unapproved app work until the user renews it.
- Launcher per-action approval controls let a trusted app session still require exact approval for selected action types such as message sends or public posts.
- Launcher app-capability preflight shows each operable playbook's action type, per-playbook grant state, required inputs, required data sources, live step operations, risk, exact-approval posture, and current readiness before the user raises autonomy.
- Launcher app-agent revoke control for disabling a logged-in app surface after onboarding or grant.
- Instant stop control that sets global and per-app autonomy to `ASK_ONLY`, cancels voice, clears queued app operations, and audits the stop.
- Launcher app handoff section that shows missing login, missing grants, and required user action for queued app operations, with a grant-and-retry control for app playbook sessions.
- App handoffs expose per-app autonomy context, including the session mode, playbook risk, and exact-approval status when autonomy blocks execution.
- App handoffs persist a specific primary action label so the launcher distinguishes login, grant, input review, exact approval, and autonomy changes.
- App handoffs show required source grants, blocked base data sources, concrete app inputs, and exact outbound content before the user grants, reviews, or retries.
- App handoffs carry created/expiry timestamps and are pruned before launcher display, UI retry, or accessibility dispatch, so stale app work cannot revive after context changes.
- Voice follow-up can repair a queued missing-input handoff by filling only the playbook's declared input keys, then retrying the original operation through the normal login, source, autonomy, and approval checks.
- Voice follow-up can revise queued exact outbound content, clear any stale approval receipt, and keep the app operation paused for a fresh exact approval.
- Voice and launcher controls can cancel pending app handoffs by active signed-in user, including targeted commands such as cancel the Facebook post or cancel the message.
- Handoff acceptance preserves existing login proof and does not mint app-session grants for input review or exact-content approval handoffs.
- Kotlin runtime domain models.
- Native record-store contract for account sessions, tasks, operation timelines, graph grants/facts, approval decisions, autonomy mode, connector accounts, app-operation sessions, app-agent discovery snapshots, queued app handoffs, and audit events.
- Encrypted Android record-store implementation for retained OS memory, plus an in-memory test store and SQLCipher-oriented schema plan for the production database.
- Native personal graph store with purpose-scoped source grants, app-agent grants, and redacted summaries.
- Conductor first-party memory can retain user activity preferences from voice utterances as purpose-scoped graph facts and use them in outdoor planning.
- Cross-app context items carry auditable source provenance: fact id, account id, purpose, freshness, base grant, and app-agent grant.
- Launcher data-access controls for revoking and restoring purpose-scoped source grants.
- Launcher agent-data controls for revoking and restoring which app-agent surfaces may use source grants for a purpose.
- Native connector contracts and mock outdoor connectors for calendar, weather, Facebook-style events, contacts, and maps.
- Connector runtime restores persisted connected accounts before reading cross-app sources, so OS memory survives launcher/runtime restart.
- Launcher data-access controls show connector account source, account id, allowed purposes, and connected/missing status without exposing credential handles.
- Deterministic autonomy policy engine.
- Native policy and approval-decision stores backed by Android `SharedPreferences`.
- Context broker that hydrates graph facts through connector contracts.
- Context broker reuses fresh retained graph context before rehydrating connectors, while preserving source grants, expiry filters, and app-agent grants.
- Launcher source-freshness ledger shows whether each retained cross-app source is fresh, expired, revoked, missing a grant, or blocked for the current planning purpose.
- Launcher source refresh rehydrates only the selected source through the connector runtime, preserving revoked-grant denial and connector credential boundaries.
- Outdoor activity planner matching the hero workflow.
- Android intent planner for draft messages, tentative calendar holds, and Maps route previews.
- Android intent launcher boundary with real `Context.startActivity` implementation and recording test launcher.
- Tool executor with approval-receipt-gated execution and AccessibilityService-style operation receipts.
- Tool executor records a pre-dispatch app-operation preview with package, playbook, account proof, source scope, concrete inputs, exact-approval receipt, and autonomy decision before touching the live app bridge.
- Tool executor dispatches final app operation through the live bridge and consumes exact approval receipts only after bridge verification.
- Native build/device preflight gate for Gradle, Android SDK, ADB, emulator, manifest, and install verification readiness.
- ADB device smoke gate for installed package, launcher resolution, permissions, accessibility service declaration, and home intent visibility.
- Static build-config gate for Compose compiler plugin, Java/Kotlin targets, manifest resources, and accessibility XML references.
- Verification script for required scaffold files and safety invariants.

## Important Product Boundary

This is not a full AOSP fork and not a finished Android app. It is the native implementation scaffold for the first build:

> "Find me something outdoors to do this afternoon using my calendar, weather, and nearby events. Draft an invite to Maya, but ask before sending."

## Build Direction

Recommended next implementation steps:

1. Add a real Android Gradle wrapper and verify against the local Android SDK.
2. Verify the Compose launcher against a local Android SDK and emulator.
3. Replace the scaffold mobile bearer-token provider with production account-session auth, backend deployment, and device-safe network policy.
4. Replace mock connector implementations with Calendar, Location, Weather, Contacts, Maps, and event-source providers.
5. Replace the encrypted `SharedPreferences` record-store bridge with a Room/SQLCipher implementation of `AndroidRecordStoreSchema`.
6. Compile/install the app and verify launched Messages, Calendar, and Maps screens on emulator/device.
7. Harden app-operation sessions with Android account/session proof and production persisted grants.
8. Connect live app-discovery snapshots to a full user-authored playbook teaching flow, then verify selectors on emulator/device.

## Safety Invariants

- The model never directly executes tools.
- Voice input must produce an auditable final transcript before task handoff.
- Before execution, the launcher must expose each planned action's tool, risk, external side-effect status, required source scope, concrete inputs, policy decision reason, and current execution status so the user can understand the intent-to-action translation.
- Each task must persist a durable operation timeline that ties planned steps to policy decisions, approval ids, app packages/playbooks, queued handoffs, source scope, and verification summaries, and the launcher must surface that timeline after restart.
- Voice handoff must preserve the classified mobile intent type, so task/reminder requests can be distinguished from outdoor planning and general mobile intents.
- Voice OS-control utterances such as stop autonomy, ask only, trusted auto, always ask before posting, or do not ask before sending messages must update durable global autonomy or per-action approval overrides directly, audit the change, and avoid routing the utterance as app-operation work.
- Runtime planning must honor the classified intent type: app-task handoffs route to app-agent task actions, outdoor handoffs use cross-app activity planning, and general intents use no-side-effect answer generation until a discovery-taught app-agent route exists.
- Persisted task records must retain the classified intent type; the launcher must surface it alongside the transcript.
- Microphone permission must be granted before speech capture starts; denial is audited and interrupts voice.
- Assistant responses stream to native speech output and can be cancelled on interruption or transport failure.
- Speech-recognition errors interrupt the voice session instead of producing unverified intents.
- Realtime model responses can stream assistant deltas, but model output remains intent-only and cannot execute tools directly.
- Realtime sessions require ephemeral server-issued tokens scoped to voice intent handoff.
- Android requests realtime tokens through HTTP with `Authorization: Bearer ...` and falls back to recording tokens only in the scaffold path.
- Realtime voice auth, workflow task identity, and app-operation grants derive from the same logged-in account session.
- All proposed steps pass through `PolicyEngine`.
- `outbound_message.send` requires a persisted matching approval id before execution.
- Exact-approval app playbooks require an approval receipt whose action type and exact content match the requested app operation.
- Exact-approval cards must show the live app-agent click/write steps that approval would unlock.
- Public or high-risk user-taught app playbooks cannot opt out of exact approval; onboarding rejects them and execution blocks any already-stored unsafe definition.
- Runtime approval decisions must persist the approved action type and exact content; id-only or mismatched approvals must be rejected and requeued before side effects.
- Exact-approval receipts are single-use, stored through `ConductorRecordStore`, and must be consumed after verified app operation, blocking replay before any second side effect even after process restart.
- Account sessions, tasks, operation timelines, graph grants/facts, connector accounts, app-operation sessions, queued handoffs, approval decisions, autonomy mode, and audit events must pass through `ConductorRecordStore`.
- `public_post.create` requires exact approval plus a logged-in app session; purchases, data deletion, and account security changes are blocked in the MVP.
- Accessibility operation is user-enabled and whitelisted.
- App-agent discovery may observe bounded foreground labels for teaching, but must not grant app sessions, playbooks, source grants, or execution rights by itself.
- App-agent discovery must skip password and editable field text, bound retained labels, and persist only encrypted label counts plus account-proof candidates.
- AccessibilityService must use the current encrypted record store registry, inspect the active window, and resolve queued live operations only after bridge verification.
- If the requested app is not the active package, the live bridge must use a foreground-launch boundary and queue the operation as `Run in app`; queued execution may continue only after AccessibilityService observes the requested package and revalidates account proof, selectors, stop state, and post-state.
- Live recovery from changed app screens must be playbook-declared, visible in launcher step previews, verified as unique during onboarding, limited to one recovery click before retrying the original target, and audited as attempted, succeeded, failed, or handed off.
- Built-in and user-taught app-agent playbooks must declare a concrete visible account proof label so live execution can prove the expected logged-in account before any click or text entry.
- App-agent playbook steps must declare supported operation semantics; `set_text` steps must name a declared input key before live accessibility execution writes user content.
- App operation requires a logged-in app session plus an allowed playbook grant for that package.
- Logged-in app-operation sessions must include non-empty login proof, persisted with the session, before routing or execution can proceed.
- The launcher must surface app-session login proof so the user can audit why an app is eligible for autonomous operation.
- App-agent routes must resolve a capable logged-in app surface before app operation, and missing login, source grants, or playbook grants must become user handoffs.
- App-agent playbooks must declare durable source requirements, and the launcher must compute readiness from live login state, base graph grants, app-agent source grants, risk, and exact-approval requirements.
- The launcher must surface app-agent playbook step operations so the user can audit what Conductor may click or write before raising autonomy.
- The launcher must let the user enable or disable an individual app-agent playbook without broadening login proof, source grants, or other playbook grants.
- App-agent routes and queued handoff acceptance require a signed-in Conductor account, and the queued operation user must still match the active account before any app session or data grant is created.
- Draft invites, calendar holds, maps routes, message sends, and public posts must preserve their required source grants when routed through app-agent operation.
- Required source ids for app operation must be authorized by both a live base data grant and a live app-agent data grant; a stored app session alone is not enough.
- App-operation sessions enforce their own autonomy cap at execution time, including ASK_ONLY handoff and LOW_RISK_AUTO limits.
- App-operation sessions must persist and display the remaining autonomous action budget; unapproved autonomous app work must decrement it only after verified execution, and a depleted budget must queue a renewal handoff without creating sessions, broadening playbook grants, or broadening source grants.
- App-operation sessions must persist per-action approval overrides; when an override matches the playbook action type, execution must require a content-bound approval receipt even if the session autonomy mode would otherwise allow the app operation.
- App-operation autonomy handoffs must persist and display the mode/risk/exact-approval context that caused the pause.
- Handoff buttons must describe the specific required action instead of using a generic grant label for every pause.
- Handoff consent must show the operation's required source scope, blocked base data sources, exact input content, and live click/write step operations, not only the package and playbook id.
- Queued app handoffs must expire durably and be removed before display, retry, or live AccessibilityService dispatch.
- Missing-input handoffs must list the absent slots, accept voice follow-up only for the active signed-in user's queued operation, and retry without creating app sessions or broadening grants.
- Exact-content handoffs must accept voice revisions only for the active signed-in user's queued operation, update the queued request content, clear stale approval receipts, and keep the handoff pending for renewed approval instead of executing immediately.
- Pending app handoffs must be cancellable by voice or touch only for the active signed-in user's queued operations; cancellation resolves the queued request and records whether it was applied or blocked.
- Review-input and exact-approval handoffs must not create or replace logged-in app sessions; only login, grant, renewal, or autonomy handoffs may update the session.
- Exact-approval handoffs must retry with a receipt bound to the queued playbook action type and exact body, then clear the queue only after verified execution.
- Queued app-operation retries must replace any pending item with the same request id.
- App-operation execution must emit a durable preview before live dispatch so the launcher can show what app, account, sources, inputs, live step operations, approval, and autonomy state will be used.
- `ASK_ONLY` blocks app-agent model context snapshots before connector hydration and app actions.
- User preference memory must be stored as a purpose-scoped graph source with base and app-agent grants, and outdoor planning may use it only as optional context rather than as a required connector.
- The launcher lets the user lower or raise each app session's autonomy grant, and updates are audited.
- Revoked app-agent sessions must not route or operate, even when their playbooks remain stored.
- App-operation session revocation must be persisted with the session record so a revoked app agent cannot be restored by storage reload.
- Expired app-agent sessions must not route or operate; they queue a renewal handoff before any app action.
- Accepting a login, grant, or renewal handoff must set a fresh bounded app-session expiry instead of preserving an expired timestamp.
- The launcher must provide a one-tap autonomy stop that immediately drops global and app-session autonomy to `ASK_ONLY`.
- AccessibilityService must honor the same stop state during live execution so app actions cannot continue after the user stops autonomy or the system interrupts the service.
- Missing login, missing app grants, or ambiguous inputs enqueue the operation for user handoff instead of silently executing.
- Revoked or expired app-agent source grants enqueue a restore-data-access handoff; accepting it restores only the queued required source scope with a fresh bounded expiry, retries the operation, and must not create, renew, or broaden an app session. If a known base data grant for that source is revoked or expired, the handoff may restore that same base grant with a bounded expiry first; if no base grant exists, it must stop and require source connection.
- The launcher must expose queued app handoffs so the user can grant an app playbook session, resolve the queue item, and retry through policy.
- Accepting a login, grant, renewal, or autonomy handoff must retry the queued app operation and clear it only after verified execution; if more approval or review is needed, the queue remains pending.
- Queued app handoffs must persist required source ids so grant-and-retry preserves the original cross-app source scope.
- App-operation playbooks block unlisted packages, ask for handoff on ambiguity, and require post-action verification receipts.
- Custom app-operation playbooks must be stored through `ConductorRecordStore`, merged with built-ins, declare required inputs, and be granted per package before execution.
- Custom app-operation playbooks must persist invocation phrases, expose them in the launcher capability matrix, and use them to route general mobile voice requests to the matching taught app playbook before falling back to a generic observed-app route.
- If multiple playbooks match a general mobile voice request, Conductor must audit the ambiguity and return a no-side-effect clarification plan instead of operating an app.
- Clarification follow-up must resolve only against the previous ambiguous candidate list, preserve the original general mobile request text, and route through normal app-agent policy, grants, and approval checks with a selected playbook id.
- User-taught app-agent playbooks must validate required inputs, steps, exact-body approval needs, a unique visible signed-in account proof label, and observed UI dry-run proof with no missing or ambiguous targets before Conductor stores or routes them.
- Launcher app teaching must call `AppAgentOnboarding`, require an active signed-in Conductor account, let the user author action type, risk label, primary input key, additional observed-label-to-input-key field bindings, account proof label, required source scope, text target/verifier label, optional recovery labels, and optional click target/verifier label from a stored app-agent discovery snapshot, block unknown source ids and unknown field labels, prove those labels through `observedTreeLabelCounts`, force public/high-risk drafted actions into exact approval semantics, and persist the resulting multi-step playbook, session, and bounded app-agent source grant through `ConductorRecordStore`.
- App-operation routing must read the current stored playbook registry so newly taught app agents can route immediately after onboarding.
- User-taught app source scopes must become bounded `AppAgentGrant` records for the exact allowed source ids; a session-level source allowance alone must not satisfy source authorization.
- App-agent source authorization must match the active base source grant and the target app package's own `AppAgentGrant`; one app's source grant must not authorize another app.
- Restore-data-access handoffs must recreate or renew source grants for the queued app package and playbook, not for the launcher package or a generic Conductor agent.
- User-taught app onboarding must preserve an explicitly revoked app-agent source grant instead of silently restoring it.
- Tool execution must route any action type backed by the current stored playbook registry through app operation, including planner-emitted taught-app actions with explicit `__requiredSourceIds` source scope.
- Intent launch failures are captured as tool failures, not silent success.
- Safe Android intents can prefill app work, but external side effects still require policy and app-level verification.
- Every context access, policy decision, approval, tool call, and verification should be audited.
- Verified app-operation receipts from the live bridge and AccessibilityService queue resolution must be surfaced in the launcher from durable audit storage.
- Verified app-operation receipts must include package/source-scope provenance so the user can audit which app-agent used which cross-app sources.
- Connector credential handles stay inside the connector boundary.
- Connector account status in the launcher must expose only source, account id, purposes, and connected/missing status; credential handles must stay inside connector storage/runtime.
- Connected app-account handles must be restored from `ConductorRecordStore` before connector hydration.
- Personal graph queries must be purpose-scoped and redacted before model use.
- Personal graph facts must persist expiry metadata and expired facts must be withheld from model context.
- Personal graph grants and app-agent grants must persist expiry metadata and expired grants must not authorize model context.
- Fresh retained graph facts should satisfy cross-app planning after restart before connector reads, but only when every required source is still granted and unexpired.
- The launcher must surface cross-app source freshness from persisted graph facts and grants, so recommendations are auditable against retained calendar, weather, events, contacts, and maps context.
- Each context item shown to the user must preserve the source fact id, connected account id, allowed purpose, freshness status, base grant id, and app-agent grant id that authorized its use.
- Source refresh controls must hydrate only the selected connector request and must not bypass revoked graph grants or expose credential handles.
- Model context for an agent surface must pass through an app-agent grant for the requested purpose and sources.
- The launcher must show purpose-scoped data grants and let the user revoke or restore each source.
- The launcher must show app-agent data grants and let the user revoke or restore each agent surface's source access.
- Revoked source grants are authoritative: connector hydration must not recreate revoked grants or refresh facts for that purpose.

## Verify Scaffold

```bash
node verify_scaffold.js
node static_build_config_check.js
node static_kotlin_source_check.js
node native_preflight.js
node device_smoke_test.js
```

The verifier checks that the manifest, launcher, services, policy engine, runtime, and safety strings exist.
The static build-config check catches project-local Gradle/resource issues before a full Android build is available.
The Kotlin source check catches package/path drift, manifest targets, resource references, launcher callback wiring, and launcher state contract drift before a full Android build is available.
The native preflight reports whether this machine is ready for `./gradlew :app:assembleDebug`, `adb install`, and device/emulator checks. Use `node native_preflight.js --strict` on a configured Android build machine to fail CI when any native prerequisite is missing.
After installing a debug APK, use `node device_smoke_test.js --strict` to fail CI when the package, launcher, permissions, accessibility service, or HOME intent surface is not visible through ADB.
