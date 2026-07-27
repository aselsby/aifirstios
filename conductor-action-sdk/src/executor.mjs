import { createRegistry, validateActionManifest } from "./action-manifest.mjs";
import { Decision, decideAction } from "./policy.mjs";

export class ActionRuntime {
  constructor({ actions, mode, audit = [], consumedApprovalIds = new Set() } = {}) {
    this.registry = createRegistry(actions);
    this.mode = mode;
    this.audit = audit;
    this.consumedApprovalIds = consumedApprovalIds;
  }

  execute(actionId, input, approval = null) {
    const action = this.registry.get(actionId);
    const manifestValidation = action ? validateActionManifest(action) : { valid: false, errors: ["Unknown action"] };

    if (!manifestValidation.valid) {
      this.audit.unshift({ type: "action.invalid", detail: `${actionId}: ${manifestValidation.errors.join(", ")}` });
      return { status: "blocked", error: manifestValidation.errors };
    }

    const inputValidation = validateInput(action, input);
    if (!inputValidation.valid) {
      this.audit.unshift({ type: "action.input_invalid", detail: `${actionId}: ${inputValidation.errors.join(", ")}` });
      return { status: "blocked", error: inputValidation.errors };
    }

    const policy = decideAction(action, this.mode);
    this.audit.unshift({ type: "policy.evaluated", detail: `${actionId}: ${policy.decision}` });

    if (policy.decision === Decision.BLOCK) {
      return { status: "blocked", policy };
    }

    const approvalCard = policy.decision === Decision.REQUIRE_APPROVAL
      ? {
          id: `approval_${actionId.replaceAll(".", "_")}`,
          actionId,
          app: action.app,
          exactContent: input.exactBody ?? input.body ?? input.title ?? null,
          reason: policy.reason
        }
      : null;
    const approvalReceiptId = approval?.approvalId ?? approval?.id ?? approvalCard?.id;

    if (policy.decision === Decision.REQUIRE_APPROVAL && approval?.status !== "approved") {
      this.audit.unshift({ type: "approval.queued", detail: `${actionId} requires approval.` });
      return { status: "awaiting_approval", policy, approval: approvalCard };
    }

    if (policy.decision === Decision.REQUIRE_APPROVAL && approval?.exactContent !== approvalCard.exactContent) {
      this.audit.unshift({ type: "approval.queued", detail: `${actionId} exact approval mismatch.` });
      return { status: "awaiting_approval", reason: "exact_approval_mismatch", policy, approval: approvalCard };
    }

    if (policy.decision === Decision.REQUIRE_APPROVAL && this.consumedApprovalIds.has(approvalReceiptId)) {
      this.audit.unshift({ type: "approval.replay_blocked", detail: `${actionId}: ${approvalReceiptId}` });
      return { status: "awaiting_approval", reason: "approval_receipt_replayed", policy, approval: approvalCard };
    }

    const output = mockOutput(action, input);
    this.audit.unshift({ type: "action.executed", detail: `${action.app}: ${action.id}` });
    if (policy.decision === Decision.REQUIRE_APPROVAL) {
      this.consumedApprovalIds.add(approvalReceiptId);
      this.audit.unshift({ type: "approval.consumed", detail: `${actionId}: ${approvalReceiptId}` });
    }
    return {
      status: "succeeded",
      actionId,
      output,
      verification: {
        status: "verified",
        method: action.verification
      }
    };
  }
}

function validateInput(action, input) {
  const missing = action.inputSchema.required.filter((field) => input[field] === undefined || input[field] === null || input[field] === "");
  return {
    valid: missing.length === 0,
    errors: missing.map((field) => `Missing input ${field}`)
  };
}

function mockOutput(action, input) {
  if (action.id === "calendar.free_busy") {
    return { freeWindows: [{ startsAt: input.timeMin, endsAt: input.timeMax }] };
  }
  if (action.id === "calendar.create_hold") {
    return { eventId: "event_hold_001", status: "tentative" };
  }
  if (action.id === "weather.hourly") {
    return { summary: "Clear after 1 PM", hourly: [] };
  }
  if (action.id === "facebook_events.search_nearby") {
    return { events: [{ id: "event_jazz", title: "Outdoor Jazz At The Garden" }] };
  }
  if (action.id === "contacts.search") {
    return { contacts: [{ id: "maya", name: "Maya Chen" }] };
  }
  if (action.id === "messages.create_draft") {
    return { draftId: "draft_001", status: "created" };
  }
  if (action.id === "messages.send") {
    return { messageId: "message_001", sentAt: new Date("2026-07-27T15:00:00-05:00").toISOString() };
  }
  if (action.id === "facebook.post") {
    return { postId: "post_001", postedAt: new Date("2026-07-27T15:05:00-05:00").toISOString() };
  }
  if (action.id === "maps.open_route") {
    return { routeId: "route_001", status: "opened" };
  }
  return {};
}
