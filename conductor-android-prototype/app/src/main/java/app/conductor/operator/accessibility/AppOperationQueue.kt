package app.conductor.operator.accessibility

import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore

interface AppOperationQueue {
    fun enqueue(item: AppOperationQueueItem)
    fun pending(): List<AppOperationQueueItem>
    fun resolve(requestId: String)
    fun clear()
}

class InMemoryAppOperationQueue(
    private val nowIso: () -> String = { SystemClock.nowIso() }
) : AppOperationQueue {
    private val items = mutableListOf<AppOperationQueueItem>()

    override fun enqueue(item: AppOperationQueueItem) {
        items += item
    }

    override fun pending(): List<AppOperationQueueItem> {
        items.removeAll { it.isExpired(nowIso()) }
        return items.toList()
    }

    override fun resolve(requestId: String) {
        items.removeAll { it.request.id == requestId }
    }

    override fun clear() {
        items.clear()
    }
}

class RecordBackedAppOperationQueue(
    private val recordStore: ConductorRecordStore,
    private val nowIso: () -> String = { SystemClock.nowIso() }
) : AppOperationQueue {
    override fun enqueue(item: AppOperationQueueItem) {
        recordStore.enqueueAppOperation(item)
    }

    override fun pending(): List<AppOperationQueueItem> =
        recordStore.queuedAppOperations().filterNot { queued ->
            queued.isExpired(nowIso()).also { expired ->
                if (expired) recordStore.resolveQueuedAppOperation(queued.request.id)
            }
        }

    override fun resolve(requestId: String) {
        recordStore.resolveQueuedAppOperation(requestId)
    }

    override fun clear() {
        recordStore.clearQueuedAppOperations()
    }
}
