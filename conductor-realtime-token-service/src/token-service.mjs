import crypto from "node:crypto";

export const REALTIME_SCOPE = "voice:intent_handoff";
export const ALLOWED_INTENTS = new Set(["outdoor_activity", "general_assistant"]);
export const ALLOWED_AUTONOMY_MODES = new Set(["ASK_ONLY", "DRAFT_ONLY", "LOW_RISK_AUTO", "TRUSTED_AUTO"]);
export const MAX_TTL_SECONDS = 300;

export class BearerAuthVerifier {
  constructor({ tokenPrefix = "mobile-user:" } = {}) {
    this.tokenPrefix = tokenPrefix;
  }

  verify(authorizationHeader = "") {
    const [scheme, token] = authorizationHeader.trim().split(/\s+/, 2);
    if (scheme !== "Bearer" || !token) {
      return { authenticated: false, reason: "authentication_required" };
    }
    if (!token.startsWith(this.tokenPrefix)) {
      return { authenticated: false, reason: "invalid_auth_token" };
    }

    const userId = token.slice(this.tokenPrefix.length).trim();
    if (!/^[a-zA-Z0-9_-]{3,64}$/.test(userId)) {
      return { authenticated: false, reason: "invalid_auth_token" };
    }

    return {
      authenticated: true,
      user: {
        id: userId,
        authenticated: true
      }
    };
  }
}

export class ProviderTokenIssuer {
  constructor({ now = () => new Date(), secret = "local-provider-secret" } = {}) {
    this.now = now;
    this.secret = secret;
  }

  issue({ userId, intentHint, autonomyMode, scope = REALTIME_SCOPE, ttlSeconds = MAX_TTL_SECONDS }) {
    const expiresAt = new Date(this.now().getTime() + Math.min(ttlSeconds, MAX_TTL_SECONDS) * 1000);
    const nonce = crypto.randomBytes(12).toString("base64url");
    const material = `${userId}:${intentHint}:${autonomyMode}:${scope}:${expiresAt.toISOString()}:${nonce}`;
    const signature = crypto.createHmac("sha256", this.secret).update(material).digest("base64url");

    return {
      value: `rt_${nonce}.${signature}`,
      expiresAtIso: expiresAt.toISOString(),
      model: "realtime-mobile-os-preview",
      scope
    };
  }
}

export class RealtimeTokenService {
  constructor({
    now = () => new Date(),
    providerTokenIssuer = new ProviderTokenIssuer({ now }),
    audit = []
  } = {}) {
    this.now = now;
    this.providerTokenIssuer = providerTokenIssuer;
    this.audit = audit;
  }

  createSessionToken(request) {
    const validation = this.#validate(request);
    if (!validation.ok) {
      this.#record("realtime_token.denied", validation.reason);
      return {
        status: "denied",
        reason: validation.reason
      };
    }

    const ttlSeconds = Math.min(request.ttlSeconds ?? MAX_TTL_SECONDS, MAX_TTL_SECONDS);
    const token = this.providerTokenIssuer.issue({
      userId: request.user.id,
      intentHint: request.intentHint,
      autonomyMode: request.autonomyMode,
      scope: REALTIME_SCOPE,
      ttlSeconds
    });

    this.#record("realtime_token.issued", `${request.user.id}:${request.intentHint}:${request.autonomyMode}:${ttlSeconds}`);
    return {
      status: "issued",
      token,
      userContext: {
        userId: request.user.id,
        intentHint: request.intentHint,
        autonomyMode: request.autonomyMode
      }
    };
  }

  auditEvents() {
    return [...this.audit];
  }

  #validate(request = {}) {
    if (!request.user?.id || !request.user?.authenticated) {
      return { ok: false, reason: "authentication_required" };
    }
    if (request.scope && request.scope !== REALTIME_SCOPE) {
      return { ok: false, reason: "scope_not_allowed" };
    }
    if (!ALLOWED_INTENTS.has(request.intentHint)) {
      return { ok: false, reason: "intent_not_allowed" };
    }
    if (!ALLOWED_AUTONOMY_MODES.has(request.autonomyMode)) {
      return { ok: false, reason: "autonomy_mode_not_allowed" };
    }
    return { ok: true };
  }

  #record(type, detail) {
    this.audit.push({
      at: this.now().toISOString(),
      type,
      detail
    });
  }
}

export function createRealtimeTokenHttpHandler({
  service = new RealtimeTokenService(),
  authVerifier = new BearerAuthVerifier()
} = {}) {
  return async function realtimeTokenHttpHandler(req, res) {
    if (req.method !== "POST" || req.url !== "/realtime/session-token") {
      writeJson(res, 404, { status: "not_found" });
      return;
    }

    try {
      const body = await readJson(req);
      const auth = authVerifier.verify(req.headers?.authorization ?? "");
      if (!auth.authenticated) {
        writeJson(res, 401, {
          status: "denied",
          reason: auth.reason
        });
        return;
      }

      const response = service.createSessionToken({
        ...body,
        user: auth.user
      });
      writeJson(res, response.status === "issued" ? 200 : 403, response);
    } catch (error) {
      writeJson(res, 400, {
        status: "denied",
        reason: "invalid_json"
      });
    }
  };
}

async function readJson(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString("utf8");
  return raw ? JSON.parse(raw) : {};
}

function writeJson(res, statusCode, body) {
  res.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store"
  });
  res.end(JSON.stringify(body));
}
