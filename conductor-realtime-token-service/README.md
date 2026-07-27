# Conductor Realtime Token Service

Backend contract for issuing short-lived realtime model session tokens to the Android voice runtime.

The mobile client must never carry provider API keys. It asks this service for an ephemeral token scoped to voice intent handoff, then uses that token with the realtime model transport.

## What This Proves

- Authenticated user identity is required.
- HTTP requests authenticate with `Authorization: Bearer ...`; body-supplied user claims are ignored at the network boundary.
- Tokens are scoped to `voice:intent_handoff`.
- Token TTL is capped.
- Autonomy mode is embedded as request context.
- Provider secrets are never returned to the device.
- Denied requests return structured errors.

## Run

```bash
npm test
npm run demo
npm start
```

HTTP endpoint:

```text
POST /realtime/session-token
Authorization: Bearer mobile-user:user_001
```

The `mobile-user:*` bearer verifier is a scaffold identity adapter. Production should swap `BearerAuthVerifier` for the platform's real logged-in session verifier, such as OIDC/JWT validation backed by the mobile account service.

## Production Boundary

This package does not call a real model provider. In production, `ProviderTokenIssuer` is the only component that should hold provider credentials. The Android app receives only the returned ephemeral session token.
