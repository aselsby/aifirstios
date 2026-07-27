export const Risk = Object.freeze({
  LOW: "low",
  MEDIUM: "medium",
  HIGH: "high"
});

export const ApprovalMode = Object.freeze({
  NEVER: "never",
  ALWAYS: "always",
  POLICY: "policy"
});

export const sampleActions = [
  {
    id: "calendar.free_busy",
    app: "Calendar",
    actionType: "calendar.read",
    description: "Read calendar availability for a bounded time window.",
    risk: Risk.LOW,
    externalSideEffect: false,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["timeMin", "timeMax"] },
    outputSchema: { required: ["freeWindows"] },
    verification: "provider_response_schema"
  },
  {
    id: "calendar.create_hold",
    app: "Calendar",
    actionType: "calendar.create_hold",
    description: "Create a tentative calendar hold.",
    risk: Risk.MEDIUM,
    externalSideEffect: true,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["title", "startsAt", "endsAt"] },
    outputSchema: { required: ["eventId", "status"] },
    verification: "calendar_event_exists"
  },
  {
    id: "weather.hourly",
    app: "Weather",
    actionType: "weather.read",
    description: "Read hourly weather for a location.",
    risk: Risk.LOW,
    externalSideEffect: false,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["location", "hours"] },
    outputSchema: { required: ["summary", "hourly"] },
    verification: "provider_response_schema"
  },
  {
    id: "facebook_events.search_nearby",
    app: "Facebook Events",
    actionType: "events.read",
    description: "Search connected Facebook-style event sources for nearby events.",
    risk: Risk.LOW,
    externalSideEffect: false,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["query", "location", "radiusMiles", "timeWindow"] },
    outputSchema: { required: ["events"] },
    verification: "provider_response_schema"
  },
  {
    id: "contacts.search",
    app: "Contacts",
    actionType: "contacts.read",
    description: "Find matching contacts for a task-scoped purpose.",
    risk: Risk.LOW,
    externalSideEffect: false,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["query"] },
    outputSchema: { required: ["contacts"] },
    verification: "provider_response_schema"
  },
  {
    id: "messages.create_draft",
    app: "Messages",
    actionType: "outbound_message.create_draft",
    description: "Create an unsent message draft.",
    risk: Risk.LOW,
    externalSideEffect: false,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["recipient", "body"] },
    outputSchema: { required: ["draftId", "status"] },
    verification: "draft_exists"
  },
  {
    id: "messages.send",
    app: "Messages",
    actionType: "outbound_message.send",
    description: "Send an exact message to a recipient.",
    risk: Risk.MEDIUM,
    externalSideEffect: true,
    approval: ApprovalMode.ALWAYS,
    inputSchema: { required: ["recipient", "exactBody"] },
    outputSchema: { required: ["messageId", "sentAt"] },
    verification: "message_receipt"
  },
  {
    id: "facebook.post",
    app: "Facebook",
    actionType: "public_post.create",
    description: "Create an exact public post.",
    risk: Risk.HIGH,
    externalSideEffect: true,
    approval: ApprovalMode.ALWAYS,
    inputSchema: { required: ["exactBody"] },
    outputSchema: { required: ["postId", "postedAt"] },
    verification: "post_receipt"
  },
  {
    id: "maps.open_route",
    app: "Maps",
    actionType: "maps.open_route",
    description: "Open a route to a destination.",
    risk: Risk.LOW,
    externalSideEffect: false,
    approval: ApprovalMode.POLICY,
    inputSchema: { required: ["destination"] },
    outputSchema: { required: ["routeId", "status"] },
    verification: "route_opened"
  }
];

export function createRegistry(actions = sampleActions) {
  return new Map(actions.map((action) => [action.id, action]));
}

export function validateActionManifest(action) {
  const required = ["id", "app", "actionType", "risk", "externalSideEffect", "approval", "inputSchema", "outputSchema", "verification"];
  const missing = required.filter((field) => action[field] === undefined || action[field] === null);
  if (missing.length > 0) {
    return { valid: false, errors: missing.map((field) => `Missing ${field}`) };
  }

  if (!Array.isArray(action.inputSchema.required) || !Array.isArray(action.outputSchema.required)) {
    return { valid: false, errors: ["inputSchema.required and outputSchema.required must be arrays"] };
  }

  return { valid: true, errors: [] };
}
