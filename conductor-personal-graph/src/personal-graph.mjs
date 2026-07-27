import { AutonomyMode, Decision, decideDataSnapshot } from "../../conductor-action-sdk/src/policy.mjs";

export const Sensitivity = Object.freeze({
  PUBLIC: "public",
  PERSONAL: "personal",
  PRIVATE: "private",
  SECRET: "secret"
});

export class PersonalGraph {
  constructor({ now = () => new Date(), audit = [] } = {}) {
    this.now = now;
    this.facts = new Map();
    this.grants = new Map();
    this.appAgentGrants = new Map();
    this.audit = audit;
  }

  grantAccess(grant) {
    const normalized = {
      id: grant.id,
      source: grant.source,
      accountId: grant.accountId,
      purposes: new Set(grant.purposes),
      expiresAt: grant.expiresAt ? new Date(grant.expiresAt) : null,
      revoked: false
    };
    this.grants.set(normalized.id, normalized);
    this.audit.unshift({ type: "grant.created", detail: `${grant.source}:${grant.purposes.join(",")}` });
    return normalized.id;
  }

  revokeGrant(grantId) {
    const grant = this.grants.get(grantId);
    if (!grant) return false;
    grant.revoked = true;
    this.audit.unshift({ type: "grant.revoked", detail: grantId });
    return true;
  }

  grantAppAgentAccess(grant) {
    const existing = this.appAgentGrants.get(grant.id);
    if (existing?.revoked) {
      this.audit.unshift({
        type: "app_agent.grant_preserved_revoked",
        detail: `${grant.appAgentId}:${grant.purposes.join(",")}`
      });
      return existing.id;
    }

    const normalized = {
      id: grant.id,
      appAgentId: grant.appAgentId,
      packageName: grant.packageName,
      purposes: new Set(grant.purposes),
      sources: new Set(grant.sources),
      expiresAt: grant.expiresAt ? new Date(grant.expiresAt) : null,
      revoked: false
    };
    this.appAgentGrants.set(normalized.id, normalized);
    this.audit.unshift({
      type: "app_agent.grant_created",
      detail: `${grant.appAgentId}:${grant.purposes.join(",")}:${grant.sources.join(",")}`
    });
    return normalized.id;
  }

  revokeAppAgentGrant(grantId) {
    const grant = this.appAgentGrants.get(grantId);
    if (!grant) return false;
    grant.revoked = true;
    this.audit.unshift({ type: "app_agent.grant_revoked", detail: grantId });
    return true;
  }

  addFact(fact) {
    const validation = this.#validateFact(fact);
    if (!validation.valid) {
      throw new Error(validation.errors.join(", "));
    }

    const normalized = {
      ...fact,
      observedAt: fact.observedAt ?? this.now().toISOString(),
      expiresAt: fact.expiresAt ?? null,
      allowedPurposes: new Set(fact.allowedPurposes),
      tags: new Set(fact.tags ?? [])
    };
    this.facts.set(normalized.id, normalized);
    this.audit.unshift({ type: "fact.added", detail: `${fact.type}:${fact.source}` });
    return normalized.id;
  }

  query({ purpose, sources = [], types = [], includeExpired = false }) {
    const now = this.now();
    const sourceSet = new Set(sources);
    const typeSet = new Set(types);
    const results = [];
    const denied = [];

    for (const fact of this.facts.values()) {
      if (sourceSet.size > 0 && !sourceSet.has(fact.source)) continue;
      if (typeSet.size > 0 && !typeSet.has(fact.type)) continue;
      if (!includeExpired && fact.expiresAt && new Date(fact.expiresAt) <= now) continue;
      if (!fact.allowedPurposes.has(purpose)) {
        denied.push({ factId: fact.id, reason: "purpose_not_allowed" });
        continue;
      }
      if (!this.#hasGrant(fact, purpose, now)) {
        denied.push({ factId: fact.id, reason: "missing_or_revoked_grant" });
        continue;
      }
      results.push(fact);
    }

    this.audit.unshift({
      type: "graph.queried",
      detail: `${purpose}: ${results.length} allowed, ${denied.length} denied`
    });

    return { facts: results, denied };
  }

  modelSnapshot(query, { redact = true } = {}) {
    const result = this.query(query);
    const facts = result.facts.map((fact) => ({
      id: fact.id,
      type: fact.type,
      source: fact.source,
      sensitivity: fact.sensitivity,
      summary: redact ? this.#redact(fact) : fact.summary,
      observedAt: fact.observedAt,
      expiresAt: fact.expiresAt
    }));

    return {
      purpose: query.purpose,
      facts,
      denied: result.denied
    };
  }

