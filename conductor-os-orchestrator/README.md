# Conductor OS Orchestrator

This package composes the Conductor subsystems into one end-to-end operating loop.

It proves:

1. Voice captures the user's intent.
2. The intent is handed to the planner as a structured task.
3. The personal graph returns purpose-scoped cross-app context.
4. The Action SDK creates a safe message draft.
5. Sensitive send action pauses for approval.
6. The App Operator executes the approved send through a whitelisted app playbook.
7. The final state and audit trail show what happened.

## Run

```bash
npm test
npm run demo
```

This is still a local prototype, not a deployed Android app. Its value is that it exercises the core OS contract across the modules instead of testing each piece in isolation.
