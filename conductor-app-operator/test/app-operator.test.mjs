import test from "node:test";
import assert from "node:assert/strict";
import {
  AppOperator,
  OperatorStatus,
  RouteStatus,
  createCommunityTree,
  createMapsTree,
  createMessagingTree,
  createNotesTree,
  defaultAgentSurfaces,
  dryRunPlaybook,
  findUniqueNode,
  validatePlaybook,
  verifyAccountProof
} from "../src/app-operator.mjs";

test("draft message playbook types recipient and body", () => {
  const operator = new AppOperator();
  const result = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "draftMessage",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" }
  });

  assert.equal(result.status, OperatorStatus.SUCCEEDED);
  assert.equal(result.actions.length, 2);
  assert.equal(result.verification.verified, true);
});

test("send message requires approval before UI click", () => {
  const operator = new AppOperator();
  const result = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "sendMessage",
    tree: createMessagingTree(),
    input: { body: "Want to go?" }
  });

  assert.equal(result.status, OperatorStatus.AWAITING_APPROVAL);
  assert.equal(result.approval.actionType, "outbound_message.send");
});

test("approved send clicks and verifies sent state", () => {
  const operator = new AppOperator();
  const draft = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "draftMessage",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" }
  });
  const send = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "sendMessage",
    tree: draft.tree,
    input: { body: "Want to go?" },
    approval: { status: "approved", exactContent: "Want to go?" }
  });

  assert.equal(send.status, OperatorStatus.SUCCEEDED);
  assert.equal(send.verification.verified, true);
});

test("approved send consumes exact approval receipt once", () => {
  const operator = new AppOperator();
  const queued = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "sendMessage",
    tree: createMessagingTree(),
    input: { body: "Want to go?" }
  });
  const draft = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "draftMessage",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" }
  });
  const approval = {
    status: "approved",
    approvalId: queued.approval.id,
    exactContent: queued.approval.exactContent
  };
  const first = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "sendMessage",
    tree: draft.tree,
    input: { body: "Want to go?" },
    approval
  });
  const replay = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "sendMessage",
    tree: draft.tree,
    input: { body: "Want to go?" },
    approval
  });

  assert.equal(first.status, OperatorStatus.SUCCEEDED);
  assert.equal(replay.status, OperatorStatus.AWAITING_APPROVAL);
  assert.equal(replay.reason, "approval_receipt_replayed");
  assert.equal(operator.audit.some((event) => event.type === "operator.approval_consumed"), true);
  assert.equal(operator.audit.some((event) => event.type === "operator.approval_replay_blocked"), true);
});

test("unwhitelisted package is blocked", () => {
  const operator = new AppOperator();
  const result = operator.run({
    packageName: "com.example.unknown",
    playbookId: "draftMessage",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" }
  });

  assert.equal(result.status, OperatorStatus.BLOCKED);
  assert.equal(result.reason, "package_not_whitelisted");
});

test("ambiguous UI target requires handoff", () => {
  const operator = new AppOperator();
  const result = operator.run({
    packageName: "com.google.android.apps.messaging",
    playbookId: "sendMessage",
    tree: createMessagingTree({ ambiguousSend: true }),
    input: { body: "Want to go?" },
    approval: { status: "approved", exactContent: "Want to go?" }
  });

  assert.equal(result.status, OperatorStatus.NEEDS_HANDOFF);
  assert.equal(result.reason, "ambiguous");
});

test("maps route playbook opens and verifies route", () => {
  const operator = new AppOperator();
  const result = operator.run({
    packageName: "com.google.android.apps.maps",
    playbookId: "openRoute",
    tree: createMapsTree(),
    input: { destination: "Outdoor Jazz At The Garden" }
  });

  assert.equal(result.status, OperatorStatus.SUCCEEDED);
  assert.equal(result.verification.verified, true);
});

test("agent surface router selects a logged-in capable app before operating UI", () => {
  const operator = new AppOperator();
  const result = operator.runRouted({
    actionType: "maps.open_route",
    tree: createMapsTree(),
    input: { destination: "Outdoor Jazz At The Garden" },
    requiredSources: ["maps"]
  });

  assert.equal(result.route.status, RouteStatus.READY);
  assert.equal(result.route.surface.packageName, "com.google.android.apps.maps");
  assert.equal(result.route.playbook.id, "openRoute");
  assert.equal(result.status, OperatorStatus.SUCCEEDED);
});

