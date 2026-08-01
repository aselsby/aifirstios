#!/usr/bin/env node
/**
 * G4-OEM — live AccessibilityService proof on Google Messages compose.
 *
 * Pass criteria: log contains operator.live_verified with
 * accessibility_live_tree:messages_draft_invite and/or
 * accessibility.queue_resolved for operation_outbound_message_create_draft.
 */
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;
const apk = path.join(root, "app/build/outputs/apk/debug/app-debug.apk");
const pkg = "app.conductor.prototype";
const a11y = `${pkg}/app.conductor.operator.accessibility.ConductorAccessibilityService`;
const launcher = `${pkg}/app.conductor.launcher.ConductorLauncherActivity`;
const body = "Hello_from_Conductor_OEM_draft";
const utterance = `text 5550100 saying ${body}`;

function adb(args) {
  const r = spawnSync("adb", args, { encoding: "utf8", timeout: 120000 });
  return `${r.stdout || ""}${r.stderr || ""}`.trim();
}
function sleep(sec) {
  spawnSync("sleep", [String(sec)]);
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

const packages = adb(["shell", "pm", "list", "packages"]);
check(
  "messages_installed",
  packages.includes("com.google.android.apps.messaging"),
  packages.split("\n").filter((l) => /messag|sms/i.test(l)).join("\n")
);

adb(["install", "-r", apk]);
adb(["shell", "am", "force-stop", pkg]);
adb(["shell", "am", "force-stop", "com.google.android.apps.messaging"]);
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
adb(["shell", "settings", "put", "secure", "accessibility_enabled", "0"]);
sleep(1);
adb(["shell", "settings", "put", "secure", "accessibility_enabled", "1"]);
adb(["shell", "settings", "put", "secure", "enabled_accessibility_services", a11y]);
sleep(2);
const enabled = adb(["shell", "settings", "get", "secure", "enabled_accessibility_services"]);
check("a11y_enabled", enabled.includes(a11y), enabled);

adb(["logcat", "-c"]);
adb([
  "shell",
  "am",
  "start",
  "-n",
  launcher,
  "--es",
  "conductor_intent",
  "life_messaging",
  "--es",
  "conductor_utterance",
  utterance
]);
// First pass queues + launches Messages; a11y drain fills compose.
sleep(16);

let log = adb(["logcat", "-d"]);
const snippets = log
  .split("\n")
  .filter((l) =>
    /ConductorOS|live_verified|queue_resolved|queue_still|foreground_assist|workflow_rendered|smsto|live_selector|live_step/.test(
      l
    )
  )
  .slice(-50)
  .join("\n");

const liveVerified =
  /operator\.live_verified.*messages_draft_invite/.test(log) ||
  /accessibility\.queue_resolved.*outbound_message_create_draft/.test(log) ||
  /accessibility\.queue_resolved.*messages_draft/.test(log);

check("live_tree_verified_oem", liveVerified, snippets);

adb(["shell", "uiautomator", "dump", "/sdcard/oem-g4.xml"]);
adb(["pull", "/sdcard/oem-g4.xml", path.join(root, "oem-g4-ui.xml")]);
let ui = "";
try {
  ui = fs.readFileSync(path.join(root, "oem-g4-ui.xml"), "utf8");
} catch (_) {}
const hasBody = ui.includes(body) || ui.includes("Hello_from_Conductor");
const onMessages = ui.includes("com.google.android.apps.messaging") || /555.?0100/.test(ui);
check("messages_ui_or_body", hasBody || onMessages, {
  hasBody,
  onMessages,
  uiLen: ui.length
});

// Soft secondary signal: foreground assist launched messaging package.
check(
  "foreground_assist_messages",
  /operator\.foreground_assist.*messaging|launch_started:com\.google\.android\.apps\.messaging|smsto:/.test(
    log
  ) || liveVerified,
  snippets
);

const failed = checks.some((c) => c.status !== "ok");
console.log(JSON.stringify({ status: failed ? "failed" : "ready", gate: "G4-OEM", checks }, null, 2));
process.exit(failed ? 1 : 0);
