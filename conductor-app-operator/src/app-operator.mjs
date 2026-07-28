export const OperatorStatus = Object.freeze({
  SUCCEEDED: "succeeded",
  BLOCKED: "blocked",
  AWAITING_APPROVAL: "awaiting_approval",
  NEEDS_HANDOFF: "needs_handoff",
  FAILED_VERIFICATION: "failed_verification"
});

export const RouteStatus = Object.freeze({
  READY: "ready",
  NEEDS_LOGIN: "needs_login",
  NEEDS_GRANT: "needs_grant",
  BLOCKED: "blocked"
});

export const defaultWhitelist = new Set([
  "com.google.android.apps.messaging",
  "com.google.android.apps.maps",
  "com.example.notes",
  "com.example.community"
]);

export const defaultPlaybooks = {
  draftMessage: {
    id: "draftMessage",
    packageName: "com.google.android.apps.messaging",
    actionType: "outbound_message.create_draft",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["recipient", "body"],
    steps: [
      { kind: "type", targetLabel: "To", valuePath: "recipient" },
      { kind: "type", targetLabel: "Message", valuePath: "body" }
    ],
    verify: { nodeLabel: "Message", containsPath: "body" }
  },
  sendMessage: {
    id: "sendMessage",
    packageName: "com.google.android.apps.messaging",
    actionType: "outbound_message.send",
    sensitive: true,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["body"],
    exactApprovalPath: "body",
    steps: [
      { kind: "click", targetLabel: "Send" }
    ],
    verify: { nodeLabel: "Sent", containsPath: "body" }
  },
  openRoute: {
    id: "openRoute",
    packageName: "com.google.android.apps.maps",
    actionType: "maps.open_route",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["destination"],
    steps: [
      { kind: "type", targetLabel: "Search here", valuePath: "destination" },
      { kind: "click", targetLabel: "Directions" }
    ],
    verify: { nodeLabel: "Route", containsPath: "destination" }
  },
  appendNote: {
    id: "appendNote",
    packageName: "com.example.notes",
    actionType: "notes.append",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["title", "body"],
    steps: [
      { kind: "type", targetLabel: "Find note", valuePath: "title" },
      { kind: "type", targetLabel: "Note body", valuePath: "body" },
      { kind: "click", targetLabel: "Save" }
    ],
    verify: { nodeLabel: "Saved note", containsPath: "body" }
  },
  communityPost: {
    id: "communityPost",
    packageName: "com.example.community",
    actionType: "public_post.create",
    sensitive: true,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["body"],
    exactApprovalPath: "body",
    steps: [
      { kind: "type", targetLabel: "Post body", valuePath: "body" },
      { kind: "click", targetLabel: "Post" }
    ],
    verify: { nodeLabel: "Posted", containsPath: "body" }
  }
};

export const defaultAgentSurfaces = [
  {
    id: "messages.agent",
    appName: "Messages",
    packageName: "com.google.android.apps.messaging",
    loginState: "logged_in",
    autonomyMode: "draft_only",
    allowedPlaybookIds: ["draftMessage", "sendMessage"],
    supportedActionTypes: ["outbound_message.create_draft", "outbound_message.send"],
    accountProofLabel: "Signed in as Alex",
    requiredSources: ["device_contacts"],
    revoked: false,
    expiresAt: "2027-07-27T18:00:00-05:00"
  },
  {
    id: "maps.agent",
    appName: "Maps",
    packageName: "com.google.android.apps.maps",
    loginState: "logged_in",
    autonomyMode: "low_risk_auto",
    allowedPlaybookIds: ["openRoute"],
    supportedActionTypes: ["maps.open_route"],
    accountProofLabel: "Signed in as Alex",
    requiredSources: ["maps"],
    revoked: false,
    expiresAt: "2027-07-27T18:00:00-05:00"
  },
  {
    id: "notes.agent",
    appName: "Notes",
    packageName: "com.example.notes",
    loginState: "logged_in",
    autonomyMode: "draft_only",
    allowedPlaybookIds: ["appendNote"],
    supportedActionTypes: ["notes.append"],
    accountProofLabel: "Signed in as Alex",
    requiredSources: [],
    revoked: false,
    expiresAt: "2027-07-27T18:00:00-05:00"
  },
  {
    id: "community.agent",
    appName: "Community",
    packageName: "com.example.community",
    loginState: "logged_in",
    autonomyMode: "draft_only",
    allowedPlaybookIds: ["communityPost"],
    supportedActionTypes: ["public_post.create"],
    accountProofLabel: "Signed in as Alex",
    requiredSources: ["facebook_events"],
    revoked: false,
    expiresAt: "2027-07-27T18:00:00-05:00"
  }
];

