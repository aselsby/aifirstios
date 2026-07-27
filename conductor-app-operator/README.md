# Conductor App Operator

This package prototypes the guarded app-operation layer for Conductor OS.

It models the Android AccessibilityService fallback path:

1. Confirm target app package is whitelisted.
2. Match a whitelisted playbook.
3. Inspect the accessibility tree.
4. Refuse ambiguous targets.
5. Require approval for sensitive actions.
6. Require declared inputs before operating any app.
7. Execute only approved/allowed UI actions.
8. Verify the resulting app state.
9. Stop or hand off when confidence is low.

## Why This Exists

The Action SDK is the preferred long-term path. But the OS also needs to operate apps once the user is logged in, especially when structured APIs are unavailable. This operator is the safety boundary for that capability.

## Run

```bash
npm test
npm run demo
```

## Android Target

`ConductorAccessibilityService` should map Android accessibility events into this operator:

- package name
- visible screen id
- accessibility tree nodes
- requested playbook
- current policy/approval state

The native implementation must preserve the same invariant: no unapproved sensitive external action, no operation on unwhitelisted packages, and no execution when target nodes are ambiguous.

## Custom App Playbooks

Apps become agent-operable only through explicit playbooks. A playbook declares:

- `packageName`: the installed app package Conductor may operate.
- `requiredInputPaths`: exact user/context inputs needed before UI action.
- `sensitive` and `exactApprovalPath`: whether the user must approve the exact outgoing content.
- `accountProofLabel`: the visible signed-in account chip/label that must be unique in the current app UI.
- `steps`: accessibility-tree actions that must resolve to one enabled node each.
- `verify`: the post-action app state that proves completion.

Public or high-risk taught playbooks must be sensitive, declare an exact approval path, and require that exact input. The test suite includes custom notes and community-post apps to keep the arbitrary-app path honest. Taught app agents must pass a dry-run with the account proof present, and each operation rechecks that proof before touching the UI.
