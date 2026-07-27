import { ApprovalMode } from "./action-manifest.mjs";
import { AutonomyMode, profileForMode } from "./autonomy-profile.mjs";

export { AutonomyMode } from "./autonomy-profile.mjs";

export const Decision = Object.freeze({
  ALLOW: "allow",
  REQUIRE_APPROVAL: "require_approval",
  BLOCK: "block"
});

const blockedInMvp = new Set([
  "purchase.create",
  "data.delete",
  "account_security.change"
]);

const exactApprovalActions = new Set([
  "public_post.create",
  "outbound_message.send",
  "email.send",
  "location.share"
]);

export function decideAction(action, mode = AutonomyMode.DRAFT_ONLY) {
  const profile = profileForMode(mode);

  if (!action) {
    return { decision: Decision.BLOCK, reason: "Unknown action." };
  }

  if (blockedInMvp.has(action.actionType)) {
    return { decision: Decision.BLOCK, reason: "Blocked in MVP." };
  }

  if (!profile.allowsExternalSideEffects && action.externalSideEffect) {
    return { decision: Decision.BLOCK, reason: `${profile.mode} blocks external side effects.` };
  }

  if (exactApprovalActions.has(action.actionType) || action.approval === ApprovalMode.ALWAYS) {
    return { decision: Decision.REQUIRE_APPROVAL, reason: "Exact approval required for this external action." };
  }

  if (action.externalSideEffect && profile.approvalRequiredForExternalSideEffects) {
    return { decision: Decision.REQUIRE_APPROVAL, reason: `${profile.mode} requires approval for external side effects.` };
  }

  if (!profile.autoRisk.has(action.risk)) {
    return { decision: Decision.REQUIRE_APPROVAL, reason: `${profile.mode} requires approval for ${action.risk}-risk actions.` };
  }

  return { decision: Decision.ALLOW, reason: "Allowed by policy." };
}

export function decideDataSnapshot({ appAgentGrant, requestedSources = [] } = {}, mode = AutonomyMode.DRAFT_ONLY) {
  const profile = profileForMode(mode);
  if (!profile.allowsDataSnapshots) {
    return { decision: Decision.BLOCK, reason: `${profile.mode} blocks model data snapshots.` };
  }
  if (!appAgentGrant || appAgentGrant.revoked) {
    return { decision: Decision.BLOCK, reason: "Missing or revoked app-agent grant." };
  }
  const sourceDenied = requestedSources.some((source) => !appAgentGrant.sources?.includes(source));
  if (sourceDenied) {
    return { decision: Decision.BLOCK, reason: "Requested source outside app-agent grant." };
  }
  return { decision: Decision.ALLOW, reason: "Allowed by autonomy profile and app-agent grant." };
}
