#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const root = __dirname;
const strict = process.argv.includes("--strict");
const packageName = "app.conductor.prototype";
const launcherActivity = `${packageName}/app.conductor.launcher.ConductorLauncherActivity`;
const accessibilityService = `${packageName}/app.conductor.operator.accessibility.ConductorAccessibilityService`;
const apkPath = path.join(root, "app/build/outputs/apk/debug/app-debug.apk");

const checks = [
  commandCheck("adb_available", "adb"),
  fileCheck("debug_apk", apkPath),
  adbCheck("device_connected", ["devices"], (stdout) =>
    stdout.split("\n").some((line) => /\tdevice$/.test(line))
  ),
  adbCheck("package_installed", ["shell", "pm", "path", packageName], (stdout) =>
    stdout.includes(`package:`)
  ),
  adbCheck("launcher_resolves", ["shell", "cmd", "package", "resolve-activity", "--brief", packageName], (stdout) =>
    stdout.includes("ConductorLauncherActivity") || stdout.includes(packageName)
  ),
  adbCheck("record_audio_permission_declared", ["shell", "dumpsys", "package", packageName], (stdout) =>
    stdout.includes("android.permission.RECORD_AUDIO")
  ),
  adbCheck("accessibility_service_declared", ["shell", "dumpsys", "package", packageName], (stdout) =>
    stdout.includes("ConductorAccessibilityService")
  ),
  adbCheck("home_intent_declared", ["shell", "cmd", "package", "query-activities", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"], (stdout) =>
    stdout.includes(packageName)
  )
];

const failed = checks.filter((check) => check.status !== "ok");
const result = {
  status: failed.length === 0 ? "ready" : "blocked",
  strict,
  packageName,
  launcherActivity,
  accessibilityService,
  checks,
  nextCommands: [
    "./gradlew :app:assembleDebug",
    `adb install -r ${path.relative(root, apkPath)}`,
    `adb shell monkey -p ${packageName} 1`,
    "adb shell settings get secure enabled_accessibility_services",
    `adb shell appops get ${packageName}`
  ]
};

console.log(JSON.stringify(result, null, 2));

if (strict && failed.length > 0) {
  process.exit(1);
}

function fileCheck(name, fullPath) {
  return {
    name,
    status: fs.existsSync(fullPath) ? "ok" : "missing",
    detail: path.relative(root, fullPath)
  };
}

function commandCheck(name, command) {
  return {
    name,
    status: commandExists(command) ? "ok" : "missing",
    detail: command
  };
}

function adbCheck(name, args, predicate) {
  if (!commandExists("adb")) {
    return { name, status: "missing", detail: "adb" };
  }

  const output = spawnSync("adb", args, { encoding: "utf8" });
  const stdout = output.stdout || "";
  const stderr = output.stderr || "";
  if (output.status !== 0) {
    return {
      name,
      status: "failed",
      detail: stderr.trim() || stdout.trim() || args.join(" ")
    };
  }
  return {
    name,
    status: predicate(stdout) ? "ok" : "failed",
    detail: stdout.trim().slice(0, 500) || args.join(" ")
  };
}

function commandExists(command) {
  return spawnSync("sh", ["-lc", `command -v ${quote(command)}`], {
    stdio: "ignore"
  }).status === 0;
}

function quote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}
