export const AutonomyMode = Object.freeze({
  ASK_ONLY: "ask_only",
  DRAFT_ONLY: "draft_only",
  LOW_RISK_AUTO: "low_risk_auto",
  TRUSTED_AUTO: "trusted_auto"
});

export const Decision = Object.freeze({
  ALLOW: "allow",
  REQUIRE_APPROVAL: "require_approval",
  BLOCK: "block"
});

export function createInitialState() {
  return {
    mode: AutonomyMode.DRAFT_ONLY,
    activeTask: null,
    transcript: "Ask Conductor to do something across your apps.",
    apps: {
      calendar: {
        connected: true,
        events: [
          { title: "Product review", startsAt: "2026-07-27T12:00:00-05:00", endsAt: "2026-07-27T12:45:00-05:00" },
          { title: "Dinner hold", startsAt: "2026-07-27T19:00:00-05:00", endsAt: "2026-07-27T20:30:00-05:00" }
        ],
        holds: []
      },
      weather: {
        connected: true,
        summary: "Clear after 1 PM, 78 F, low wind",
        bestOutdoorWindow: "2:30 PM - 5:30 PM"
      },
      events: {
        connected: true,
        source: "Facebook Events + public web",
        nearby: [
          { id: "event_jazz", title: "Outdoor Jazz At The Garden", startsAt: "3:30 PM", distance: "2.4 mi", price: 0, score: 94 },
          { id: "event_lake", title: "Lakefront Photo Walk", startsAt: "4:00 PM", distance: "3.1 mi", price: 12, score: 88 },
          { id: "event_market", title: "Riverside Food Market", startsAt: "2:45 PM", distance: "4.8 mi", price: 0, score: 81 }
        ]
      },
      contacts: {
        connected: true,
        people: [
          { id: "maya", name: "Maya Chen", channel: "Messages", freeAfter: "3:00 PM" }
        ]
      },
      messages: {
        connected: true,
        drafts: [],
        sent: []
      },
      maps: {
        connected: true,
        routes: []
      }
    },
    plan: [],
    recommendations: [],
    approvals: [],
    audit: [
      { type: "system.ready", detail: "No app data accessed yet." }
    ]
  };
}

export function setAutonomyMode(state, mode) {
  return {
    ...state,
    mode,
    audit: [{ type: "policy.mode_changed", detail: `Autonomy mode set to ${mode}.` }, ...state.audit]
  };
}

export function evaluatePolicy(step, mode) {
  if (mode === AutonomyMode.ASK_ONLY && step.externalSideEffect) {
    return { decision: Decision.BLOCK, reason: "Ask Only blocks external side effects." };
  }

  if (["purchase.create", "data.delete", "account_security.change"].includes(step.actionType)) {
    return { decision: Decision.BLOCK, reason: "This action is blocked in the MVP." };
  }

  if (["outbound_message.send", "email.send", "public_post.create", "calendar.confirm_booking", "location.share"].includes(step.actionType)) {
    return { decision: Decision.REQUIRE_APPROVAL, reason: "Sensitive external action requires exact approval." };
  }

  if (mode === AutonomyMode.DRAFT_ONLY && step.externalSideEffect) {
    return { decision: Decision.REQUIRE_APPROVAL, reason: "Draft Only requires approval before outside actions." };
  }

  return { decision: Decision.ALLOW, reason: "Allowed by autonomy policy." };
}