test("agent surface router asks for login before operating a logged-out app", () => {
  const loggedOutSurfaces = defaultAgentSurfaces.map((surface) =>
    surface.packageName === "com.google.android.apps.messaging"
      ? { ...surface, loginState: "logged_out" }
      : surface
  );
  const operator = new AppOperator({ agentSurfaces: loggedOutSurfaces });
  const result = operator.runRouted({
    actionType: "outbound_message.create_draft",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" },
    requiredSources: ["device_contacts"]
  });

  assert.equal(result.status, OperatorStatus.NEEDS_HANDOFF);
  assert.equal(result.reason, "login_required");
  assert.equal(result.route.status, RouteStatus.NEEDS_LOGIN);
});

test("agent surface router asks to renew an expired app-agent session", () => {
  const expiredSurfaces = defaultAgentSurfaces.map((surface) =>
    surface.packageName === "com.google.android.apps.messaging"
      ? { ...surface, expiresAt: "2026-07-27T09:00:00-05:00" }
      : surface
  );
  const operator = new AppOperator({
    agentSurfaces: expiredSurfaces,
    now: () => new Date("2026-07-27T10:45:00-05:00")
  });
  const result = operator.runRouted({
    actionType: "outbound_message.create_draft",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" },
    requiredSources: ["device_contacts"]
  });

  assert.equal(result.status, OperatorStatus.NEEDS_HANDOFF);
  assert.equal(result.reason, "session_expired");
  assert.equal(result.route.status, RouteStatus.NEEDS_LOGIN);
  assert.equal(operator.audit.some((event) => event.type === "agent_route.session_expired"), true);
});

test("agent surface router requires a playbook grant before using an app as an agent", () => {
  const ungrantedSurfaces = defaultAgentSurfaces.map((surface) =>
    surface.packageName === "com.google.android.apps.messaging"
      ? { ...surface, allowedPlaybookIds: ["sendMessage"] }
      : surface
  );
  const operator = new AppOperator({ agentSurfaces: ungrantedSurfaces });
  const result = operator.runRouted({
    actionType: "outbound_message.create_draft",
    tree: createMessagingTree(),
    input: { recipient: "Maya Chen", body: "Want to go?" },
    requiredSources: ["device_contacts"]
  });

  assert.equal(result.status, OperatorStatus.NEEDS_HANDOFF);
  assert.equal(result.reason, "playbook_grant_required");
  assert.equal(result.route.status, RouteStatus.NEEDS_GRANT);
});

test("agent surface router checks data-source grants needed for app-agent work", () => {
  const operator = new AppOperator();
  const route = operator.routeAction({
    actionType: "public_post.create",
    requiredSources: ["facebook_events", "calendar"]
  });

  assert.equal(route.status, RouteStatus.NEEDS_GRANT);
  assert.equal(route.reason, "source_grant_required");
  assert.deepEqual(route.missingSources, ["calendar"]);
});

test("app-agent onboarding teaches a previously unknown logged-in app surface", () => {
  const operator = new AppOperator();
  const playbook = {
    id: "tasksAdd",
    packageName: "com.example.tasks",
    actionType: "tasks.add",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["title"],
    steps: [
      { kind: "type", targetLabel: "Task title", valuePath: "title" }
    ],
    verify: { nodeLabel: "Task title", containsPath: "title" }
  };
  const onboarded = operator.onboardAppAgent({
    playbook,
    appName: "Tasks",
    allowedSourceIds: ["calendar"],
    observedTree: {
      id: "root",
      label: "Tasks",
      children: [
        { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
        { id: "title", label: "Task title", role: "textField", value: "" }
      ]
    }
  });
  const result = operator.runRouted({
    actionType: "tasks.add",
    tree: {
      id: "root",
      label: "Tasks",
      children: [
        { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
        { id: "title", label: "Task title", role: "textField", value: "" }
      ]
    },
    input: { title: "Buy picnic blanket" },
    requiredSources: ["calendar"]
  });

  assert.equal(onboarded.status, OperatorStatus.SUCCEEDED);
  assert.equal(onboarded.dryRun.verified, true);
  assert.equal(result.route.status, RouteStatus.READY);
  assert.equal(result.route.surface.packageName, "com.example.tasks");
  assert.equal(result.status, OperatorStatus.SUCCEEDED);
  assert.equal(result.verification.verified, true);
});

test("app-agent onboarding can create a future-dated session grant", () => {
  const operator = new AppOperator({ now: () => new Date("2026-07-27T10:45:00-05:00") });
  const playbook = {
    id: "tasksAdd",
    packageName: "com.example.tasks",
    actionType: "tasks.add",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["title"],
    steps: [
      { kind: "type", targetLabel: "Task title", valuePath: "title" }
    ],
    verify: { nodeLabel: "Task title", containsPath: "title" }
  };
  const tree = {
    id: "root",
    label: "Tasks",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "title", label: "Task title", role: "textField", value: "" }
    ]
  };

  const onboarded = operator.onboardAppAgent({
    playbook,
    appName: "Tasks",
    expiresAt: "2026-07-27T18:00:00-05:00",
    observedTree: tree
  });
  const result = operator.runRouted({
    actionType: "tasks.add",
    tree,
    input: { title: "Buy picnic blanket" }
  });

  assert.equal(onboarded.status, OperatorStatus.SUCCEEDED);
  assert.equal(onboarded.surface.expiresAt, "2026-07-27T18:00:00-05:00");
  assert.equal(result.route.status, RouteStatus.READY);
  assert.equal(result.status, OperatorStatus.SUCCEEDED);
});