  modelSnapshotForAppAgent(query, { appAgentId, autonomyMode = AutonomyMode.DRAFT_ONLY, redact = true }) {
    const sourceSet = new Set(query.sources ?? []);
    const grant = this.#appAgentGrantFor({ appAgentId, purpose: query.purpose, sources: sourceSet });
    if (!grant.allowed) {
      this.audit.unshift({
        type: "app_agent.snapshot_denied",
        detail: `${appAgentId}:${query.purpose}:${grant.reason}`
      });
      return {
        purpose: query.purpose,
        appAgentId,
        facts: [],
        denied: [{ reason: grant.reason, sources: [...sourceSet] }]
      };
    }

    const profileDecision = decideDataSnapshot({
      appAgentGrant: {
        sources: [...grant.grant.sources],
        revoked: grant.grant.revoked
      },
      requestedSources: [...sourceSet]
    }, autonomyMode);

    if (profileDecision.decision !== Decision.ALLOW) {
      this.audit.unshift({
        type: "app_agent.snapshot_denied",
        detail: `${appAgentId}:${query.purpose}:${profileDecision.reason}`
      });
      return {
        purpose: query.purpose,
        appAgentId,
        facts: [],
        denied: [{ reason: profileDecision.reason, sources: [...sourceSet] }]
      };
    }

    const snapshot = this.modelSnapshot(query, { redact });
    this.audit.unshift({
      type: "app_agent.snapshot_allowed",
      detail: `${appAgentId}:${query.purpose}:${snapshot.facts.length} facts`
    });
    return { ...snapshot, appAgentId };
  }

  purgeExpired() {
    const now = this.now();
    let purged = 0;
    for (const [id, fact] of this.facts.entries()) {
      if (fact.expiresAt && new Date(fact.expiresAt) <= now) {
        this.facts.delete(id);
        purged += 1;
      }
    }
    this.audit.unshift({ type: "facts.purged", detail: `${purged} expired facts removed` });
    return purged;
  }

  #hasGrant(fact, purpose, now) {
    for (const grant of this.grants.values()) {
      if (grant.revoked) continue;
      if (grant.source !== fact.source) continue;
      if (grant.accountId !== fact.accountId) continue;
      if (!grant.purposes.has(purpose)) continue;
      if (grant.expiresAt && grant.expiresAt <= now) continue;
      return true;
    }
    return false;
  }

  #appAgentGrantFor({ appAgentId, purpose, sources }) {
    const now = this.now();
    for (const grant of this.appAgentGrants.values()) {
      if (grant.revoked) continue;
      if (grant.appAgentId !== appAgentId) continue;
      if (!grant.purposes.has(purpose)) continue;
      if (grant.expiresAt && grant.expiresAt <= now) continue;
      const sourceDenied = [...sources].some((source) => !grant.sources.has(source));
      if (sourceDenied) continue;
      return { allowed: true, grant };
    }
    return { allowed: false, reason: "missing_or_revoked_app_agent_grant" };
  }

  #redact(fact) {
    if (fact.sensitivity === Sensitivity.SECRET) return "[redacted secret]";
    if (fact.sensitivity === Sensitivity.PRIVATE && fact.redactedSummary) return fact.redactedSummary;
    return fact.summary;
  }

  #validateFact(fact) {
    const required = ["id", "type", "source", "accountId", "summary", "sensitivity", "allowedPurposes"];
    const errors = required.filter((field) => fact[field] === undefined || fact[field] === null).map((field) => `Missing ${field}`);
    if (fact.allowedPurposes && !Array.isArray(fact.allowedPurposes)) {
      errors.push("allowedPurposes must be an array");
    }
    return { valid: errors.length === 0, errors };
  }
}

export function seedOutdoorPlanningGraph(graph) {
  graph.grantAccess({
    id: "grant_calendar_activity",
    source: "google_calendar",
    accountId: "personal",
    purposes: ["activity_planning", "scheduling"]
  });
  graph.grantAppAgentAccess({
    id: "agent_grant_conductor_activity",
    appAgentId: "conductor.voice",
    packageName: "app.conductor.prototype",
    purposes: ["activity_planning"],
    sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts", "maps"]
  });
  graph.grantAccess({
    id: "grant_weather_activity",
    source: "weather_provider",
    accountId: "device",
    purposes: ["activity_planning"]
  });
  graph.grantAccess({
    id: "grant_events_activity",
    source: "facebook_events",
    accountId: "personal",
    purposes: ["activity_planning"]
  });
  graph.grantAccess({
    id: "grant_contacts_activity",
    source: "device_contacts",
    accountId: "device",
    purposes: ["activity_planning", "messaging"]
  });

  graph.addFact({
    id: "fact_calendar_free",
    type: "calendar_availability",
    source: "google_calendar",
    accountId: "personal",
    summary: "Free from 2:30 PM to 5:30 PM; dinner hold at 7:00 PM.",
    redactedSummary: "Free from 2:30 PM to 5:30 PM.",
    sensitivity: Sensitivity.PRIVATE,
    allowedPurposes: ["activity_planning", "scheduling"]
  });
  graph.addFact({
    id: "fact_weather_clear",
    type: "weather_hourly",
    source: "weather_provider",
    accountId: "device",
    summary: "Clear after 1 PM, 78 F, low wind.",
    sensitivity: Sensitivity.PUBLIC,
    allowedPurposes: ["activity_planning"]
  });
  graph.addFact({
    id: "fact_event_jazz",
    type: "event_candidate",
    source: "facebook_events",
    accountId: "personal",
    summary: "Outdoor Jazz At The Garden at 3:30 PM, 2.4 miles away, free.",
    sensitivity: Sensitivity.PERSONAL,
    allowedPurposes: ["activity_planning"]
  });
  graph.addFact({
    id: "fact_contact_maya",
    type: "contact_preference",
    source: "device_contacts",
    accountId: "device",
    summary: "Maya Chen prefers Messages and is often invited to outdoor events.",
    redactedSummary: "Selected contact prefers Messages.",
    sensitivity: Sensitivity.PRIVATE,
    allowedPurposes: ["activity_planning", "messaging"]
  });

  return graph;
}
