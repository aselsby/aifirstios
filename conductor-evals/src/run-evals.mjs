import {
  AutonomyMode,
  approveAction,
  createInitialState,
  runOutdoorIntent,
  setAutonomyMode
} from "../../conductor-os-simulator/src/conductor-model.mjs";
import { ActionRuntime } from "../../conductor-action-sdk/src/executor.mjs";
import { ApprovalMode, Risk, sampleActions } from "../../conductor-action-sdk/src/action-manifest.mjs";
import { AutonomyMode as SdkMode, Decision as SdkDecision, decideDataSnapshot } from "../../conductor-action-sdk/src/policy.mjs";
import { PersonalGraph, seedOutdoorPlanningGraph } from "../../conductor-personal-graph/src/personal-graph.mjs";
import { VoiceSession, VoiceStatus, runOutdoorVoiceDemo } from "../../conductor-voice-runtime/src/voice-session.mjs";
import {
  AppOperator,
  OperatorStatus,
  RouteStatus,
  createCommunityTree,
  createMapsTree,
  createMessagingTree,
  createNotesTree,
  validatePlaybook
} from "../../conductor-app-operator/src/app-operator.mjs";
import { runEndToEndOutdoorTask } from "../../conductor-os-orchestrator/src/orchestrator.mjs";

