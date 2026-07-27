package app.conductor.operator.accessibility

import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore
import app.conductor.storage.StoredConsumedApprovalReceipt

interface AppOperationApprovalReceiptLedger {
    fun isConsumed(approvalId: String): Boolean
    fun consume(receipt: AppOperationApprovalReceipt)
}

class InMemoryAppOperationApprovalReceiptLedger : AppOperationApprovalReceiptLedger {
    private val consumedApprovalIds = linkedSetOf<String>()

    override fun isConsumed(approvalId: String): Boolean =
        consumedApprovalIds.contains(approvalId)

    override fun consume(receipt: AppOperationApprovalReceipt) {
        consumedApprovalIds.add(receipt.approvalId)
    }
}

class RecordBackedAppOperationApprovalReceiptLedger(
    private val recordStore: ConductorRecordStore,
    private val nowIso: () -> String = { SystemClock.nowIso() }
) : AppOperationApprovalReceiptLedger {
    override fun isConsumed(approvalId: String): Boolean =
        recordStore.consumedApprovalReceipts().any { it.approvalId == approvalId }

    override fun consume(receipt: AppOperationApprovalReceipt) {
        recordStore.saveConsumedApprovalReceipt(
            StoredConsumedApprovalReceipt(
                approvalId = receipt.approvalId,
                actionType = receipt.actionType,
                consumedAtIso = nowIso()
            )
        )
    }
}
