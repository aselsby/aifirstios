import { createAuditEvent } from "./domain.js";

export class AuditLog {
  #events = [];

  record(type, detail) {
    const event = createAuditEvent(type, detail);
    this.#events.push(event);
    return event;
  }

  all() {
    return [...this.#events];
  }

  findByType(type) {
    return this.#events.filter((event) => event.type === type);
  }
}
