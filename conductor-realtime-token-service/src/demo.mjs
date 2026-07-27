import { RealtimeTokenService } from "./token-service.mjs";

const service = new RealtimeTokenService({
  now: () => new Date("2026-07-27T10:45:00-05:00")
});

const response = service.createSessionToken({
  user: { id: "user_001", authenticated: true },
  intentHint: "outdoor_activity",
  autonomyMode: "DRAFT_ONLY",
  ttlSeconds: 120
});

console.log(JSON.stringify({ response, audit: service.auditEvents() }, null, 2));
