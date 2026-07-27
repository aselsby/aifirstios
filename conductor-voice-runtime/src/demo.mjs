import { runOutdoorVoiceDemo, VoiceSession } from "./voice-session.mjs";

const outdoor = runOutdoorVoiceDemo();

const interrupted = new VoiceSession({
  now: () => new Date("2026-07-27T10:46:00-05:00")
});
interrupted.startListening();
interrupted.receiveFinal("Find something outdoors.");
interrupted.beginAssistantResponse("I found an outdoor jazz event and I can invite");
interrupted.interrupt("user said: do not message anyone yet");
interrupted.resumeListening();
interrupted.receiveFinal("Don't message anyone yet. Just show me options.");

console.log(JSON.stringify({
  outdoor,
  interrupted: interrupted.snapshot()
}, null, 2));