export class AppOperator {
  constructor({
    whitelist = defaultWhitelist,
    playbooks = defaultPlaybooks,
    agentSurfaces = defaultAgentSurfaces,
    audit = [],
    now = () => new Date(),
    consumedApprovalIds = new Set()
  } = {}) {
    this.whitelist = whitelist;
    this.playbooks = playbooks;
    this.agentSurfaces = agentSurfaces;
    this.audit = audit;
    this.now = now;
    this.consumedApprovalIds = consumedApprovalIds;
  }

  onboardAppAgent({
    playbook,
    appName,
    loginState = "logged_in",
    autonomyMode = "draft_only",
    allowedSourceIds = [],
    expiresAt = "2027-07-27T18:00:00-05:00",
    observedTree = null
  }) {
    const validation = validatePlaybook(playbook);
    if (!validation.valid) {
      this.#record("app_agent.onboarding_blocked", `${playbook?.id ?? "unknown"}: ${validation.errors.join(", ")}`);
      return { status: OperatorStatus.BLOCKED, reason: "invalid_playbook", errors: validation.errors };
    }

    const dryRun = observedTree
      ? dryRunPlaybook({ playbook, tree: observedTree })
      : { verified: false, reason: "observed_tree_required" };
    if (!dryRun.verified) {
      this.#record("app_agent.onboarding_blocked", `${playbook.id}: ${dryRun.reason}`);
      return { status: OperatorStatus.BLOCKED, reason: "playbook_dry_run_failed", dryRun };
    }

    this.playbooks = { ...this.playbooks, [playbook.id]: playbook };
    this.whitelist.add(playbook.packageName);

    const surface = createAgentSurfaceFromPlaybook({
      playbook,
      appName,
      loginState,
      autonomyMode,
      allowedSourceIds,
      expiresAt
    });
    this.agentSurfaces = mergeAgentSurface(this.agentSurfaces, surface);
    this.#record("app_agent.playbook_dry_run_verified", `${surface.packageName}:${playbook.id}`);
    this.#record("app_agent.onboarded", `${surface.packageName}:${playbook.id}`);

    return { status: OperatorStatus.SUCCEEDED, surface, playbook, dryRun };
  }

  revokeAppAgent({ packageName, playbookId = null }) {
    const existing = this.agentSurfaces.find((surface) => surface.packageName === packageName);
    if (!existing) {
      this.#record("app_agent.revocation_blocked", `${packageName}:missing_surface`);
      return { status: OperatorStatus.BLOCKED, reason: "missing_app_agent_surface" };
    }

    this.agentSurfaces = this.agentSurfaces.map((surface) => {
      if (surface.packageName !== packageName) return surface;
      if (playbookId == null) {
        return {
          ...surface,
          loginState: "logged_out",
          revoked: true,
          allowedPlaybookIds: [],
          supportedActionTypes: [],
          requiredSources: []
        };
      }
      const playbook = this.playbooks[playbookId];
      return {
        ...surface,
        revoked: surface.allowedPlaybookIds.length <= 1,
        allowedPlaybookIds: surface.allowedPlaybookIds.filter((id) => id !== playbookId),
        supportedActionTypes: playbook
          ? surface.supportedActionTypes.filter((actionType) => actionType !== playbook.actionType)
          : surface.supportedActionTypes
      };
    });

    this.#record("app_agent.revoked", playbookId ? `${packageName}:${playbookId}` : packageName);
    return { status: OperatorStatus.SUCCEEDED, packageName, playbookId };
  }

