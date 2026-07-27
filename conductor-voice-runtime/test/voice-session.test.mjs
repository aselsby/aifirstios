import test from "node:test";
import assert from "node:assert/strict";
import { VoiceSession, VoiceStatus, runOutdoorVoiceDemo } from "../src/voice-session.mjs";

test("voice session captures partial and final transcripts", () => {
  const session = new VoiceSession();

  session.startListening();
  session.receivePartial("Find something");
  const snapshot = session.receiveFinal("Find something outdoors.");

  assert.equal(snapshot.status, VoiceStatus.THINKING);
  assert.equal(snapshot.partialTranscript, "");
  assert.equal(snapshot.finalTranscript, "Find something outdoors.");
  assert.equal(snapshot.turns[0].role, "user");
});

test("voice session hands off final transcript as intent", () => {
  const result = runOutdoorVoiceDemo();

  assert.equal(result.handoff.intentType, "outdoor_activity");
  assert.equal(result.handoff.utterance.includes("invite Maya"), true);
  assert.equal(result.session.audit.some((event) => event.type === "intent.handed_off"), true);
});

test("assistant response can be streamed and completed", () => {
  const session = new VoiceSession();

  session.startListening();
  session.receiveFinal("What should I do outside?");
  session.beginAssistantResponse("I found ");
  session.streamAssistantDelta("Outdoor Jazz.");
  const snapshot = session.finishAssistantResponse();

  assert.equal(snapshot.status, VoiceStatus.IDLE);
  assert.equal(snapshot.turns.at(-1).text, "I found Outdoor Jazz.");
});

test("user can interrupt assistant response and resume listening", () => {
  const session = new VoiceSession();

  session.startListening();
  session.receiveFinal("Invite Maya.");
  session.beginAssistantResponse("I will send Maya");
  const interrupted = session.interrupt("do not send yet");
  const resumed = session.resumeListening();

  assert.equal(interrupted.status, VoiceStatus.INTERRUPTED);
  assert.equal(resumed.status, VoiceStatus.LISTENING);
  assert.equal(resumed.audit.some((event) => event.type === "voice.interrupted"), true);
});

test("cannot hand off without final transcript", () => {
  const session = new VoiceSession();

  assert.throws(() => session.handoffIntent(), /final transcript/);
});
