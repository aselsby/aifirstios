# Conductor Action SDK

The Action SDK is the structured alternative to brittle app UI automation.

Apps expose typed actions. Conductor reads the manifest, evaluates user policy, asks for approval when needed, executes allowed actions, verifies the result, and records an audit event.

## Why This Exists

The OS can operate apps through several layers:

- Direct APIs and OAuth.
- Android intents and platform providers.
- App Intents or Shortcuts on iOS.
- Browser or accessibility operation where necessary.

The long-term scalable layer is structured app actions. This package prototypes that contract.

## Action Manifest Shape

Each action declares:

- `id`: stable action id.
- `app`: app or connector name.
- `actionType`: policy classification string.
- `description`: human-readable purpose.
- `risk`: low, medium, or high.
- `externalSideEffect`: whether execution changes external state.
- `approval`: never, always, or policy.
- `inputSchema`: required fields.
- `outputSchema`: expected result fields.
- `verification`: how the runtime verifies completion.

## Sample Apps

Included sample actions:

- `calendar.free_busy`
- `calendar.create_hold`
- `weather.hourly`
- `facebook_events.search_nearby`
- `contacts.search`
- `messages.create_draft`
- `messages.send`
- `facebook.post`
- `maps.open_route`

## Run

```bash
npm test
npm run demo
```

The tests prove that sensitive actions such as `messages.send` and `facebook.post` require exact single-use approval, purchases/security/data-deletion actions are blocked in the MVP, and unknown or malformed actions cannot execute.
