#!/usr/bin/env node
/**
 * G5 — Approval → execute / deny
 * G7 — Instant stop
 *
 * Uses force-stop between phases so intent extras are always applied on a fresh Activity.
 */
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;
const apk = path.join(root, "app/build/outputs/apk/debug/app-debug.apk");
const pkg = "app.conductor.prototype";
const a11y = `${pkg}/app.conductor.operator.accessibility.ConductorAccessibilityService`;
const launcher = `${pkg}/app.conductor.launcher.ConductorLauncherActivity`;
const utter = "Transfer_fifty_dollars_to_savings";
const utter2 = "Transfer_twenty_dollars_to_checking";

function adb(args) {
  const r = spawnSync("adb", args, { encoding: "utf8", timeout: 120000 });
  return `${r.stdout || ""}${r.stderr || ""}`.trim();
}
function sleep(sec) {
  spawnSync("sleep", [String(sec)]);
}
function logs() {
  return adb(["logcat", "-d"]);
}
function conductorLines(log) {
  return log
    .split("\n")
    .filter((l) => /ConductorOS/.test(l))
    .slice(-60)
    .join("\n");
}
function start(extras) {
  adb(["shell", "am", "force-stop", pkg]);
  sleep(1);
  const args = ["shell", "am", "start", "-n", launcher];
  for (const [k, v] of Object.entries(extras)) {
    args.push("--es", k, v);
  }
  return adb(args);
}

const checks = [];
function check(name, ok, detail) {
  checks.push({ name, status: ok ? "ok" : "failed", detail: String(detail || "").slice(0, 1500) });
}

if (!fs.existsSync(apk)) {
  console.log(JSON.stringify({ status: "failed", reason: "missing_apk" }, null, 2));
  process.exit(1);
}

check("device_connected", /\tdevice\b/.test(adb(["devices"])), adb(["devices"]));
adb(["install", "-r", apk]);
adb(["shell", "am", "force-stop", pkg]);
adb(["shell", "pm", "clear", pkg]);
sleep(1);
for (const perm of [
  "android.permission.RECORD_AUDIO",
  "android.permission.READ_CALENDAR",
  "android.permission.READ_CONTACTS",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.POST_NOTIFICATIONS"
]) {
  adb(["shell", "pm", "grant", pkg, perm]);
}
adb(["shell", "settings", "put", "secure", "enabled_accessibility_services", a11y]);
adb(["shell", "settings", "put", "secure", "accessibility_enabled", "1"]);

// Phase 1: pending approval
adb(["logcat", "-c"]);
start({
  conductor_intent: "life_banking",
  conductor_utterance: utter
});
sleep(10);
let log = logs();
check(
  "approval_queued",
  /approval\.queued/.test(log) && /pendingApprovals=1/.test(log),
  conductorLines(log)
);

// Phase 2: approve (prefs survive force-stop; first run still queues, then harness approves and re-runs)
adb(["logcat", "-c"]);
start({
  conductor_intent: "life_banking",
  conductor_utterance: utter,
  conductor_action: "approve_all_pending"
});
sleep(14);
log = logs();
check("harness_approved", /approval\.harness_approved/.test(log), conductorLines(log));
check("approval_granted_after_approve", /approval\.granted/.test(log), conductorLines(log));
check(
  "post_approve_progress",
  /pendingApprovals=0/.test(log) || /operator\.queued|operator\.foreground_assist|approval\.granted/.test(log),
  conductorLines(log)
);

// Phase 3: deny
adb(["logcat", "-c"]);
start({
  conductor_intent: "life_banking",
  conductor_utterance: utter2
});
sleep(9);
// second start with deny — keep process alive so first pending is in state; use no force-stop
adb(["logcat", "-c"]);
adb([
  "shell", "am", "start", "-n", launcher,
  "--es", "conductor_intent", "life_banking",
  "--es", "conductor_utterance", utter2,
  "--es", "conductor_action", "deny_all_pending"
]);
sleep(12);
log = logs();
// If onNewIntent not handled, force-stop path with deny-only after a queue-only start:
if (!/approval\.harness_denied/.test(log)) {
  adb(["logcat", "-c"]);
  start({
    conductor_intent: "life_banking",
    conductor_utterance: utter2,
    conductor_action: "deny_all_pending"
  });
  sleep(14);
  log = logs();
}
check("harness_denied", /approval\.harness_denied/.test(log), conductorLines(log));
check(
  "deny_blocks",
  /approval\.denied|harness_denied|User denied/.test(log),
  conductorLines(log)
);

// Phase 4: G7 stop
adb(["logcat", "-c"]);
start({
  conductor_intent: "life_demo",
  conductor_utterance: "run_live_demo_stop_test_body",
  conductor_action: "stop_autonomy"
});
sleep(10);
log = logs();
check("instant_stop", /autonomy\.stopped/.test(log), conductorLines(log));

const failed = checks.some((c) => c.status !== "ok");
console.log(JSON.stringify({ status: failed ? "failed" : "ready", gate: "G5+G7", checks }, null, 2));
process.exit(failed ? 1 : 0);
