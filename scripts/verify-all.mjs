#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = new Set(process.argv.slice(2));
const jsOnly = args.has("--js-only");
const androidStaticOnly = args.has("--android-static-only");

const jsPackages = [
  "conductor-action-sdk",
  "conductor-os-simulator",
  "conductor-personal-graph",
  "conductor-connectors",
  "conductor-app-operator",
  "conductor-voice-runtime",
  "conductor-os-orchestrator",
  "conductor-evals",
  "conductor-realtime-token-service",
  "conductor-runtime-core"
];

const androidStatic = [
  "verify_scaffold.js",
  "static_build_config_check.js",
  "static_kotlin_source_check.js"
];

function run(command, cwd, label) {
  console.log(`\n===== ${label} =====`);
  const result = spawnSync(command, {
    cwd,
    shell: true,
    stdio: "inherit",
    env: process.env
  });
  if (result.status !== 0) {
    console.error(`\nFAILED: ${label}`);
    process.exit(result.status || 1);
  }
}

if (!androidStaticOnly) {
  for (const dir of jsPackages) {
    run("npm test", path.join(root, dir), dir);
  }
}

if (!jsOnly) {
  const androidRoot = path.join(root, "conductor-android-prototype");
  for (const script of androidStatic) {
    run(`node ${script}`, androidRoot, `android:${script}`);
  }
  run("node native_preflight.js", androidRoot, "android:native_preflight.js");
  run("node device_smoke_test.js", androidRoot, "android:device_smoke_test.js");
}

console.log("\nConductor verify-all completed.");
