import { Risk } from "./action-manifest.mjs";

export const AutonomyMode = Object.freeze({
  ASK_ONLY: "ask_only",
  DRAFT_ONLY: "draft_only",
  LOW_RISK_AUTO: "low_risk_auto",
  TRUSTED_AUTO: "trusted_auto"
});

export const autonomyProfiles = Object.freeze({
  [AutonomyMode.ASK_ONLY]: Object.freeze({
    mode: AutonomyMode.ASK_ONLY,
    allowsExternalSideEffects: false,
    allowsDataSnapshots: false,
    autoRisk: new Set([Risk.LOW]),
    approvalRequiredForExternalSideEffects: true
  }),
  [AutonomyMode.DRAFT_ONLY]: Object.freeze({
    mode: AutonomyMode.DRAFT_ONLY,
    allowsExternalSideEffects: true,
    allowsDataSnapshots: true,
    autoRisk: new Set([Risk.LOW]),
    approvalRequiredForExternalSideEffects: true
  }),
  [AutonomyMode.LOW_RISK_AUTO]: Object.freeze({
    mode: AutonomyMode.LOW_RISK_AUTO,
    allowsExternalSideEffects: true,
    allowsDataSnapshots: true,
    autoRisk: new Set([Risk.LOW]),
    approvalRequiredForExternalSideEffects: true
  }),
  [AutonomyMode.TRUSTED_AUTO]: Object.freeze({
    mode: AutonomyMode.TRUSTED_AUTO,
    allowsExternalSideEffects: true,
    allowsDataSnapshots: true,
    autoRisk: new Set([Risk.LOW, Risk.MEDIUM]),
    approvalRequiredForExternalSideEffects: false
  })
});

export function profileForMode(mode = AutonomyMode.DRAFT_ONLY) {
  return autonomyProfiles[mode] ?? autonomyProfiles[AutonomyMode.DRAFT_ONLY];
}

export function describeAutonomyProfile(mode = AutonomyMode.DRAFT_ONLY) {
  const profile = profileForMode(mode);
  return {
    mode: profile.mode,
    allowsExternalSideEffects: profile.allowsExternalSideEffects,
    allowsDataSnapshots: profile.allowsDataSnapshots,
    autoRisk: [...profile.autoRisk],
    approvalRequiredForExternalSideEffects: profile.approvalRequiredForExternalSideEffects
  };
}
