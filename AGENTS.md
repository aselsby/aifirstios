# Conductor OS agent personas

## Conductor (OS agent)

Primary mobile OS operator. Owns voice, intent routing, multi-app context, policy, approvals, and app-subagent dispatch.

## Life domain planners

Specialized planning personas invoked by intent type:

| Persona | Intent | Responsibility |
|---------|--------|----------------|
| Outdoor planner | `outdoor_activity` | Calendar + weather + nearby places → ranked plan |
| Messaging agent | `life_messaging` | Draft/send via Messages |
| Calendar agent | `life_calendar` | Agenda + holds |
| Contacts agent | `life_contacts` | Lookup + approved calls |
| Maps agent | `life_maps` | Search + routes |
| Email agent | `life_email` | Gmail draft/send |
| Shopping agent | `life_shopping` | Search/cart/approved purchase |
| Banking agent | `life_banking` | Balances + approved transfers/payments |
| Social agent | `life_social` | Exact-approved public posts |
| Browser agent | `life_browser` | Safe open/search |

## App subagents

Each installed, logged-in, granted package is a subagent with:

- playbook whitelist
- autonomy mode + budget
- source scope
- exact-approval overrides for sensitive actions

## Reviewer stance

When changing agent surfaces, prefer:

1. safety over convenience for money/privacy
2. handoff over guessing on ambiguous UI
3. durable audit over silent success
