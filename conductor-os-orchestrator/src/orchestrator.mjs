import { ActionRuntime } from "../../conductor-action-sdk/src/executor.mjs";
import { AutonomyMode as ActionMode } from "../../conductor-action-sdk/src/policy.mjs";
import { AppOperator, OperatorStatus, createMessagingTree } from "../../conductor-app-operator/src/app-operator.mjs";
import { createDefaultConnectorRuntime, outdoorPlanningRequests } from "../../conductor-connectors/src/connectors.mjs";
import { PersonalGraph, seedOutdoorPlanningGraph } from "../../conductor-personal-graph/src/personal-graph.mjs";
import { VoiceSession } from "../../conductor-voice-runtime/src/voice-session.mjs";

export async function runEndToEndOutdoorTask({
  approveSend = false,
  useConnectors = false,
  mode = ActionMode.DRAFT_ONLY,
  now = () => new Date("2026-07-27T10:45:00-05:00")
} = {}) {
  const audit = [];
  const voice = new VoiceSession({ now });

  voice.startListening();
  voice.receivePartial("Find me something outdoors");
  voice.receiveFinal("Find me something outdoors to do this afternoon and invite Maya if it fits.");
  const handoff = voice.handoffIntent({ intentType: "outdoor_activity", confidence: 0.97 });
  audit.unshift({ type: "voice.handoff", detail: handoff.utterance });

  const graph = useConnectors
    ? await createDefaultConnectorRuntime({ now, audit }).hydrateGraph({
        graph: new PersonalGraph({ now, audit }),
        requests: outdoorPlanningRequests()
      })
    : seedOutdoorPlanningGraph(new PersonalGraph({ now, audit }));
  graph.grantAppAgentAccess({
    id: "agent_grant_conductor_activity",
    appAgentId: "conductor.voice",
    packageName: "app.conductor.prototype",
    purposes: ["activity_planning"],
    sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts", "maps"]
  });
  const context = graph.modelSnapshotForAppAgent({
    purpose: "activity_planning",
    sources: useConnectors
      ? ["google_calendar", "weather_provider", "facebook_events", "device_contacts", "maps"]
      : ["google_calendar", "weather_provider", "facebook_events", "device_contacts"]
  }, {
    appAgentId: "conductor.voice",
    autonomyMode: mode
  });
  audit.unshift({ type: "context.snapshot", detail: `${context.facts.length} facts` });

  const event = context.facts.find((fact) => fact.source === "facebook_events");
  const contact = context.facts.find((fact) => fact.source === "device_contacts");
  const inviteBody = "Want to check out Outdoor Jazz At The Garden at 3:30 PM? Weather looks good and it is nearby.";
  if (!event || !contact) {
    return {
      handoff,
      context,
      selectedEvent: event,
      selectedContact: contact,
      actionResults: {},
      operatorResults: {},
      status: "context_blocked",
      audit
    };
  }

  const actions = new ActionRuntime({ mode, audit });
  const draft = actions.execute("messages.create_draft", {
    recipient: "Maya Chen",
    body: inviteBody
  });
  const send = actions.execute("messages.send", {
    recipient: "Maya Chen",
    exactBody: inviteBody
  });

  const operator = new AppOperator({ audit });
  const draftUi = operator.runRouted({
    actionType: "outbound_message.create_draft",
    tree: createMessagingTree(),
    input: {
      recipient: "Maya Chen",
      body: inviteBody
    },
    requiredSources: ["device_contacts"]
  });

  let approvedSend = null;
  if (approveSend) {
    approvedSend = operator.runRouted({
      actionType: "outbound_message.send",
      tree: draftUi.tree,
      input: {
        body: inviteBody
      },
      approval: {
        status: "approved",
        approvalId: send.approval?.id,
        exactContent: send.approval?.exactContent
      },
      requiredSources: ["device_contacts"]
    });
  }

  return {
    handoff,
    context,
    selectedEvent: event,
    selectedContact: contact,
    actionResults: {
      draft,
      send
    },
    operatorResults: {
      draftUi,
      approvedSend
    },
    status: approvedSend?.status === OperatorStatus.SUCCEEDED ? "completed" : "awaiting_approval",
    audit
  };
}