test("app-agent operation requires live signed-in account proof", () => {
  const operator = new AppOperator();
  const missingProofTree = {
    id: "root",
    label: "Messages",
    children: [
      { id: "to", label: "To", role: "textField", value: "" },
      { id: "message", label: "Message", role: "textField", value: "" },
      { id: "send", label: "Send", role: "button", enabled: true },
      { id: "sent", label: "Sent", role: "status", value: "" }
    ]
  };
  const ambiguousProofTree = {
    id: "root",
    label: "Messages",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "account_2", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "to", label: "To", role: "textField", value: "" },
      { id: "message", label: "Message", role: "textField", value: "" }
    ]
  };

  const missingProof = operator.runRouted({
    actionType: "outbound_message.create_draft",
    tree: missingProofTree,
    input: { recipient: "Maya Chen", body: "Want to go?" },
    requiredSources: ["device_contacts"]
  });
  const ambiguousProof = verifyAccountProof(ambiguousProofTree, "Signed in as Alex");

  assert.equal(missingProof.status, OperatorStatus.NEEDS_HANDOFF);
  assert.equal(missingProof.reason, "account_proof_missing");
  assert.equal(ambiguousProof.verified, false);
  assert.equal(ambiguousProof.reason, "account_proof_ambiguous");
  assert.equal(operator.audit.some((event) => event.type === "operator.account_proof_handoff"), true);
});

