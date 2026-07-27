#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const root = __dirname;
const strict = process.argv.includes("--strict");

const checks = [
  nodeScriptCheck("static_build_config", "static_build_config_check.js"),
  nodeScriptCheck("static_kotlin_source", "static_kotlin_source_check.js"),
  fileCheck("settings_gradle", "settings.gradle.kts"),
  fileCheck("root_build_gradle", "build.gradle.kts"),
  fileCheck("app_build_gradle", "app/build.gradle.kts"),
  fileCheck("manifest", "app/src/main/AndroidManifest.xml"),
  textCheck("home_launcher", "app/src/main/AndroidManifest.xml", "android.intent.category.HOME"),
  textCheck("microphone_permission", "app/src/main/AndroidManifest.xml", "android.permission.RECORD_AUDIO"),
  textCheck("accessibility_service", "app/src/main/AndroidManifest.xml", "android.permission.BIND_ACCESSIBILITY_SERVICE"),
  executableCheck("gradle_or_wrapper", [
    path.join(root, "gradlew"),
    "gradle"
  ]),
  androidSdkCheck(),
  executableCheck("adb", ["adb", sdkTool("platform-tools", "adb")]),
  executableCheck("emulator", ["emulator", sdkTool("emulator", "emulator")]),
  fileOrEnvCheck("local_properties_or_android_home", "local.properties", ["ANDROID_HOME", "ANDROID_SDK_ROOT"])
];

const failed = checks.filter((check) => check.status !== "ok");
const result = {
  status: failed.length === 0 ? "ready" : "blocked",
  strict,
  checks,
  nextCommands: [
    "./gradlew :app:assembleDebug",
    "adb install -r app/build/outputs/apk/debug/app-debug.apk",
    "node device_smoke_test.js --strict",
    "adb shell cmd package resolve-activity --brief app.conductor.prototype",
    "adb shell settings get secure enabled_accessibility_services"
  ]
};

console.log(JSON.stringify(result, null, 2));

if (strict && failed.length > 0) {
  process.exit(1);
}

function fileCheck(name, relativePath) {
  const fullPath = path.join(root, relativePath);
  return {
    name,
    status: fs.existsSync(fullPath) ? "ok" : "missing",
    detail: relativePath
  };
}

function nodeScriptCheck(name, relativePath) {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    return { name, status: "missing", detail: relativePath };
  }
  const result = spawnSync(process.execPath, [fullPath], {
    cwd: root,
    encoding: "utf8"
  });
  return {
    name,
    status: result.status === 0 ? "ok" : "failed",
    detail: relativePath
  };
}

function textCheck(name, relativePath, expectedText) {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    return { name, status: "missing", detail: relativePath };
  }
  const body = fs.readFileSync(fullPath, "utf8");
  return {
    name,
    status: body.includes(expectedText) ? "ok" : "missing_text",
    detail: expectedText
  };
}

function executableCheck(name, candidates) {
  for (const candidate of candidates) {
    if (!candidate) continue;
    if (candidate.includes(path.sep) && fs.existsSync(candidate)) {
      return { name, status: "ok", detail: candidate };
    }
    if (!candidate.includes(path.sep) && commandExists(candidate)) {
      return { name, status: "ok", detail: candidate };
    }
  }
  return { name, status: "missing", detail: candidates.filter(Boolean).join(" or ") };
}

function androidSdkCheck() {
  const sdkPath = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || "";
  return {
    name: "android_sdk",
    status: sdkPath && fs.existsSync(sdkPath) ? "ok" : "missing",
    detail: sdkPath || "ANDROID_HOME or ANDROID_SDK_ROOT"
  };
}

function fileOrEnvCheck(name, relativePath, envNames) {
  const fullPath = path.join(root, relativePath);
  const envName = envNames.find((item) => process.env[item]);
  if (fs.existsSync(fullPath)) {
    return { name, status: "ok", detail: relativePath };
  }
  if (envName) {
    return { name, status: "ok", detail: envName };
  }
  return { name, status: "missing", detail: `${relativePath} or ${envNames.join("/")}` };
}

function sdkTool(...parts) {
  const sdkPath = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  return sdkPath ? path.join(sdkPath, ...parts) : "";
}

function commandExists(command) {
  return spawnSync("sh", ["-lc", `command -v ${quote(command)}`], {
    stdio: "ignore"
  }).status === 0;
}

function quote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}