export function runOutdoorIntent(state, utterance) {
  const taskId = "task_outdoor_plan";
  const apps = structuredClone(state.apps);
  const best = apps.events.nearby.toSorted((left, right) => right.score - left.score)[0];
  const maya = apps.contacts.people[0];
  const invite = `Want to check out ${best.title} at ${best.startsAt}? Weather looks good and it is only ${best.distance} away.`;
  const steps = [
    { id: "calendar", title: "Read Calendar", app: "Calendar", actionType: "calendar.read", externalSideEffect: false, detail: "Find free windows this afternoon." },
    { id: "weather", title: "Read Weather", app: "Weather", actionType: "weather.read", externalSideEffect: false, detail: "Score outdoor comfort." },
    { id: "events", title: "Search Events", app: "Facebook Events", actionType: "events.read", externalSideEffect: false, detail: "Find nearby outdoor events." },
    { id: "rank", title: "Rank Options", app: "Conductor", actionType: "recommendation.rank", externalSideEffect: false, detail: "Balance timing, distance, price, and preferences." },
    { id: "draft", title: "Draft Invite", app: "Messages", actionType: "outbound_message.create_draft", externalSideEffect: false, detail: invite, payload: { recipient: maya.name, body: invite } },
    { id: "send", title: "Send Invite", app: "Messages", actionType: "outbound_message.send", externalSideEffect: true, detail: invite, payload: { recipient: maya.name, body: invite } },
    { id: "hold", title: "Create Tentative Hold", app: "Calendar", actionType: "calendar.create_hold", externalSideEffect: true, detail: `${best.title}, ${best.startsAt}`, payload: { title: best.title, startsAt: best.startsAt } },
    { id: "route", title: "Open Route", app: "Maps", actionType: "maps.open_route", externalSideEffect: false, detail: `${best.distance} route to ${best.title}`, payload: { destination: best.title } }
  ];

  const audit = [
    { type: "intent.captured", detail: utterance },
    { type: "context.accessed", detail: "Calendar, Weather, Facebook Events, Contacts, Messages, and Maps checked for this task." },
    ...state.audit
  ];
  const approvals = [];
  const plan = [];

  for (const step of steps) {
    const policy = evaluatePolicy(step, state.mode);
    plan.push({ ...step, policy });
    audit.unshift({ type: "policy.evaluated", detail: `${step.title}: ${policy.decision}` });

    if (policy.decision === Decision.ALLOW) {
      applyAllowedStep(apps, step);
      audit.unshift({ type: "tool.executed", detail: `${step.app}: ${step.title}` });
    } else if (policy.decision === Decision.REQUIRE_APPROVAL) {
      approvals.push({
        id: `approval_${step.id}`,
        step,
        exactContent: step.payload?.body ?? step.detail,
        destination: step.app,
        reason: policy.reason
      });
      audit.unshift({ type: "approval.queued", detail: `${step.title} paused for approval.` });
    } else {
      audit.unshift({ type: "policy.blocked", detail: `${step.title}: ${policy.reason}` });
    }
  }

  return {
    ...state,
    apps,
    activeTask: { id: taskId, utterance, status: approvals.length > 0 ? "awaiting_approval" : "complete" },
    transcript: `I found ${best.title}. I drafted the invite and paused sensitive actions for approval.`,
    recommendations: apps.events.nearby,
    plan,
    approvals,
    audit
  };
}

export function approveAction(state, approvalId) {
  const approval = state.approvals.find((item) => item.id === approvalId);
  if (!approval) return state;

  const apps = structuredClone(state.apps);
  applyAllowedStep(apps, approval.step);
  const approvals = state.approvals.filter((item) => item.id !== approvalId);

  return {
    ...state,
    apps,
    approvals,
    activeTask: {
      ...state.activeTask,
      status: approvals.length > 0 ? "awaiting_approval" : "complete"
    },
    audit: [
      { type: "approval.granted", detail: `${approval.step.title} approved.` },
      { type: "tool.executed", detail: `${approval.destination}: ${approval.step.title}` },
      ...state.audit
    ]
  };
}

export function denyAction(state, approvalId) {
  const approval = state.approvals.find((item) => item.id === approvalId);
  if (!approval) return state;

  const approvals = state.approvals.filter((item) => item.id !== approvalId);
  return {
    ...state,
    approvals,
    activeTask: {
      ...state.activeTask,
      status: approvals.length > 0 ? "awaiting_approval" : "stopped"
    },
    audit: [
      { type: "approval.denied", detail: `${approval.step.title} denied. No external action taken.` },
      ...state.audit
    ]
  };
}

function applyAllowedStep(apps, step) {
  if (step.actionType === "outbound_message.create_draft") {
    apps.messages.drafts.push({
      to: step.payload.recipient,
      body: step.payload.body,
      status: "draft"
    });
  }

  if (step.actionType === "outbound_message.send") {
    apps.messages.sent.push({
      to: step.payload.recipient,
      body: step.payload.body,
      status: "sent"
    });
  }

  if (step.actionType === "calendar.create_hold") {
    apps.calendar.holds.push({
      title: step.payload.title,
      startsAt: step.payload.startsAt,
      status: "tentative"
    });
  }

  if (step.actionType === "maps.open_route") {
    apps.maps.routes.push({
      destination: step.payload.destination,
      status: "opened"
    });
  }
}