  routeAction({ actionType, preferredPackageName = null, requiredSources = [] }) {
    const candidates = this.agentSurfaces
      .filter((surface) => surface.supportedActionTypes.includes(actionType))
      .filter((surface) => preferredPackageName == null || surface.packageName === preferredPackageName);

    if (candidates.length === 0) {
      this.#record("agent_route.blocked", `No app agent supports ${actionType}`);
      return { status: RouteStatus.BLOCKED, reason: "no_capable_app_agent", actionType };
    }

    if (candidates.every((surface) => surface.revoked)) {
      this.#record("agent_route.blocked", `${actionType} app agent revoked`);
      return { status: RouteStatus.BLOCKED, reason: "app_agent_revoked", actionType, candidates };
    }

    const whitelisted = candidates.find((surface) => !surface.revoked && this.whitelist.has(surface.packageName));
    if (!whitelisted) {
      this.#record("agent_route.blocked", `${actionType} has no whitelisted app agent`);
      return { status: RouteStatus.BLOCKED, reason: "package_not_whitelisted", actionType, candidates };
    }

    if (whitelisted.loginState !== "logged_in") {
      this.#record("agent_route.handoff", `${whitelisted.packageName} requires login`);
      return { status: RouteStatus.NEEDS_LOGIN, reason: "login_required", actionType, surface: whitelisted };
    }

    if (isExpired(whitelisted.expiresAt, this.now())) {
      this.#record("agent_route.session_expired", `${actionType} -> ${whitelisted.packageName}:${whitelisted.expiresAt}`);
      return { status: RouteStatus.NEEDS_LOGIN, reason: "session_expired", actionType, surface: whitelisted };
    }

    const playbook = Object.values(this.playbooks).find((item) =>
      item.packageName === whitelisted.packageName &&
      item.actionType === actionType &&
      whitelisted.allowedPlaybookIds.includes(item.id)
    );

    if (!playbook) {
      this.#record("agent_route.handoff", `${whitelisted.packageName} needs playbook grant for ${actionType}`);
      return { status: RouteStatus.NEEDS_GRANT, reason: "playbook_grant_required", actionType, surface: whitelisted };
    }

    const missingSources = requiredSources.filter((source) => !(whitelisted.requiredSources ?? []).includes(source));
    if (missingSources.length > 0) {
      this.#record("agent_route.handoff", `${whitelisted.packageName} missing sources: ${missingSources.join(",")}`);
      return {
        status: RouteStatus.NEEDS_GRANT,
        reason: "source_grant_required",
        actionType,
        surface: whitelisted,
        playbook,
        missingSources
      };
    }

    this.#record("agent_route.ready", `${actionType} -> ${whitelisted.packageName}:${playbook.id}`);
    return { status: RouteStatus.READY, actionType, surface: whitelisted, playbook };
  }

  runRouted({ actionType, tree, input = {}, approval = null, preferredPackageName = null, requiredSources = [] }) {
    const route = this.routeAction({ actionType, preferredPackageName, requiredSources });
    if (route.status !== RouteStatus.READY) {
      return {
        status: route.status === RouteStatus.BLOCKED ? OperatorStatus.BLOCKED : OperatorStatus.NEEDS_HANDOFF,
        reason: route.reason,
        route
      };
    }

    return {
      route,
      ...this.run({
        packageName: route.surface.packageName,
        playbookId: route.playbook.id,
        tree,
        input,
        approval
      })
    };
  }

