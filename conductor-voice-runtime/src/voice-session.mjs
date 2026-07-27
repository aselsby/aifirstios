export const VoiceStatus = Object.freeze({
  IDLE: "idle",
  LISTENING: "listening",
  THINKING: "thinking",
  SPEAKING: "speaking",
  INTERRUPTED: "interrupted",
  HANDED_OFF: "handed_off"
});

export class VoiceSession {
  constructor({ now = () => new Date() } = {}) {
    this.now = now;
    this.status = VoiceStatus.IDLE;
    this.partialTranscript = "";
    this.finalTranscript = "";
    this.assistantText = "";
    this.turns = [];
    this.audit = [];
  }

  startListening() {
    this.status = VoiceStatus.LISTENING;
    this.partialTranscript = "";
    this.#record("voice.listening_started", "Push-to-talk session started.");
    return this.snapshot();
  }

  receivePartial(text) {
    this.#requireStatus(VoiceStatus.LISTENING, "Partial transcript requires listening state.");
    this.partialTranscript = text;
    this.#record("voice.partial", text);
    return this.snapshot();
  }

  receiveFinal(text) {
    this.#requireStatus(VoiceStatus.LISTENING, "Final transcript requires listening state.");
    this.finalTranscript = text.trim();
    this.partialTranscript = "";
    this.status = VoiceStatus.THINKING;
    this.turns.push({ role: "user", text: this.finalTranscript });
    this.#record("voice.final", this.finalTranscript);
    return this.snapshot();
  }

  beginAssistantResponse(text = "") {
    this.#requireStatus(VoiceStatus.THINKING, "Assistant response requires thinking state.");
    this.status = VoiceStatus.SPEAKING;
    this.assistantText = text;
    this.#record("assistant.started", text);
    return this.snapshot();
  }

  streamAssistantDelta(text) {
    this.#requireStatus(VoiceStatus.SPEAKING, "Assistant deltas require speaking state.");
    this.assistantText += text;
    this.#record("assistant.delta", text);
    return this.snapshot();
  }

  finishAssistantResponse() {
    this.#requireStatus(VoiceStatus.SPEAKING, "Finishing response requires speaking state.");
    this.turns.push({ role: "assistant", text: this.assistantText });
    this.status = VoiceStatus.IDLE;
    this.#record("assistant.finished", this.assistantText);
    return this.snapshot();
  }

  interrupt(reason = "user_interrupted") {
    if (![VoiceStatus.SPEAKING, VoiceStatus.THINKING, VoiceStatus.LISTENING].includes(this.status)) {
      return this.snapshot();
    }
    this.status = VoiceStatus.INTERRUPTED;
    this.#record("voice.interrupted", reason);
    return this.snapshot();
  }

  resumeListening() {
    this.status = VoiceStatus.LISTENING;
    this.partialTranscript = "";
    this.#record("voice.listening_resumed", "User resumed voice input.");
    return this.snapshot();
  }

  handoffIntent({ intentType = "outdoor_activity", confidence = 0.9 } = {}) {
    if (!this.finalTranscript) {
      throw new Error("Cannot hand off without a final transcript.");
    }
    this.status = VoiceStatus.HANDED_OFF;
    const handoff = {
      utterance: this.finalTranscript,
      intentType,
      confidence,
      createdAt: this.now().toISOString()
    };
    this.#record("intent.handed_off", `${intentType}:${this.finalTranscript}`);
    return handoff;
  }

  snapshot() {
    return {
      status: this.status,
      partialTranscript: this.partialTranscript,
      finalTranscript: this.finalTranscript,
      assistantText: this.assistantText,
      turns: [...this.turns],
      audit: [...this.audit]
    };
  }

  #record(type, detail) {
    this.audit.unshift({
      at: this.now().toISOString(),
      type,
      detail
    });
  }

  #requireStatus(expected, message) {
    if (this.status !== expected) {
      throw new Error(`${message} Current status: ${this.status}`);
    }
  }
}

export function runOutdoorVoiceDemo() {
  const session = new VoiceSession({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  });
  session.startListening();
  session.receivePartial("Find me something outdoors");
  session.receiveFinal("Find me something outdoors to do this afternoon and invite Maya if it fits.");
  const handoff = session.handoffIntent({ intentType: "outdoor_activity", confidence: 0.96 });
  return { handoff, session: session.snapshot() };
}
