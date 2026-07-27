import test from "node:test";
import assert from "node:assert/strict";
import { runEvals, scenarios } from "../src/run-evals.mjs";

test("all eval scenarios pass", async () => {
  const report = await runEvals();

  assert.equal(report.total, scenarios.length);
  assert.equal(report.passed, report.total);
});

test("eval suite covers the required product promises", () => {
  const ids = new Set(scenarios.map((scenario) => scenario.id));

  assert.equal(ids.has("outdoor_uses_cross_app_context"), true);
  assert.equal(ids.has("draft_mode_pauses_sensitive_actions"), true);
  assert.equal(ids.has("approval_executes_only_selected_action"), true);
  assert.equal(ids.has("ask_only_blocks_side_effects"), true);
  assert.equal(ids.has("sdk_blocks_purchase_action"), true);
  assert.equal(ids.has("sdk_requires_exact_approval_for_public_post"), true);
  assert.equal(ids.has("sdk_blocks_replayed_approval_receipt"), true);
  assert.equal(ids.has("autonomy_profile_blocks_ask_only_snapshots"), true);
  assert.equal(ids.has("orchestrator_ask_only_stops_before_cross_app_context"), true);
  assert.equal(ids.has("personal_graph_scopes_cross_app_context"), true);
  assert.equal(ids.has("personal_graph_denies_unapproved_purpose"), true);
  assert.equal(ids.has("personal_graph_enforces_app_agent_grants"), true);
  assert.equal(ids.has("voice_handoff_preserves_user_intent"), true);
  assert.equal(ids.has("voice_interruption_stops_assistant_turn"), true);
  assert.equal(ids.has("app_operator_requires_approval_for_send"), true);
  assert.equal(ids.has("app_operator_blocks_replayed_approval_receipt"), true);
  assert.equal(ids.has("app_operator_stops_on_ambiguous_ui"), true);
  assert.equal(ids.has("app_operator_operates_custom_app_with_declared_inputs"), true);
  assert.equal(ids.has("app_operator_requires_exact_approval_for_custom_public_post"), true);
  assert.equal(ids.has("app_operator_rejects_silent_public_post_playbook"), true);
  assert.equal(ids.has("app_operator_routes_intent_to_logged_in_app_agent"), true);
  assert.equal(ids.has("app_operator_requires_live_account_proof"), true);
  assert.equal(ids.has("app_operator_onboards_user_taught_app_agent"), true);
  assert.equal(ids.has("app_operator_revokes_user_taught_app_agent"), true);
  assert.equal(ids.has("app_operator_expires_user_taught_app_agent"), true);
  assert.equal(ids.has("orchestrator_runs_spoken_task_to_approved_app_action"), true);
  assert.equal(ids.has("connectors_hydrate_orchestrator_without_exposing_tokens"), true);
});