  run({ packageName, playbookId, tree, input = {}, approval = null }) {
    if (!this.whitelist.has(packageName)) {
      this.#record("operator.blocked", `Package not whitelisted: ${packageName}`);
      return { status: OperatorStatus.BLOCKED, reason: "package_not_whitelisted" };
    }

    const playbook = this.playbooks[playbookId];
    if (!playbook || playbook.packageName !== packageName) {
      this.#record("operator.blocked", `Playbook not allowed: ${playbookId}`);
      return { status: OperatorStatus.BLOCKED, reason: "playbook_not_allowed" };
    }

    const missingInputs = (playbook.requiredInputPaths ?? []).filter((key) => isBlank(input[key]));
    if (missingInputs.length > 0) {
      this.#record("operator.handoff", `${playbook.id} missing inputs: ${missingInputs.join(",")}`);
      return {
        status: OperatorStatus.NEEDS_HANDOFF,
        reason: "missing_required_input",
        missingInputs
      };
    }

    const accountProof = verifyAccountProof(tree, playbook.accountProofLabel);
    if (!accountProof.verified) {
      this.#record("operator.account_proof_handoff", `${playbook.id}: ${accountProof.reason}`);
      return {
        status: OperatorStatus.NEEDS_HANDOFF,
        reason: accountProof.reason,
        accountProofLabel: playbook.accountProofLabel
      };
    }

    const approvalCard = playbook.sensitive
      ? {
          id: `approval_operator_${playbook.id}`,
          actionType: playbook.actionType,
          exactContent: input[playbook.exactApprovalPath] ?? null
        }
      : null;
    const approvalReceiptId = approval?.approvalId ?? approval?.id ?? approvalCard?.id;

    if (playbook.sensitive && approval?.status !== "approved") {
      this.#record("operator.approval_queued", `${playbook.id} requires approval`);
      return {
        status: OperatorStatus.AWAITING_APPROVAL,
        approval: approvalCard
      };
    }

    if (playbook.sensitive && this.consumedApprovalIds.has(approvalReceiptId)) {
      this.#record("operator.approval_replay_blocked", `${playbook.id}:${approvalReceiptId}`);
      return {
        status: OperatorStatus.AWAITING_APPROVAL,
        reason: "approval_receipt_replayed",
        approval: approvalCard
      };
    }

    if (playbook.sensitive && approval?.exactContent !== input[playbook.exactApprovalPath]) {
      this.#record("operator.approval_queued", `${playbook.id} exact approval mismatch`);
      return {
        status: OperatorStatus.AWAITING_APPROVAL,
        reason: "exact_approval_mismatch",
        approval: approvalCard
      };
    }

    const actions = [];
    const workingTree = structuredClone(tree);

    for (const step of playbook.steps) {
      const match = findUniqueNode(workingTree, step.targetLabel);
      if (match.status !== "unique") {
        this.#record("operator.handoff", `${step.targetLabel}: ${match.status}`);
        return {
          status: OperatorStatus.NEEDS_HANDOFF,
          reason: match.status,
          targetLabel: step.targetLabel,
          actions
        };
      }

      const value = step.valuePath ? input[step.valuePath] : null;
      actions.push({ kind: step.kind, nodeId: match.node.id, label: match.node.label, value });
      applyUiAction(workingTree, match.node.id, step.kind, value);
    }

    const verification = verifyTree(workingTree, playbook.verify, input);
    if (!verification.verified) {
      this.#record("operator.failed_verification", `${playbook.id}: ${verification.reason}`);
      return { status: OperatorStatus.FAILED_VERIFICATION, actions, verification };
    }

    this.#record("operator.succeeded", `${packageName}:${playbook.id}`);
    if (playbook.sensitive) {
      this.consumedApprovalIds.add(approvalReceiptId);
      this.#record("operator.approval_consumed", `${playbook.id}:${approvalReceiptId}`);
    }
    return {
      status: OperatorStatus.SUCCEEDED,
      actions,
      verification,
      tree: workingTree
    };
  }

  #record(type, detail) {
    this.audit.unshift({ type, detail });
  }
}

export function findUniqueNode(tree, label) {
  const matches = flattenNodes(tree).filter((node) => node.label === label && node.enabled !== false);
  if (matches.length === 0) return { status: "missing" };
  if (matches.length > 1) return { status: "ambiguous", matches };
  return { status: "unique", node: matches[0] };
}

export function createMessagingTree({ ambiguousSend = false } = {}) {
  return {
    id: "root",
    label: "Messages",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "to", label: "To", role: "textField", value: "" },
      { id: "message", label: "Message", role: "textField", value: "" },
      { id: "send", label: "Send", role: "button", enabled: true },
      ...(ambiguousSend ? [{ id: "send_secondary", label: "Send", role: "button", enabled: true }] : []),
      { id: "sent", label: "Sent", role: "status", value: "" }
    ]
  };
}

export function createMapsTree() {
  return {
    id: "root",
    label: "Maps",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "search", label: "Search here", role: "textField", value: "" },
      { id: "directions", label: "Directions", role: "button", enabled: true },
      { id: "route", label: "Route", role: "status", value: "" }
    ]
  };
}

