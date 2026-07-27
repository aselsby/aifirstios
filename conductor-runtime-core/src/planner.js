import { Risk } from "./domain.js";

export function createOutdoorActivityPlan(task, contextBundle) {
  const events = contextBundle.items.events.events;
  const bestEvent = events.toSorted((left, right) => right.score - left.score)[0];
  const contact = contextBundle.items.contacts.contacts[0];
  const inviteBody = `Want to check out ${bestEvent.title} at 3:30? Weather looks good and it is about 12 minutes away.`;

  return {
    id: `plan_${task.id}`,
    taskId: task.id,
    goal: task.goal,
    recommendation: bestEvent,
    steps: [
      {
        id: "check_calendar",
        title: "Check calendar availability",
        tool: "calendar.free_busy",
        actionType: "calendar.read",
        risk: Risk.LOW,
        externalSideEffect: false,
        input: contextBundle.items.calendar.freeWindows
      },
      {
        id: "check_weather",
        title: "Check outdoor weather window",
        tool: "weather.hourly",
        actionType: "weather.read",
        risk: Risk.LOW,
        externalSideEffect: false,
        input: contextBundle.items.weather.bestOutdoorWindow
      },
      {
        id: "rank_events",
        title: "Rank nearby outdoor events",
        tool: "events.rank",
        actionType: "events.read",
        risk: Risk.LOW,
        externalSideEffect: false,
        input: events.map((event) => event.id)
      },
      {
        id: "draft_invite",
        title: "Draft invite to Maya",
        tool: "messages.create_draft",
        actionType: "outbound_message.create_draft",
        risk: Risk.LOW,
        externalSideEffect: false,
        input: {
          recipientContactId: contact.id,
          body: inviteBody
        }
      },
      {
        id: "send_invite",
        title: "Send invite to Maya",
        tool: "messages.send",
        actionType: "outbound_message.send",
        risk: Risk.MEDIUM,
        externalSideEffect: true,
        input: {
          recipientContactId: contact.id,
          exactBody: inviteBody
        }
      }
    ]
  };
}
