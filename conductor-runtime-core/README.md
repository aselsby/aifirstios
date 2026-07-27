# Conductor Runtime Core

This package is a dependency-free implementation scaffold for the Conductor OS agent runtime.

It proves the critical loop:

1. Create a user task.
2. Gather task-scoped cross-app context.
3. Generate a plan.
4. Evaluate deterministic autonomy policy.
5. Pause sensitive actions in an approval queue.
6. Execute approved or low-risk steps through typed tools.
7. Record an audit trail.

Run it:

```bash
npm test
npm start
```

## Current Workflow

The demo workflow models:

> "Find me something outdoors to do this afternoon and draft an invite to Maya."

Mock context sources:

- Calendar availability.
- Weather window.
- Nearby events, including a Facebook Events-style source.
- Contacts.

The plan ranks an outdoor event, creates a draft invite, and pauses `messages.send` until explicit approval. Public posts are allowed only as exact-approval actions; purchases, account-security changes, and data deletion remain blocked.

## Runtime Modules

- `src/domain.js`: enums and task/audit factories.
- `src/context-broker.js`: task-scoped context gathering.
- `src/mock-connectors.js`: mock calendar, weather, events, and contacts connectors.
- `src/planner.js`: outdoor activity planner.
- `src/policy-engine.js`: deterministic autonomy and approval rules.
- `src/executor.js`: policy-gated tool execution.
- `src/tool-registry.js`: typed tool adapters.
- `src/runtime.js`: end-to-end workflow orchestration.

## Android Mapping

This Node scaffold should become a shared runtime spec while the Android implementation is built natively.

Recommended Android module map:

| Runtime concept | Android implementation |
| --- | --- |
| `Task` | Room entity plus in-memory active task state |
| `ContextBroker` | Kotlin service coordinating Calendar, Location, Contacts, Weather, OAuth, and app connectors |
| `Planner` | Model orchestration client plus deterministic plan normalizer |
| `PolicyEngine` | Kotlin rules engine with local user autonomy settings |
| `Approval` | Compose approval queue and notification action |
| `Executor` | Tool dispatcher for APIs, Android intents, App Actions, browser, and AccessibilityService |
| `AuditLog` | SQLCipher-backed event ledger |
| `ToolRegistry` | Typed Kotlin interfaces for each connector/action |

## Android Services To Build First

1. `ConductorLauncherActivity`
   The AI-first home screen: voice button, current task, approvals, context cards, and escape hatch to apps.

2. `VoiceSessionService`
   Push-to-talk, streamed transcript, interruption, and task handoff.

3. `ContextBrokerService`
   Collects task-scoped context from calendar, weather, location, contacts, and event sources.

4. `AutonomyPolicyStore`
   Stores user mode and per-action rules: Ask, Draft, Low Risk, Trusted, and temporary session scopes.

5. `ApprovalQueue`
   Shows exact outbound content before sends/posts/bookings/purchases.

6. `ToolExecutionService`
   Runs typed tools with idempotency keys and verification.

7. `ConductorAccessibilityService`
   User-enabled, whitelist-only app operation adapter for Android UI flows.

8. `AuditLedger`
   Records every context access, policy decision, approval, tool call, and verification result.

## First Native Tool Interfaces

```kotlin
interface ConductorTool<I, O> {
    val name: String
    val actionType: String
    val risk: Risk
    suspend fun execute(input: I, task: Task): ToolResult<O>
    suspend fun verify(result: ToolResult<O>): Verification
}
```

Initial tools:

- `calendar.free_busy`
- `weather.hourly`
- `location.current`
- `events.search_nearby`
- `contacts.search`
- `messages.create_draft`
- `messages.send`
- `calendar.create_hold`
- `maps.open_route`

## Safety Invariant

The policy engine owns the final action decision.

The model may propose:

- An action.
- A risk label.
- Suggested approval copy.
- A preferred tool.

The deterministic runtime must decide:

- allow
- require approval
- block

No app operation should bypass this check.

## Verified Behavior

The tests assert that:

- Draft creation is allowed in Draft Only mode.
- Message sending requires approval.
- Public posts require exact approval, while purchases and destructive account actions remain blocked.
- Approved outbound actions are rejected if the returned approval does not match the exact queued content.
- Ask Only blocks external action.
- The outdoor workflow gathers calendar, weather, events, and contacts context.
- `send_invite` is queued before execution.
- Approved sends are executed and verified.
- Audit records context, policy, approval, execution, and completion events.
