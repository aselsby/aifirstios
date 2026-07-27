# Conductor OS Interactive Simulator

This is a richer local simulator for the Conductor OS mobile experience.

It demonstrates:

- Voice-style interaction with Web Speech API hooks when the browser supports them.
- Text fallback for the same intent loop.
- Configurable autonomy modes.
- Cross-app context from Calendar, Weather, Facebook-style Events, Contacts, Messages, and Maps.
- Agent plans that operate apps as tools.
- Approval gates for outbound messages, public posting, purchases, and destructive actions.
- Audit trail showing context access, policy decisions, approvals, and executed app actions.

Run locally:

```bash
npm test
npm start
```

Then open:

```text
http://127.0.0.1:8777
```

The model is in `src/conductor-model.mjs`; the browser shell is in `public/`.
