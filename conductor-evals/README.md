# Conductor Evals

This package evaluates whether Conductor OS behavior matches the goal-level promises.

The harness checks:

- Cross-app context is used for outdoor planning.
- Recommendations include event data.
- Draft mode creates drafts but pauses sends.
- Approvals execute the correct app action.
- Ask Only blocks external side effects.
- Purchase/public-post-style actions are blocked by the Action SDK.
- Personal graph returns cross-app context only for approved purposes.
- Personal graph denies unrelated purposes such as ad targeting.
- Voice runtime hands spoken intent to the planner.
- Voice runtime supports user interruption before unwanted action.
- App operator pauses message-send UI operation until approval.
- App operator stops when a target UI node is ambiguous.
- App operator requires current signed-in account proof before operating a logged-in app.
- App operator rejects user-taught public-post playbooks that try to skip exact approval.
- OS orchestrator runs one spoken task through voice, graph, approvals, and app operation.
- Connector-backed orchestrator hydrates context without exposing credential handles.

Run:

```bash
npm test
npm run eval
```

The eval runner prints a compact pass/fail report.