export function createNotesTree() {
  return {
    id: "root",
    label: "Notes",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "find_note", label: "Find note", role: "textField", value: "" },
      { id: "note_body", label: "Note body", role: "textField", value: "" },
      { id: "save", label: "Save", role: "button", enabled: true },
      { id: "saved_note", label: "Saved note", role: "status", value: "" }
    ]
  };
}

export function createCommunityTree() {
  return {
    id: "root",
    label: "Community",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "post_body", label: "Post body", role: "textField", value: "" },
      { id: "post", label: "Post", role: "button", enabled: true },
      { id: "posted", label: "Posted", role: "status", value: "" }
    ]
  };
}

function flattenNodes(node) {
  return [node, ...(node.children ?? []).flatMap(flattenNodes)];
}

function applyUiAction(tree, nodeId, kind, value) {
  for (const node of flattenNodes(tree)) {
    if (node.id !== nodeId) continue;
    if (kind === "type") node.value = value;
    if (kind === "click" && node.label === "Send") {
      const message = flattenNodes(tree).find((candidate) => candidate.label === "Message")?.value ?? "";
      const sent = flattenNodes(tree).find((candidate) => candidate.label === "Sent");
      if (sent) sent.value = message;
    }
    if (kind === "click" && node.label === "Directions") {
      const destination = flattenNodes(tree).find((candidate) => candidate.label === "Search here")?.value ?? "";
      const route = flattenNodes(tree).find((candidate) => candidate.label === "Route");
      if (route) route.value = destination;
    }
    if (kind === "click" && node.label === "Save") {
      const noteBody = flattenNodes(tree).find((candidate) => candidate.label === "Note body")?.value ?? "";
      const saved = flattenNodes(tree).find((candidate) => candidate.label === "Saved note");
      if (saved) saved.value = noteBody;
    }
    if (kind === "click" && node.label === "Post") {
      const body = flattenNodes(tree).find((candidate) => candidate.label === "Post body")?.value ?? "";
      const posted = flattenNodes(tree).find((candidate) => candidate.label === "Posted");
      if (posted) posted.value = body;
    }
  }
}

function verifyTree(tree, verifier, input) {
  const match = findUniqueNode(tree, verifier.nodeLabel);
  if (match.status !== "unique") return { verified: false, reason: match.status };
  const expected = input[verifier.containsPath];
  if (expected && !String(match.node.value ?? "").includes(expected)) {
    return { verified: false, reason: "expected_value_missing" };
  }
  return { verified: true, method: "accessibility_tree_state" };
}

export function verifyAccountProof(tree, accountProofLabel) {
  if (isBlank(accountProofLabel)) {
    return { verified: false, reason: "account_proof_required" };
  }
  const match = findUniqueNode(tree, accountProofLabel);
  if (match.status !== "unique") {
    return { verified: false, reason: `account_proof_${match.status}` };
  }
  return { verified: true, method: "accessibility_account_proof", label: accountProofLabel };
}

function isBlank(value) {
  return value == null || String(value).trim() === "";
}

export function validatePlaybook(playbook) {
  const errors = [];
  for (const field of ["id", "packageName", "actionType", "steps", "verify"]) {
    if (playbook?.[field] == null || playbook[field] === "") errors.push(`Missing ${field}`);
  }
  if (!Array.isArray(playbook?.requiredInputPaths)) errors.push("requiredInputPaths must be an array");
  if (!Array.isArray(playbook?.steps) || playbook.steps.length === 0) errors.push("steps must be a non-empty array");
  if (playbook?.steps?.some((step) => !step.kind || !step.targetLabel)) {
    errors.push("each step must declare kind and targetLabel");
  }
  if (!playbook?.verify?.nodeLabel || !playbook?.verify?.containsPath) {
    errors.push("verify must declare nodeLabel and containsPath");
  }
  if (isBlank(playbook?.accountProofLabel)) {
    errors.push("accountProofLabel must identify the signed-in account UI");
  }
  if (playbook?.sensitive === true && !playbook?.exactApprovalPath) {
    errors.push("sensitive playbooks must declare exactApprovalPath");
  }
  if (requiresExactApprovalRisk(playbook) && playbook?.sensitive !== true) {
    errors.push("public or high-risk playbooks must be sensitive");
  }
  if (requiresExactApprovalRisk(playbook) && !playbook?.exactApprovalPath) {
    errors.push("public or high-risk playbooks must declare exactApprovalPath");
  }
  if (requiresExactApprovalRisk(playbook) && !playbook?.requiredInputPaths?.includes(playbook.exactApprovalPath)) {
    errors.push("public or high-risk playbooks must require their exact approval input");
  }
  return { valid: errors.length === 0, errors };
}

