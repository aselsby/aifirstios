#!/usr/bin/env node
/**
 * G4 — live AccessibilityService proof on the controlled Conductor demo surface.
 *
 * Pass criteria: log contains operator.live_verified with accessibility_live_tree
 * and/or accessibility.queue_resolved for conductor_demo_live_draft.
 */
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;
const apk = path.join(root, "app/build/outputs/apk/debug/app-debug.apk");
const pkg = "app.conductor.prototype";
const a11y = `${pkg}/app.conductor.operator.accessibility.ConductorAccessibilityService`;
const demo = `${pkg}/app.conductor.operator.accessibility.ConductorAgentDemoActivity`;
const launcher = `${pkg}/app.conductor.launcher.ConductorLauncherActivity`;

function adb(args) {
  const r = spawnSync("adb", args, { encoding: "utf8", timeout: 90000 });
  return `${r.stdout || ""}${r.stderr || ""}`.trim();
}
function sleep(ms) {
  spawnSync("sleep", [String(Math.max(1, Math.ceil(ms / 1000)))]);
}

const checks = [];
function check(name, ok, detail) {
  checks.push({ name, status: ok ? "ok" : "failed", detail: String(detail || "").slice(0, 1000) });
}

if (!fs.existsSync(apk)) {
  console.log(JSON.stringify({ status: "failed", reason: "missing_apk", apk }, null, 2));
  process.exit(1);
}

const devices = adb(["devices"]);
check("device_connected", /\tdevice\b/.test(devices), devices);

adb(["install", "-r", apk]);
adb(["shell", "am", "force-stop", pkg]);
adb(["shell", "pm", "clear", pkg]);
sleep(1000);
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
sleep(1000);
adb(["shell", "settings", "put", "secure", "accessibility_enabled", "1"]);
adb(["shell", "settings", "put", "secure", "enabled_accessibility_services", a11y]);
sleep(1500);
const enabled = adb(["shell", "settings", "get", "secure", "enabled_accessibility_services"]);
check("a11y_enabled", enabled.includes(a11y), enabled);

adb(["logcat", "-c"]);

// Launcher runs life_demo → queues op → activity-process foreground assist opens demo.
adb([
  "shell",
  "am",
  "start",
  "-n",
  launcher,
  "--es",
  "conductor_intent",
  "life_demo",
  "--es",
  "conductor_utterance",
  "run_live_demo_Hello_from_live_accessibility"
]);
sleep(12000);

const log = adb(["logcat", "-d", "-t", "400"]);
const liveVerified =
  /operator\.live_verified.*accessibility_live_tree:conductor_demo_live_draft/.test(log) ||
  /accessibility\.queue_resolved.*conductor_demo_live_draft/.test(log);
const snippets = log
  .split("\n")
  .filter((l) => /ConductorOS|live_verified|queue_resolved|queue_still|workflow_rendered|foreground_assist/.test(l))
  .slice(-40)
  .join("\n");

adb(["shell", "uiautomator", "dump", "/sdcard/g4.xml"]);
adb(["pull", "/sdcard/g4.xml", path.join(root, "g4-ui.xml")]);
let ui = "";
try {
  ui = fs.readFileSync(path.join(root, "g4-ui.xml"), "utf8");
} catch (_) {}
const hasProof = ui.includes("Conductor demo signed in");
const hasReady = ui.includes("demo draft ready");
const hasBody = ui.includes("run_live_demo_Hello_from_live_accessibility") || ui.includes("Hello");

check("live_tree_verified", liveVerified, snippets || log.slice(-500));
check("demo_surface_or_filled", hasProof || hasReady || hasBody, {
  hasProof,
  hasReady,
  hasBody
});

const failed = checks.some((c) => c.status !== "ok");
console.log(JSON.stringify({ status: failed ? "failed" : "ready", gate: "G4", checks }, null, 2));
process.exit(failed ? 1 : 0);
