export function createMockConnectors() {
  return {
    calendar: {
      async read() {
        return {
          source: "google_calendar",
          type: "calendar_availability",
          sensitivity: "private",
          freeWindows: [
            {
              startsAt: "2026-07-27T14:30:00-05:00",
              endsAt: "2026-07-27T17:30:00-05:00"
            }
          ]
        };
      }
    },
    weather: {
      async read() {
        return {
          source: "weather_provider",
          type: "weather_hourly",
          summary: "Clear after 1 PM, 78 F, low wind",
          bestOutdoorWindow: {
            startsAt: "2026-07-27T14:00:00-05:00",
            endsAt: "2026-07-27T18:00:00-05:00"
          }
        };
      }
    },
    events: {
      async read() {
        return {
          source: "facebook_events_and_web",
          type: "events_nearby",
          events: [
            {
              id: "event_001",
              title: "Outdoor Jazz At The Garden",
              startsAt: "2026-07-27T15:30:00-05:00",
              distanceMiles: 2.4,
              priceUsd: 0,
              score: 94
            },
            {
              id: "event_002",
              title: "Lakefront Photo Walk",
              startsAt: "2026-07-27T16:00:00-05:00",
              distanceMiles: 3.1,
              priceUsd: 12,
              score: 88
            }
          ]
        };
      }
    },
    contacts: {
      async read() {
        return {
          source: "device_contacts",
          type: "contact_candidates",
          contacts: [
            {
              id: "contact_maya",
              displayName: "Maya Chen",
              preferredChannel: "messages"
            }
          ]
        };
      }
    }
  };
}
