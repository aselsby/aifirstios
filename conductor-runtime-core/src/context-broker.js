export class ContextBroker {
  constructor(connectors, auditLog) {
    this.connectors = connectors;
    this.auditLog = auditLog;
  }

  async gather(task, request) {
    const requestedSources = request.sources ?? [];
    const bundle = {
      id: `ctx_${task.id}`,
      taskId: task.id,
      purpose: request.purpose,
      items: {}
    };

    for (const source of requestedSources) {
      const connector = this.connectors[source];
      if (!connector) {
        bundle.items[source] = {
          status: "missing_connector",
          source
        };
        continue;
      }

      bundle.items[source] = await connector.read({ task, purpose: request.purpose });
    }

    this.auditLog.record("context.gathered", {
      taskId: task.id,
      purpose: request.purpose,
      sources: requestedSources
    });

    return bundle;
  }
}
