#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;

const checks = [
  textCheck("compose_compiler_plugin_declared", "build.gradle.kts", "org.jetbrains.kotlin.plugin.compose"),
  textCheck("compose_compiler_plugin_applied", "app/build.gradle.kts", "id(\"org.jetbrains.kotlin.plugin.compose\")"),
  absentTextCheck("legacy_compose_compiler_extension_removed", "app/build.gradle.kts", "kotlinCompilerExtensionVersion"),
  textCheck("java_17_compile_options", "app/build.gradle.kts", "JavaVersion.VERSION_17"),
  textCheck("kotlin_jvm_target_17", "app/build.gradle.kts", "jvmTarget = \"17\""),
  textCheck("encrypted_preferences_dependency", "app/build.gradle.kts", "androidx.security:security-crypto"),
  textCheck("manifest_theme_exists", "app/src/main/res/values/styles.xml", "Theme.Conductor"),
  textCheck("accessibility_description_exists", "app/src/main/res/values/strings.xml", "accessibility_description"),
  textCheck("accessibility_config_retrieve_windows", "app/src/main/res/xml/conductor_accessibility_service.xml", "flagRetrieveInteractiveWindows")
];

const failed = checks.filter((check) => check.status !== "ok");
const result = {
  status: failed.length === 0 ? "ok" : "failed",
  checks
};

console.log(JSON.stringify(result, null, 2));

if (failed.length > 0) {
  process.exit(1);
}

function textCheck(name, relativePath, expectedText) {
  const body = read(relativePath);
  return {
    name,
    status: body.includes(expectedText) ? "ok" : "missing_text",
    detail: expectedText
  };
}

function absentTextCheck(name, relativePath, forbiddenText) {
  const body = read(relativePath);
  return {
    name,
    status: body.includes(forbiddenText) ? "forbidden_text_present" : "ok",
    detail: forbiddenText
  };
}

function read(relativePath) {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) return "";
  return fs.readFileSync(fullPath, "utf8");
}
