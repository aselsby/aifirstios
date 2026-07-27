import { runEndToEndOutdoorTask } from "./orchestrator.mjs";

console.log(JSON.stringify({
  awaitingApproval: await runEndToEndOutdoorTask(),
  approved: await runEndToEndOutdoorTask({ approveSend: true }),
  connectorBacked: await runEndToEndOutdoorTask({ approveSend: true, useConnectors: true })
}, null, 2));