export const scenarios = [
  {
    id: "outdoor_uses_cross_app_context",
    description: "Outdoor planning uses calendar, weather, Facebook-style events, contacts, messages, and maps.",
    async run() {
      const state = runOutdoorIntent(createInitialState(), "Find something outdoors and invite Maya.");
      return {
        pass: state.audit.some((event) => event.type === "context.accessed" && event.detail.includes("Facebook Events")) &&
          state.plan.some((step) => step.app === "Calendar") &&
          state.plan.some((step) => step.app === "Weather") &&
          state.plan.some((step) => step.app === "Facebook Events") &&
          state.plan.some((step) => step.app === "Messages") &&
          state.plan.some((step) => step.app === "Maps"),
        evidence: state.plan.map((step) => step.app)
      };
    }
  },
  {
    id: "draft_mode_pauses_sensitive_actions",
    description: "Draft mode drafts a message but pauses send and calendar hold for approval.",
    async run() {
      const state = runOutdoorIntent(createInitialState(), "Invite Maya.");
      const approvals = state.approvals.map((approval) => approval.step.actionType);
      return {
        pass: state.apps.messages.drafts.length === 1 &&
          state.apps.messages.sent.length === 0 &&
          approvals.includes("outbound_message.send") &&
          approvals.includes("calendar.create_hold"),
        evidence: { drafts: state.apps.messages.drafts.length, sent: state.apps.messages.sent.length, approvals }
      };
    }
  },
  {
    id: "approval_executes_only_selected_action",
    description: "Approving message send changes Messages state while leaving remaining approvals queued.",
    async run() {
      const state = runOutdoorIntent(createInitialState(), "Invite Maya.");
      const send = state.approvals.find((approval) => approval.step.actionType === "outbound_message.send");
      const approved = approveAction(state, send.id);
      return {
        pass: approved.apps.messages.sent.length === 1 &&
          approved.approvals.length === state.approvals.length - 1 &&
          approved.approvals.some((approval) => approval.step.actionType === "calendar.create_hold"),
        evidence: { sent: approved.apps.messages.sent.length, remainingApprovals: approved.approvals.map((approval) => approval.step.actionType) }
      };
    }
  },
  {
    id: "ask_only_blocks_side_effects",
    description: "Ask Only mode prevents sends, holds, and other external side effects.",
    async run() {
      const base = setAutonomyMode(createInitialState(), AutonomyMode.ASK_ONLY);
      const state = runOutdoorIntent(base, "Invite Maya.");
      return {
        pass: state.approvals.length === 0 &&
          state.apps.messages.sent.length === 0 &&
          state.apps.calendar.holds.length === 0 &&
          state.audit.some((event) => event.type === "policy.blocked"),
        evidence: { approvals: state.approvals.length, sent: state.apps.messages.sent.length, holds: state.apps.calendar.holds.length }
      };
    }
  },
  {
    id: "sdk_blocks_purchase_action",
    description: "Action SDK blocks purchase creation in the MVP.",
    async run() {
      const runtime = new ActionRuntime({
        mode: SdkMode.TRUSTED_AUTO,
        actions: [
          ...sampleActions,
          {
            id: "tickets.buy",
            app: "Tickets",
            actionType: "purchase.create",
            description: "Buy event tickets.",
            risk: Risk.HIGH,
            externalSideEffect: true,
            approval: ApprovalMode.POLICY,
            inputSchema: { required: ["eventId", "amount"] },
            outputSchema: { required: ["receiptId"] },
            verification: "receipt_exists"
          }
        ]
      });
      const result = runtime.execute("tickets.buy", { eventId: "event_jazz", amount: 48 });
      return {
        pass: result.status === "blocked",
        evidence: result
      };
    }
  },
  {
    id: "sdk_requires_exact_approval_for_public_post",
    description: "Action SDK treats public posting as exact-approval work, while keeping destructive classes blocked.",
    async run() {
      const runtime = new ActionRuntime({ mode: SdkMode.TRUSTED_AUTO });
      const body = "Anyone want to go to Outdoor Jazz At The Garden?";
      const first = runtime.execute("facebook.post", { exactBody: body });
      const approved = runtime.execute("facebook.post", { exactBody: body }, {
        status: "approved",
        approvalId: first.approval.id,
        exactContent: first.approval.exactContent
      });
      return {
        pass: first.status === "awaiting_approval" &&
          first.approval.exactContent === body &&
          approved.status === "succeeded" &&
          approved.verification.method === "post_receipt",
        evidence: {
          first: first.status,
          exactContent: first.approval.exactContent,
          approved: approved.status
        }
      };
    }
  },
  {
    id: "sdk_blocks_replayed_approval_receipt",
    description: "Action SDK consumes exact approval receipts once and blocks replayed side-effect approvals.",
    async run() {
      const runtime = new ActionRuntime({ mode: SdkMode.DRAFT_ONLY });
      const input = { recipient: "Maya Chen", exactBody: "Want to go?" };
      const queued = runtime.execute("messages.send", input);
      const approval = {
        status: "approved",
        approvalId: queued.approval.id,
        exactContent: queued.approval.exactContent
      };
      const first = runtime.execute("messages.send", input, approval);
      const replay = runtime.execute("messages.send", input, approval);
      return {
        pass: queued.status === "awaiting_approval" &&
          first.status === "succeeded" &&
          replay.status === "awaiting_approval" &&
          replay.reason === "approval_receipt_replayed" &&
          runtime.audit.some((event) => event.type === "approval.replay_blocked"),
        evidence: {
          queued: queued.status,
          first: first.status,
          replay: replay.status,
          reason: replay.reason
        }
      };
    }
  },
  {
    id: "autonomy_profile_blocks_ask_only_snapshots",
    description: "Ask Only prevents model data snapshots even when an app-agent grant exists.",
    async run() {
      const grant = {
        sources: ["google_calendar", "facebook_events"],
        revoked: false
      };
      const askOnly = decideDataSnapshot({
        appAgentGrant: grant,
        requestedSources: ["google_calendar"]
      }, SdkMode.ASK_ONLY);
      const draftOnly = decideDataSnapshot({
        appAgentGrant: grant,
        requestedSources: ["google_calendar"]
      }, SdkMode.DRAFT_ONLY);
      return {
        pass: askOnly.decision === SdkDecision.BLOCK &&
          draftOnly.decision === SdkDecision.ALLOW,
        evidence: {
          askOnly,
          draftOnly
        }
      };
    }
  },
  {
    id: "orchestrator_ask_only_stops_before_cross_app_context",
    description: "Ask Only mode stops the spoken task before model access to cross-app context or app actions.",
    async run() {
      const result = await runEndToEndOutdoorTask({ mode: SdkMode.ASK_ONLY });
      const auditText = JSON.stringify(result.audit);
      return {
        pass: result.status === "context_blocked" &&
          result.context.facts.length === 0 &&
          auditText.includes("app_agent.snapshot_denied") &&
          Object.keys(result.actionResults).length === 0 &&
          Object.keys(result.operatorResults).length === 0,
        evidence: {
          status: result.status,
          facts: result.context.facts.length,
          auditDenied: auditText.includes("app_agent.snapshot_denied")
        }
      };
    }
  },
  {
    id: "personal_graph_scopes_cross_app_context",
    description: "Personal graph returns calendar, weather, events, and contacts only for the approved activity-planning purpose.",
    async run() {
      const graph = seedOutdoorPlanningGraph(new PersonalGraph({
        now: () => new Date("2026-07-27T10:45:00-05:00")
      }));
      const snapshot = graph.modelSnapshot({
        purpose: "activity_planning",
        sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts"]
      });
      const sources = new Set(snapshot.facts.map((fact) => fact.source));
      return {
        pass: snapshot.facts.length === 4 &&
          sources.has("google_calendar") &&
          sources.has("weather_provider") &&
          sources.has("facebook_events") &&
          sources.has("device_contacts"),
        evidence: snapshot.facts.map((fact) => ({ source: fact.source, summary: fact.summary }))
      };
    }
  },
  {
    id: "personal_graph_denies_unapproved_purpose",
    description: "Personal graph denies app data when the purpose is not allowed by the fact and grant.",
    async run() {
      const graph = seedOutdoorPlanningGraph(new PersonalGraph({
        now: () => new Date("2026-07-27T10:45:00-05:00")
      }));
      const snapshot = graph.modelSnapshot({
        purpose: "ad_targeting",
        sources: ["google_calendar", "facebook_events"]
      });
      return {
        pass: snapshot.facts.length === 0 &&
          snapshot.denied.length === 2 &&
          snapshot.denied.every((item) => item.reason === "purpose_not_allowed"),
        evidence: snapshot
      };
    }
  },
  {
    id: "personal_graph_enforces_app_agent_grants",
    description: "Cross-app model snapshots require an app-agent grant for the requested purpose and sources.",
    async run() {
      const graph = seedOutdoorPlanningGraph(new PersonalGraph({
        now: () => new Date("2026-07-27T10:45:00-05:00")
      }));
      const allowed = graph.modelSnapshotForAppAgent({
        purpose: "activity_planning",
        sources: ["google_calendar", "facebook_events"]
      }, {
        appAgentId: "conductor.voice"
      });
      const denied = graph.modelSnapshotForAppAgent({
        purpose: "activity_planning",
        sources: ["google_calendar"]
      }, {
        appAgentId: "unknown.app"
      });
      return {
        pass: allowed.facts.length === 2 &&
          allowed.appAgentId === "conductor.voice" &&
          denied.facts.length === 0 &&
          denied.denied[0].reason === "missing_or_revoked_app_agent_grant",
        evidence: {
          allowedFacts: allowed.facts.map((fact) => fact.source),
          deniedReason: denied.denied[0].reason
        }
      };
    }
  },
  {
    id: "voice_handoff_preserves_user_intent",
    description: "Voice runtime captures spoken outdoor intent and hands it to the planner as a structured intent.",
    async run() {
      const result = runOutdoorVoiceDemo();
      return {
        pass: result.handoff.intentType === "outdoor_activity" &&
          result.handoff.utterance.includes("invite Maya") &&
          result.session.status === VoiceStatus.HANDED_OFF,
        evidence: result.handoff
      };
    }
  },
  {
    id: "voice_interruption_stops_assistant_turn",
    description: "Voice runtime lets the user interrupt assistant speech and resume listening before unwanted action.",
    async run() {
      const session = new VoiceSession({
        now: () => new Date("2026-07-27T10:45:00-05:00")
      });
      session.startListening();
      session.receiveFinal("Invite Maya to the event.");
      session.beginAssistantResponse("I can send Maya an invite now.");
      const interrupted = session.interrupt("user said: do not send yet");
      const resumed = session.resumeListening();
      return {
        pass: interrupted.status === VoiceStatus.INTERRUPTED &&
          resumed.status === VoiceStatus.LISTENING &&
          resumed.audit.some((event) => event.type === "voice.interrupted"),
        evidence: resumed.audit.slice(0, 4)
      };
    }
  },
  {
    id: "app_operator_requires_approval_for_send",
    description: "App operator pauses message-send UI operation until the user approves exact content.",
    async run() {
      const operator = new AppOperator();
      const first = operator.run({
        packageName: "com.google.android.apps.messaging",
        playbookId: "sendMessage",
        tree: createMessagingTree(),
        input: { body: "Want to check out Outdoor Jazz?" }
      });
      const approved = operator.run({
        packageName: "com.google.android.apps.messaging",
        playbookId: "sendMessage",
        tree: {
          id: "root",
          label: "Messages",
          children: [
            { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
            { id: "message", label: "Message", role: "textField", value: "Want to check out Outdoor Jazz?" },
            { id: "send", label: "Send", role: "button", enabled: true },
            { id: "sent", label: "Sent", role: "status", value: "" }
          ]
        },
        input: { body: "Want to check out Outdoor Jazz?" },
        approval: { status: "approved", exactContent: "Want to check out Outdoor Jazz?" }
      });
      return {
        pass: first.status === OperatorStatus.AWAITING_APPROVAL &&
          approved.status === OperatorStatus.SUCCEEDED &&
          approved.verification.verified === true,
        evidence: { first: first.status, approved: approved.status, audit: operator.audit.slice(0, 3) }
      };
    }
  },
  {
    id: "app_operator_blocks_replayed_approval_receipt",
    description: "A consumed approval receipt cannot be replayed to operate the same app action twice.",
    async run() {
      const operator = new AppOperator();
      const queued = operator.run({
        packageName: "com.google.android.apps.messaging",
        playbookId: "sendMessage",
        tree: createMessagingTree(),
        input: { body: "Want to check out Outdoor Jazz?" }
      });
      const draft = operator.run({
        packageName: "com.google.android.apps.messaging",
        playbookId: "draftMessage",
        tree: createMessagingTree(),
        input: { recipient: "Maya Chen", body: "Want to check out Outdoor Jazz?" }
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
        input: { body: "Want to check out Outdoor Jazz?" },
        approval
      });
      const replay = operator.run({
        packageName: "com.google.android.apps.messaging",
        playbookId: "sendMessage",
        tree: draft.tree,
        input: { body: "Want to check out Outdoor Jazz?" },
        approval
      });
      return {
        pass: queued.status === OperatorStatus.AWAITING_APPROVAL &&
          first.status === OperatorStatus.SUCCEEDED &&
          replay.status === OperatorStatus.AWAITING_APPROVAL &&
          replay.reason === "approval_receipt_replayed" &&
          operator.audit.some((event) => event.type === "operator.approval_replay_blocked"),
        evidence: {
          queued: queued.status,
          first: first.status,
          replay: replay.status,
          reason: replay.reason
        }
      };
    }
  },
  {
    id: "app_operator_stops_on_ambiguous_ui",
    description: "App operator stops and requests handoff when a target UI node is ambiguous.",
    async run() {
      const operator = new AppOperator();
      const result = operator.run({
        packageName: "com.google.android.apps.messaging",
        playbookId: "sendMessage",
        tree: createMessagingTree({ ambiguousSend: true }),
        input: { body: "Want to go?" },
        approval: { status: "approved", exactContent: "Want to go?" }
      });
      return {
        pass: result.status === OperatorStatus.NEEDS_HANDOFF && result.reason === "ambiguous",
        evidence: result
      };
    }
  },
  {
    id: "app_operator_operates_custom_app_with_declared_inputs",
    description: "A user-granted custom app playbook runs only after its required inputs are present.",
    async run() {
      const operator = new AppOperator();
      const missing = operator.run({
        packageName: "com.example.notes",
        playbookId: "appendNote",
        tree: createNotesTree(),
        input: { title: "Weekend ideas" }
      });
      const completed = operator.run({
        packageName: "com.example.notes",
        playbookId: "appendNote",
        tree: createNotesTree(),
        input: {
          title: "Weekend ideas",
          body: "Outdoor Jazz At The Garden"
        }
      });
      return {
        pass: missing.status === OperatorStatus.NEEDS_HANDOFF &&
          missing.reason === "missing_required_input" &&
          completed.status === OperatorStatus.SUCCEEDED &&
          completed.verification.verified === true,
        evidence: {
          missing: missing.status,
          missingInputs: missing.missingInputs,
          completed: completed.status
        }
      };
    }
  },
  {
    id: "app_operator_requires_exact_approval_for_custom_public_post",
    description: "A custom public-post playbook pauses until the exact outgoing text is approved.",
    async run() {
      const body = "Anyone want to go to Outdoor Jazz At The Garden?";
      const operator = new AppOperator();
      const first = operator.run({
        packageName: "com.example.community",
        playbookId: "communityPost",
        tree: createCommunityTree(),
        input: { body }
      });
      const mismatch = operator.run({
        packageName: "com.example.community",
        playbookId: "communityPost",
        tree: createCommunityTree(),
        input: { body },
        approval: { status: "approved", exactContent: "Different text" }
      });
      const approved = operator.run({
        packageName: "com.example.community",
        playbookId: "communityPost",
        tree: createCommunityTree(),
        input: { body },
        approval: { status: "approved", exactContent: body }
      });
      return {
        pass: first.status === OperatorStatus.AWAITING_APPROVAL &&
          mismatch.status === OperatorStatus.AWAITING_APPROVAL &&
          mismatch.reason === "exact_approval_mismatch" &&
          approved.status === OperatorStatus.SUCCEEDED &&
          approved.verification.verified === true,
        evidence: {
          first: first.status,
          mismatch: mismatch.reason,
          approved: approved.status
        }
      };
    }
  },
  {
    id: "app_operator_rejects_silent_public_post_playbook",
    description: "User-taught public posting playbooks cannot opt out of exact approval.",
    async run() {
      const operator = new AppOperator();
      const playbook = {
        id: "silentPublicPost",
        packageName: "com.example.community",
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
      const validation = validatePlaybook(playbook);
      const onboarded = operator.onboardAppAgent({
        playbook,
        appName: "Community",
        observedTree: createCommunityTree()
      });
      return {
        pass: validation.valid === false &&
          validation.errors.includes("public or high-risk playbooks must be sensitive") &&
          onboarded.status === OperatorStatus.BLOCKED,
        evidence: {
          valid: validation.valid,
          errors: validation.errors,
          onboarded: onboarded.status
        }
      };
    }
  },
  {
    id: "app_operator_routes_intent_to_logged_in_app_agent",
    description: "App operator routes an action type to a logged-in app-agent surface with playbook and source grants before UI operation.",
    async run() {
      const operator = new AppOperator();
      const result = operator.runRouted({
        actionType: "maps.open_route",
        tree: createMapsTree(),
        input: { destination: "Outdoor Jazz At The Garden" },
        requiredSources: ["maps"]
      });
      return {
        pass: result.route.status === RouteStatus.READY &&
          result.route.surface.packageName === "com.google.android.apps.maps" &&
          result.route.playbook.id === "openRoute" &&
          result.status === OperatorStatus.SUCCEEDED,
        evidence: {
          route: result.route.status,
          packageName: result.route.surface.packageName,
          playbookId: result.route.playbook.id,
          status: result.status
        }
      };
    }
  },
  {
    id: "app_operator_requires_live_account_proof",
    description: "App operator refuses to touch a logged-in app unless the current UI proves the expected account.",
    async run() {
      const operator = new AppOperator();
      const result = operator.runRouted({
        actionType: "outbound_message.create_draft",
        tree: {
          id: "root",
          label: "Messages",
          children: [
            { id: "to", label: "To", role: "textField", value: "" },
            { id: "message", label: "Message", role: "textField", value: "" },
            { id: "send", label: "Send", role: "button", enabled: true }
          ]
        },
        input: { recipient: "Maya Chen", body: "Want to go?" },
        requiredSources: ["device_contacts"]
      });
      return {
        pass: result.status === OperatorStatus.NEEDS_HANDOFF &&
          result.reason === "account_proof_missing" &&
          operator.audit.some((event) => event.type === "operator.account_proof_handoff"),
        evidence: {
          status: result.status,
          reason: result.reason
        }
      };
    }
  },
  {
    id: "app_operator_onboards_user_taught_app_agent",
    description: "A previously unknown logged-in app can become an agent surface after a validated user-taught playbook and source grant.",
    async run() {
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
      const observedTree = {
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
        allowedSourceIds: ["calendar"],
        observedTree
      });
      const result = operator.runRouted({
        actionType: "tasks.add",
        tree: observedTree,
        input: { title: "Buy picnic blanket" },
        requiredSources: ["calendar"]
      });
      return {
        pass: onboarded.status === OperatorStatus.SUCCEEDED &&
          onboarded.dryRun.verified === true &&
          result.route.status === RouteStatus.READY &&
          result.route.surface.packageName === "com.example.tasks" &&
          result.status === OperatorStatus.SUCCEEDED,
        evidence: {
          onboarded: onboarded.status,
          dryRun: onboarded.dryRun.verified,
          route: result.route.status,
          packageName: result.route.surface.packageName,
          status: result.status
        }
      };
    }
  },
  {
    id: "app_operator_revokes_user_taught_app_agent",
    description: "A user-taught app-agent surface stops routing and operating after the user revokes its app session.",
    async run() {
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
      const observedTree = {
        id: "root",
        label: "Tasks",
        children: [
          { id: "account", label: "Signed in as Alex", role: "accountChip", value: "Alex" },
          { id: "title", label: "Task title", role: "textField", value: "" }
        ]
      };
      operator.onboardAppAgent({
        playbook,
        appName: "Tasks",
        observedTree
      });
      const before = operator.runRouted({
        actionType: "tasks.add",
        tree: observedTree,
        input: { title: "Buy picnic blanket" }
      });
      const revoked = operator.revokeAppAgent({ packageName: "com.example.tasks" });
      const after = operator.runRouted({
        actionType: "tasks.add",
        tree: observedTree,
        input: { title: "Buy picnic blanket" }
      });
      return {
        pass: before.status === OperatorStatus.SUCCEEDED &&
          revoked.status === OperatorStatus.SUCCEEDED &&
          after.status === OperatorStatus.BLOCKED &&
          operator.audit.some((event) => event.type === "app_agent.revoked"),
        evidence: {
          before: before.status,
          revoked: revoked.status,
          after: after.status,
          reason: after.reason
        }
      };
    }
  },
  {
    id: "app_operator_expires_user_taught_app_agent",
    description: "A user-taught app-agent surface routes to renewal handoff after its time-bounded grant expires.",
    async run() {
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
      const observedTree = {
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
        expiresAt: "2026-07-27T09:00:00-05:00",
        observedTree
      });
      const after = operator.runRouted({
        actionType: "tasks.add",
        tree: observedTree,
        input: { title: "Buy picnic blanket" }
      });
      return {
        pass: onboarded.status === OperatorStatus.SUCCEEDED &&
          after.status === OperatorStatus.NEEDS_HANDOFF &&
          after.reason === "session_expired" &&
          after.route.status === RouteStatus.NEEDS_LOGIN &&
          operator.audit.some((event) => event.type === "agent_route.session_expired"),
        evidence: {
          onboarded: onboarded.status,
          after: after.status,
          reason: after.reason
        }
      };
    }
  },
  {
    id: "orchestrator_runs_spoken_task_to_approved_app_action",
    description: "OS orchestrator captures voice, gathers graph context, queues send approval, and executes approved app operation.",
    async run() {
      const result = await runEndToEndOutdoorTask({ approveSend: true });
      const eventTypes = new Set(result.audit.map((event) => event.type));
      return {
        pass: result.status === "completed" &&
          result.handoff.intentType === "outdoor_activity" &&
          result.context.facts.length === 4 &&
          result.actionResults.send.status === "awaiting_approval" &&
          result.operatorResults.approvedSend.status === OperatorStatus.SUCCEEDED &&
          eventTypes.has("approval.queued") &&
          eventTypes.has("operator.succeeded"),
        evidence: {
          status: result.status,
          intent: result.handoff.intentType,
          facts: result.context.facts.map((fact) => fact.source),
          approvedSend: result.operatorResults.approvedSend.status
        }
      };
    }
  },
  {
    id: "connectors_hydrate_orchestrator_without_exposing_tokens",
    description: "Connector-backed orchestrator hydrates graph context from app connectors without exposing credential handles.",
    async run() {
      const result = await runEndToEndOutdoorTask({ approveSend: true, useConnectors: true });
      const sources = new Set(result.context.facts.map((fact) => fact.source));
      const auditText = JSON.stringify(result.audit);
      return {
        pass: result.status === "completed" &&
          result.context.facts.length === 5 &&
          sources.has("google_calendar") &&
          sources.has("weather_provider") &&
          sources.has("facebook_events") &&
          sources.has("device_contacts") &&
          sources.has("maps") &&
          auditText.includes("connector.read") &&
          !auditText.includes("vault:"),
        evidence: {
          facts: result.context.facts.map((fact) => fact.source),
          auditHasConnectorReads: auditText.includes("connector.read"),
          tokenLeak: auditText.includes("vault:")
        }
      };
    }
  }
];

export async function runEvals() {
  const results = await Promise.all(scenarios.map(async (scenario) => {
    try {
      const result = await scenario.run();
      return { id: scenario.id, description: scenario.description, ...result };
    } catch (error) {
      return { id: scenario.id, description: scenario.description, pass: false, evidence: error.message };
    }
  }));

  return {
    passed: results.filter((result) => result.pass).length,
    total: results.length,
    results
  };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const report = await runEvals();
  console.log(JSON.stringify(report, null, 2));
  if (report.passed !== report.total) {
    process.exitCode = 1;
  }
}
