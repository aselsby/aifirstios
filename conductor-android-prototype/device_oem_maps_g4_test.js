#!/usr/bin/env node
/**
 * G4-OEM Maps — live AccessibilityService proof on Google Maps place open.
 *
 * Pass criteria: log contains operator.live_verified with
 * accessibility_live_tree:maps_open_route and/or
 * accessibility.queue_resolved for operation_maps_route_open.
 */
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;
const apk = path.join(root, "app/build/outputs/apk/debug/app-debug.apk");
const pkg = "app.conductor.prototype";
const a11y = `${pkg}/app.conductor.operator.accessibility.ConductorAccessibilityService`;
const launcher = `${pkg}/app.conductor.launcher.ConductorLauncherActivity`;
const destination = "Riverfront Park";
const utterance = `navigate to ${destination}`;

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
  "maps_installed",
  packages.includes("com.google.android.apps.maps"),
  packages.split("\n").filter((l) => /maps/i.test(l)).join("\n")
);

adb(["install", "-r", apk]);
adb(["shell", "am", "force-stop", pkg]);
adb(["shell", "am", "force-stop", "com.google.android.apps.maps"]);
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

// Pre-dismiss Maps first-run sign-in if it appears later; also warm package.
adb(["shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "geo:0,0?q=warmup", "com.google.android.apps.maps"]);
sleep(3);
// Tap SKIP if present (best-effort).
adb(["shell", "input", "tap", "880", "201"]);
sleep(1);
adb(["shell", "am", "force-stop", "com.google.android.apps.maps"]);
sleep(1);

adb(["logcat", "-c"]);
adb([
  "shell",
  "am",
  "start",
  "-n",
  launcher,
  "--es",
  "conductor_intent",
  "life_maps",
  "--es",
  "conductor_utterance",
  utterance
]);
// Queue + geo launch + a11y drain.
sleep(18);

let log = adb(["logcat", "-d"]);
const snippets = log
  .split("\n")
  .filter((l) =>
    /ConductorOS|live_verified|queue_resolved|queue_still|foreground_assist|workflow_rendered|geo:|live_selector|live_step|maps/.test(
      l
    )
  )
  .slice(-55)
  .join("\n");

const liveVerified =
  /operator\.live_verified.*maps_open_route/.test(log) ||
  /accessibility\.queue_resolved.*maps_route_open/.test(log) ||
  /accessibility\.queue_resolved.*maps_open_route/.test(log);

check("live_tree_verified_maps", liveVerified, snippets);

adb(["shell", "uiautomator", "dump", "/sdcard/oem-maps-g4.xml"]);
adb(["pull", "/sdcard/oem-maps-g4.xml", path.join(root, "oem-maps-g4-ui.xml")]);
let ui = "";
try {
  ui = fs.readFileSync(path.join(root, "oem-maps-g4-ui.xml"), "utf8");
} catch (_) {}
const hasDestination = ui.includes("Riverfront") || ui.includes(destination);
const onMaps = ui.includes("com.google.android.apps.maps");
check("maps_ui_or_destination", hasDestination || onMaps, {
  hasDestination,
  onMaps,
  uiLen: ui.length
});

check(
  "foreground_assist_maps",
  /operator\.foreground_assist.*maps|launch_started:com\.google\.android\.apps\.maps|geo:0,0/.test(log) ||
    liveVerified,
  snippets
);

const failed = checks.some((c) => c.status !== "ok");
console.log(JSON.stringify({ status: failed ? "failed" : "ready", gate: "G4-OEM-Maps", checks }, null, 2));
process.exit(failed ? 1 : 0);
