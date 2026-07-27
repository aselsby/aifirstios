# Conductor Connectors

This package prototypes the connector layer that replaces seeded demo data with scoped app/provider reads.

It models:

- Connector registration for Calendar, Weather, Facebook-style Events, Contacts, Messages, and Maps.
- Opaque credential handles.
- Purpose-scoped read requests.
- Normalized facts for the Personal Graph.
- Grants attached to sources/accounts/purposes.
- Audit events that record reads without exposing tokens.

## Why This Exists

Conductor needs to reason across multiple logged-in apps, but the planner should not touch credentials or raw provider payloads. Connectors are the boundary:

Provider/API data -> normalized facts -> personal graph -> model-safe context snapshot.

## Run

```bash
npm test
npm run demo
```

The demo reads the outdoor-planning sources and hydrates a Personal Graph snapshot.
