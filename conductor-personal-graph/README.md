# Conductor Personal Graph

This package prototypes the local-first data layer Conductor needs to reason across apps safely.

It models:

- Facts from Calendar, Weather, Facebook-style Events, Contacts, Messages, Maps, and memory.
- Source app and account provenance.
- Sensitivity.
- Allowed purposes.
- Expiration and retention.
- Revocable grants.
- App-agent grants that bound which OS agent or app surface can use which sources for a purpose.
- Query-time policy checks.
- Redacted snapshots for model input.

## Why This Exists

The OS must be able to answer intent like:

> "What should I do outside this afternoon?"

That requires context from multiple apps: calendar, weather, location, event sources, contacts, messages, preferences, and sometimes maps. The graph gives Conductor a safe local workspace for that context without turning every task into a broad data grab. A source grant says Conductor may read a source for a purpose; an app-agent grant says a specific agent surface may use those sources for that purpose.

## Run

```bash
npm test
npm run demo
```

The demo builds the outdoor planning context and returns a model-safe snapshot. Use `modelSnapshotForAppAgent(...)` when data will be supplied to an agent; it denies snapshots unless the app-agent, purpose, and requested sources are all covered by a live grant.

## Real Android Storage Target

This in-memory package should become:

- Room entities for structured graph records.
- SQLCipher-backed local database for sensitive records.
- Android Keystore-backed encryption keys.
- Optional end-to-end encrypted cloud sync.
- Per-source revocation and deletion.
