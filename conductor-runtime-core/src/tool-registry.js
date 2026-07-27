export function createToolRegistry(auditLog) {
  return {
    "calendar.free_busy": async (step) => succeed(step, "provider_response_schema", auditLog),
    "weather.hourly": async (step) => succeed(step, "provider_response_schema", auditLog),
    "events.rank": async (step) => succeed(step, "deterministic_ranker", auditLog),
    "messages.create_draft": async (step) => succeed(step, "draft_created", auditLog),
    "messages.send": async (step) => succeed(step, "message_sent_receipt", auditLog),
    "social.public_post": async (step) => succeed(step, "public_post_receipt", auditLog)
  };
}

async function succeed(step, verificationMethod, auditLog) {
  const result = {
    stepId: step.id,
    status: "succeeded",
    verification: {
      status: "verified",
      method: verificationMethod
    }
  };

  auditLog.record("tool.executed", {
    stepId: step.id,
    tool: step.tool,
    verification: result.verification
  });

  return result;
}
