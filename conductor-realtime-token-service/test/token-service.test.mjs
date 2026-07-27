import test from "node:test";
import assert from "node:assert/strict";
import {
  BearerAuthVerifier,
  createRealtimeTokenHttpHandler,
  MAX_TTL_SECONDS,
  REALTIME_SCOPE,
  RealtimeTokenService
} from "../src/token-service.mjs";

const now = () => new Date("2026-07-27T10:45:00-05:00");

test("issues scoped ephemeral token for authenticated voice handoff", () => {
  const service = new RealtimeTokenService({ now });
  const response = service.createSessionToken({
    user: { id: "user_001", authenticated: true },
    intentHint: "outdoor_activity",
    autonomyMode: "DRAFT_ONLY",
    ttlSeconds: 120
  });

  assert.equal(response.status, "issued");
  assert.equal(response.token.scope, REALTIME_SCOPE);
  assert.equal(response.token.value.startsWith("rt_"), true);
  assert.equal(response.userContext.autonomyMode, "DRAFT_ONLY");
  assert.equal(response.token.expiresAtIso, "2026-07-27T15:47:00.000Z");
});

test("caps token ttl to five minutes", () => {
  const service = new RealtimeTokenService({ now });
  const response = service.createSessionToken({
    user: { id: "user_001", authenticated: true },
    intentHint: "outdoor_activity",
    autonomyMode: "LOW_RISK_AUTO",
    ttlSeconds: MAX_TTL_SECONDS + 999
  });

  assert.equal(response.status, "issued");
  assert.equal(response.token.expiresAtIso, "2026-07-27T15:50:00.000Z");
});

test("denies unauthenticated token requests", () => {
  const service = new RealtimeTokenService({ now });
  const response = service.createSessionToken({
    user: { id: "user_001", authenticated: false },
    intentHint: "outdoor_activity",
    autonomyMode: "DRAFT_ONLY"
  });

  assert.deepEqual(response, {
    status: "denied",
    reason: "authentication_required"
  });
});

test("denies scopes beyond voice intent handoff", () => {
  const service = new RealtimeTokenService({ now });
  const response = service.createSessionToken({
    user: { id: "user_001", authenticated: true },
    intentHint: "outdoor_activity",
    autonomyMode: "DRAFT_ONLY",
    scope: "tools:execute"
  });

  assert.equal(response.status, "denied");
  assert.equal(response.reason, "scope_not_allowed");
});

test("does not expose provider secrets", () => {
  const service = new RealtimeTokenService({ now });
  const response = service.createSessionToken({
    user: { id: "user_001", authenticated: true },
    intentHint: "general_assistant",
    autonomyMode: "ASK_ONLY"
  });

  const serialized = JSON.stringify(response);
  assert.equal(serialized.includes("local-provider-secret"), false);
  assert.equal(serialized.includes("OPENAI_API_KEY"), false);
});

test("audits issued and denied requests", () => {
  const service = new RealtimeTokenService({ now });
  service.createSessionToken({
    user: { id: "user_001", authenticated: true },
    intentHint: "outdoor_activity",
    autonomyMode: "DRAFT_ONLY"
  });
  service.createSessionToken({
    user: { id: "user_001", authenticated: true },
    intentHint: "public_post",
    autonomyMode: "DRAFT_ONLY"
  });

  assert.equal(service.auditEvents().some((event) => event.type === "realtime_token.issued"), true);
  assert.equal(service.auditEvents().some((event) => event.type === "realtime_token.denied"), true);
});

test("bearer auth verifier accepts mobile user tokens", () => {
  const authVerifier = new BearerAuthVerifier();
  const result = authVerifier.verify("Bearer mobile-user:user_001");

  assert.equal(result.authenticated, true);
  assert.equal(result.user.id, "user_001");
});

test("http handler issues tokens from bearer identity and denies disallowed requests", async () => {
  const service = new RealtimeTokenService({ now });
  const handler = createRealtimeTokenHttpHandler({ service });

  const issued = await callHandler(handler, {
    method: "POST",
    url: "/realtime/session-token",
    headers: {
      authorization: "Bearer mobile-user:user_001"
    },
    body: {
      user: { id: "spoofed_user", authenticated: true },
      intentHint: "outdoor_activity",
      autonomyMode: "DRAFT_ONLY"
    }
  });
  const denied = await callHandler(handler, {
    method: "POST",
    url: "/realtime/session-token",
    headers: {
      authorization: "Bearer mobile-user:user_001"
    },
    body: {
      intentHint: "outdoor_activity",
      autonomyMode: "DRAFT_ONLY",
      scope: "tools:execute"
    }
  });

  assert.equal(issued.statusCode, 200);
  assert.equal(issued.body.status, "issued");
  assert.equal(issued.body.userContext.userId, "user_001");
  assert.equal(denied.statusCode, 403);
  assert.equal(denied.body.reason, "scope_not_allowed");
});

test("http handler denies missing or invalid bearer auth", async () => {
  const service = new RealtimeTokenService({ now });
  const handler = createRealtimeTokenHttpHandler({ service });

  const missing = await callHandler(handler, {
    method: "POST",
    url: "/realtime/session-token",
    body: {
      intentHint: "outdoor_activity",
      autonomyMode: "DRAFT_ONLY"
    }
  });
  const invalid = await callHandler(handler, {
    method: "POST",
    url: "/realtime/session-token",
    headers: {
      authorization: "Bearer nope"
    },
    body: {
      intentHint: "outdoor_activity",
      autonomyMode: "DRAFT_ONLY"
    }
  });

  assert.equal(missing.statusCode, 401);
  assert.equal(missing.body.reason, "authentication_required");
  assert.equal(invalid.statusCode, 401);
  assert.equal(invalid.body.reason, "invalid_auth_token");
});

async function callHandler(handler, { method, url, headers = {}, body }) {
  const encoded = Buffer.from(JSON.stringify(body));
  const req = {
    method,
    url,
    headers,
    async *[Symbol.asyncIterator]() {
      yield encoded;
    }
  };
  const res = {
    statusCode: 200,
    headers: {},
    chunks: [],
    writeHead(statusCode, headers) {
      this.statusCode = statusCode;
      this.headers = headers;
    },
    end(chunk) {
      if (chunk) this.chunks.push(Buffer.from(chunk));
    }
  };

  await handler(req, res);
  return {
    statusCode: res.statusCode,
    headers: res.headers,
    body: JSON.parse(Buffer.concat(res.chunks).toString("utf8"))
  };
}
