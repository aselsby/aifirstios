# Conductor Voice Runtime

This package prototypes the voice session state machine for Conductor OS.

It handles:

- Push-to-talk session lifecycle.
- Partial transcript updates.
- Final user transcript.
- Assistant response streaming.
- User interruption.
- Intent handoff into the OS planner.
- Audit events for voice input and interruption.

## Why This Exists

The OS goal requires voice chat, not just a button. Voice is the primary way a user delegates outcomes:

> "Find me something outdoors to do this afternoon and invite Maya if it fits."

This runtime gives Android and the browser simulator a shared behavioral contract before wiring real microphone capture and realtime speech-to-speech models.

## Run

```bash
npm test
npm run demo
```

## Android Target

`VoiceSessionService` should map Android/realtime model events into this state machine:

- `startListening()`
- `receivePartial(text)`
- `receiveFinal(text)`
- `beginAssistantResponse(text)`
- `streamAssistantDelta(text)`
- `interrupt(reason)`
- `handoffIntent()`
