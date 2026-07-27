import { ActionRuntime } from "./executor.mjs";
import { AutonomyMode } from "./policy.mjs";

const runtime = new ActionRuntime({ mode: AutonomyMode.DRAFT_ONLY });

const draft = runtime.execute("messages.create_draft", {
  recipient: "Maya Chen",
  body: "Want to check out Outdoor Jazz At The Garden at 3:30?"
});

const send = runtime.execute("messages.send", {
  recipient: "Maya Chen",
  exactBody: "Want to check out Outdoor Jazz At The Garden at 3:30?"
});

const approvedSend = runtime.execute("messages.send", {
  recipient: "Maya Chen",
  exactBody: "Want to check out Outdoor Jazz At The Garden at 3:30?"
}, { status: "approved" });

console.log(JSON.stringify({
  draft,
  send,
  approvedSend,
  audit: runtime.audit
}, null, 2));