function requiresExactApprovalRisk(playbook) {
  return playbook?.actionType === "public_post.create" ||
    String(playbook?.riskLabel ?? "").includes("public") ||
    String(playbook?.riskLabel ?? "").startsWith("high_");
}

export function dryRunPlaybook({ playbook, tree }) {
  const validation = validatePlaybook(playbook);
  if (!validation.valid) {
    return { verified: false, reason: "invalid_playbook", errors: validation.errors };
  }
  if (!tree) {
    return { verified: false, reason: "observed_tree_required" };
  }

  const checkedTargets = [];
  for (const step of playbook.steps) {
    const match = findUniqueNode(tree, step.targetLabel);
    checkedTargets.push(step.targetLabel);
    if (match.status !== "unique") {
      return {
        verified: false,
        reason: `target_${match.status}`,
        targetLabel: step.targetLabel,
        checkedTargets
      };
    }
  }

  const verifier = findUniqueNode(tree, playbook.verify.nodeLabel);
  if (verifier.status !== "unique") {
    return {
      verified: false,
      reason: `verifier_${verifier.status}`,
      targetLabel: playbook.verify.nodeLabel,
      checkedTargets
    };
  }

  const accountProof = verifyAccountProof(tree, playbook.accountProofLabel);
  if (!accountProof.verified) {
    return {
      verified: false,
      reason: accountProof.reason,
      targetLabel: playbook.accountProofLabel,
      checkedTargets
    };
  }

  return {
    verified: true,
    method: "observed_accessibility_tree_dry_run",
    checkedTargets: [...checkedTargets, playbook.accountProofLabel],
    verifier: playbook.verify.nodeLabel
  };
}

export function createAgentSurfaceFromPlaybook({
  playbook,
  appName,
  loginState = "logged_in",
  autonomyMode = "draft_only",
  allowedSourceIds = [],
  expiresAt = "2027-07-27T18:00:00-05:00"
}) {
  return {
    id: `${playbook.packageName}.agent`,
    appName: appName ?? playbook.packageName,
    packageName: playbook.packageName,
    loginState,
    autonomyMode,
    allowedPlaybookIds: [playbook.id],
    supportedActionTypes: [playbook.actionType],
    accountProofLabel: playbook.accountProofLabel,
    requiredSources: [...allowedSourceIds],
    revoked: false,
    expiresAt
  };
}

function mergeAgentSurface(surfaces, next) {
  const existing = surfaces.find((surface) => surface.packageName === next.packageName);
  if (!existing) return [...surfaces, next];
  return surfaces.map((surface) => {
    if (surface.packageName !== next.packageName) return surface;
    return {
      ...surface,
      appName: next.appName,
      loginState: next.loginState,
      autonomyMode: next.autonomyMode,
      allowedPlaybookIds: [...new Set([...surface.allowedPlaybookIds, ...next.allowedPlaybookIds])],
      supportedActionTypes: [...new Set([...surface.supportedActionTypes, ...next.supportedActionTypes])],
      accountProofLabel: next.accountProofLabel ?? surface.accountProofLabel,
      requiredSources: [...new Set([...(surface.requiredSources ?? []), ...(next.requiredSources ?? [])])],
      revoked: false,
      expiresAt: next.expiresAt ?? surface.expiresAt
    };
  });
}

export function isExpired(expiresAt, now = new Date()) {
  if (!expiresAt) return false;
  const expiresAtMs = Date.parse(expiresAt);
  const nowMs = now instanceof Date ? now.getTime() : Date.parse(now);
  if (Number.isNaN(expiresAtMs) || Number.isNaN(nowMs)) return true;
  return expiresAtMs <= nowMs;
}
