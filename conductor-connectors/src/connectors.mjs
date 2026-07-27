import { PersonalGraph, Sensitivity } from "../../conductor-personal-graph/src/personal-graph.mjs";

export class ConnectorRuntime {
  constructor({ now = () => new Date(), audit = [] } = {}) {
    this.now = now;
    this.audit = audit;
    this.connectors = new Map();
    this.credentials = new Map();
  }

  registerConnector(connector) {
    this.connectors.set(connector.id, connector);
    this.audit.unshift({ type: "connector.registered", detail: connector.id });
  }

  connectAccount({ source, accountId, credentialHandle, purposes }) {
    if (!this.connectors.has(source)) {
      throw new Error(`Unknown connector: ${source}`);
    }
    this.credentials.set(`${source}:${accountId}`, {
      source,
      accountId,
      credentialHandle,
      purposes: new Set(purposes)
    });
    this.audit.unshift({ type: "connector.account_connected", detail: `${source}:${accountId}` });
  }

  async read({ source, accountId, purpose, params = {} }) {
    const connector = this.connectors.get(source);
    if (!connector) {
      return { status: "missing_connector", facts: [], grants: [] };
    }

    const credential = this.credentials.get(`${source}:${accountId}`);
    if (!credential) {
      this.audit.unshift({ type: "connector.read_denied", detail: `${source}:${accountId}: missing credential` });
      return { status: "denied", reason: "missing_credential", facts: [], grants: [] };
    }

    if (!credential.purposes.has(purpose)) {
      this.audit.unshift({ type: "connector.read_denied", detail: `${source}:${accountId}: purpose ${purpose}` });
      return { status: "denied", reason: "purpose_not_allowed", facts: [], grants: [] };
    }

    const result = await connector.read({
      accountId,
      purpose,
      params,
      credentialHandle: credential.credentialHandle,
      now: this.now
    });

    this.audit.unshift({ type: "connector.read", detail: `${source}:${purpose}:${result.facts.length} facts` });
    return {
      status: "ok",
      facts: result.facts,
      grants: [
        {
          id: `grant_${source}_${purpose}`,
          source,
          accountId,
          purposes: [purpose]
        }
      ]
    };
  }

  async hydrateGraph({ graph = new PersonalGraph({ now: this.now }), requests }) {
    for (const request of requests) {
      const result = await this.read(request);
      for (const grant of result.grants) {
        graph.grantAccess(grant);
      }
      for (const fact of result.facts) {
        graph.addFact(fact);
      }
    }
    return graph;
  }
}

export function createDefaultConnectorRuntime(options = {}) {
  const runtime = new ConnectorRuntime(options);
  for (const connector of createMockConnectors()) {
    runtime.registerConnector(connector);
  }
  runtime.connectAccount({
    source: "google_calendar",
    accountId: "personal",
    credentialHandle: "vault:calendar:personal",
    purposes: ["activity_planning", "scheduling"]
  });
  runtime.connectAccount({
    source: "weather_provider",
    accountId: "device",
    credentialHandle: "vault:weather:device",
    purposes: ["activity_planning"]
  });
  runtime.connectAccount({
    source: "facebook_events",
    accountId: "personal",
    credentialHandle: "vault:facebook:personal",
    purposes: ["activity_planning"]
  });
  runtime.connectAccount({
    source: "device_contacts",
    accountId: "device",
    credentialHandle: "vault:contacts:device",
    purposes: ["activity_planning", "messaging"]
  });
  runtime.connectAccount({
    source: "messages",
    accountId: "device",
    credentialHandle: "vault:messages:device",
    purposes: ["messaging"]
  });
  runtime.connectAccount({
    source: "maps",
    accountId: "device",
    credentialHandle: "vault:maps:device",
    purposes: ["activity_planning", "navigation"]
  });
  return runtime;
}

export function outdoorPlanningRequests() {
  return [
    { source: "google_calendar", accountId: "personal", purpose: "activity_planning", params: { window: "afternoon" } },
    { source: "weather_provider", accountId: "device", purpose: "activity_planning", params: { hours: 8 } },
    { source: "facebook_events", accountId: "personal", purpose: "activity_planning", params: { query: "outdoor", radiusMiles: 10 } },
    { source: "device_contacts", accountId: "device", purpose: "activity_planning", params: { query: "Maya" } },
    { source: "maps", accountId: "device", purpose: "activity_planning", params: { destination: "Outdoor Jazz At The Garden" } }
  ];
}

export function createMockConnectors() {
  return [
    {
      id: "google_calendar",
      async read({ accountId, purpose, now }) {
        return {
          facts: [
            {
              id: "connector_calendar_free",
              type: "calendar_availability",
              source: "google_calendar",
              accountId,
              summary: "Free from 2:30 PM to 5:30 PM; dinner hold at 7:00 PM.",
              redactedSummary: "Free from 2:30 PM to 5:30 PM.",
              sensitivity: Sensitivity.PRIVATE,
              allowedPurposes: [purpose, "scheduling"],
              observedAt: now().toISOString()
            }
          ]
        };
      }
    },
    {
      id: "weather_provider",
      async read({ accountId, purpose, now }) {
        return {
          facts: [
            {
              id: "connector_weather_clear",
              type: "weather_hourly",
              source: "weather_provider",
              accountId,
              summary: "Clear after 1 PM, 78 F, low wind.",
              sensitivity: Sensitivity.PUBLIC,
              allowedPurposes: [purpose],
              observedAt: now().toISOString(),
              expiresAt: "2026-07-27T20:00:00-05:00"
            }
          ]
        };
      }
    },
    {
      id: "facebook_events",
      async read({ accountId, purpose, now }) {
        return {
          facts: [
            {
              id: "connector_event_jazz",
              type: "event_candidate",
              source: "facebook_events",
              accountId,
              summary: "Outdoor Jazz At The Garden at 3:30 PM, 2.4 miles away, free.",
              sensitivity: Sensitivity.PERSONAL,
              allowedPurposes: [purpose],
              observedAt: now().toISOString()
            }
          ]
        };
      }
    },
    {
      id: "device_contacts",
      async read({ accountId, purpose, now }) {
        return {
          facts: [
            {
              id: "connector_contact_maya",
              type: "contact_preference",
              source: "device_contacts",
              accountId,
              summary: "Maya Chen prefers Messages and is often invited to outdoor events.",
              redactedSummary: "Selected contact prefers Messages.",
              sensitivity: Sensitivity.PRIVATE,
              allowedPurposes: [purpose, "messaging"],
              observedAt: now().toISOString()
            }
          ]
        };
      }
    },
    {
      id: "messages",
      async read() {
        return { facts: [] };
      }
    },
    {
      id: "maps",
      async read({ accountId, purpose, now }) {
        return {
          facts: [
            {
              id: "connector_maps_route",
              type: "route_hint",
              source: "maps",
              accountId,
              summary: "Outdoor Jazz At The Garden is about 12 minutes away.",
              sensitivity: Sensitivity.PERSONAL,
              allowedPurposes: [purpose, "navigation"],
              observedAt: now().toISOString()
            }
          ]
        };
      }
    }
  ];
}