test("app-agent onboarding requires observed UI dry-run proof before routing", () => {
  const operator = new AppOperator();
  const playbook = {
    id: "tasksAdd",
    packageName: "com.example.tasks",
    actionType: "tasks.add",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["title"],
    steps: [
      { kind: "type", targetLabel: "Task title", valuePath: "title" }
    ],
    verify: { nodeLabel: "Task title", containsPath: "title" }
  };
  const missingTree = operator.onboardAppAgent({ playbook, appName: "Tasks" });
  const ambiguousTree = operator.onboardAppAgent({
    playbook,
    appName: "Tasks",
    observedTree: {
      id: "root",
      label: "Tasks",
      children: [
        { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
        { id: "title", label: "Task title", role: "textField", value: "" },
        { id: "title_2", label: "Task title", role: "textField", value: "" }
      ]
    }
  });
  const dryRun = dryRunPlaybook({
    playbook,
    tree: {
      id: "root",
      label: "Tasks",
      children: [
        { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
        { id: "title", label: "Task title", role: "textField", value: "" }
      ]
    }
  });

  assert.equal(missingTree.status, OperatorStatus.BLOCKED);
  assert.equal(missingTree.dryRun.reason, "observed_tree_required");
  assert.equal(ambiguousTree.status, OperatorStatus.BLOCKED);
  assert.equal(ambiguousTree.dryRun.reason, "target_ambiguous");
  assert.equal(dryRun.verified, true);
});

test("revoked app-agent surface cannot route or operate after onboarding", () => {
  const operator = new AppOperator();
  const playbook = {
    id: "tasksAdd",
    packageName: "com.example.tasks",
    actionType: "tasks.add",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["title"],
    steps: [
      { kind: "type", targetLabel: "Task title", valuePath: "title" }
    ],
    verify: { nodeLabel: "Task title", containsPath: "title" }
  };
  const tree = {
    id: "root",
    label: "Tasks",
    children: [
      { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
      { id: "title", label: "Task title", role: "textField", value: "" }
    ]
  };

  const onboarded = operator.onboardAppAgent({
    playbook,
    appName: "Tasks",
    observedTree: tree
  });
  const before = operator.runRouted({
    actionType: "tasks.add",
    tree,
    input: { title: "Buy picnic blanket" }
  });
  const revoked = operator.revokeAppAgent({ packageName: "com.example.tasks" });
  const after = operator.runRouted({
    actionType: "tasks.add",
    tree,
    input: { title: "Buy picnic blanket" }
  });

  assert.equal(onboarded.status, OperatorStatus.SUCCEEDED);
  assert.equal(before.status, OperatorStatus.SUCCEEDED);
  assert.equal(revoked.status, OperatorStatus.SUCCEEDED);
  assert.equal(after.status, OperatorStatus.BLOCKED);
  assert.equal(after.reason, "no_capable_app_agent");
  assert.equal(operator.audit.some((event) => event.type === "app_agent.revoked"), true);
});

test("app-agent onboarding rejects unsafe malformed playbooks", () => {
  const operator = new AppOperator();
  const invalidPlaybook = {
    id: "unsafePost",
    packageName: "com.example.unsafe",
    actionType: "public_post.create",
    sensitive: true,
    requiredInputPaths: ["body"],
    steps: [
      { kind: "click", targetLabel: "Post" }
    ],
    verify: { nodeLabel: "Posted", containsPath: "body" }
  };
  const unsafePublicPlaybook = {
    id: "unsafeSilentPost",
    packageName: "com.example.unsafe",
    actionType: "public_post.create",
    sensitive: false,
    accountProofLabel: "Signed in as Alex",
    requiredInputPaths: ["body"],
    steps: [
      { kind: "type", targetLabel: "Post body", valuePath: "body" },
      { kind: "click", targetLabel: "Post" }
    ],
    verify: { nodeLabel: "Posted", containsPath: "body" }
  };

  const validation = validatePlaybook(invalidPlaybook);
  const unsafeValidation = validatePlaybook(unsafePublicPlaybook);
  const onboarded = operator.onboardAppAgent({ playbook: invalidPlaybook, appName: "Unsafe" });
  const unsafeOnboarded = operator.onboardAppAgent({ playbook: unsafePublicPlaybook, appName: "Unsafe" });
  const route = operator.routeAction({ actionType: "public_post.create", preferredPackageName: "com.example.unsafe" });

  assert.equal(validation.valid, false);
  assert.equal(validation.errors.includes("sensitive playbooks must declare exactApprovalPath"), true);
  assert.equal(unsafeValidation.valid, false);
  assert.equal(unsafeValidation.errors.includes("public or high-risk playbooks must be sensitive"), true);
  assert.equal(unsafeValidation.errors.includes("public or high-risk playbooks must declare exactApprovalPath"), true);
  assert.equal(onboarded.status, OperatorStatus.BLOCKED);
  assert.equal(unsafeOnboarded.status, OperatorStatus.BLOCKED);
  assert.equal(route.status, RouteStatus.BLOCKED);
});

test("custom notes playbook requires declared inputs before operating an app", () => {
  const operator = new AppOperator();
  const missing = operator.run({
    packageName: "com.example.notes",
    playbookId: "appendNote",
    tree: createNotesTree(),
    input: { title: "Weekend ideas" }
  });

  assert.equal(missing.status, OperatorStatus.NEEDS_HANDOFF);
  assert.equal(missing.reason, "missing_required_input");
  assert.deepEqual(missing.missingInputs, ["body"]);
});

test("custom notes playbook operates a whitelisted logged-in style app", () => {
  const operator = new AppOperator();
  const result = operator.run({
    packageName: "com.example.notes",
    playbookId: "appendNote",
    tree: createNotesTree(),
    input: { title: "Weekend ideas", body: "Outdoor Jazz At The Garden" }
  });

  assert.equal(result.status, OperatorStatus.SUCCEEDED);
  assert.equal(result.verification.verified, true);
});

test("custom public post requires matching exact approval", () => {
  const operator = new AppOperator();
  const queued = operator.run({
    packageName: "com.example.community",
    playbookId: "communityPost",
    tree: createCommunityTree(),
    input: { body: "Anyone want to go to Outdoor Jazz At The Garden?" }
  });
  const mismatch = operator.run({
    packageName: "com.example.community",
    playbookId: "communityPost",
    tree: createCommunityTree(),
    input: { body: "Anyone want to go to Outdoor Jazz At The Garden?" },
    approval: { status: "approved", exactContent: "Different text" }
  });
  const approved = operator.run({
    packageName: "com.example.community",
    playbookId: "communityPost",
    tree: createCommunityTree(),
    input: { body: "Anyone want to go to Outdoor Jazz At The Garden?" },
    approval: { status: "approved", exactContent: "Anyone want to go to Outdoor Jazz At The Garden?" }
  });

  assert.equal(queued.status, OperatorStatus.AWAITING_APPROVAL);
  assert.equal(mismatch.status, OperatorStatus.AWAITING_APPROVAL);
  assert.equal(mismatch.reason, "exact_approval_mismatch");
  assert.equal(approved.status, OperatorStatus.SUCCEEDED);
  assert.equal(approved.verification.verified, true);
});

test("unique node finder reports missing and ambiguous states", () => {
  assert.equal(findUniqueNode(createMessagingTree(), "Missing").status, "missing");
  assert.equal(findUniqueNode(createMessagingTree({ ambiguousSend: true }), "Send").status, "ambiguous");
});
