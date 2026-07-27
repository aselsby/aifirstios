import { runOutdoorActivityWorkflow } from "./runtime.js";

const result = await runOutdoorActivityWorkflow();

console.log(JSON.stringify({
  goal: result.task.goal,
  mode: result.task.mode,
  recommendation: result.plan.recommendation.title,
  firstPassResults: result.firstPassResults,
  approvedResults: result.approvedResults,
  auditEvents: result.audit.length
}, null, 2));
