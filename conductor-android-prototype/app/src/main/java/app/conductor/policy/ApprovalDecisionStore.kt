package app.conductor.policy

import app.conductor.runtime.SystemClock

import android.content.Context
import app.conductor.runtime.ApprovalCard
import app.conductor.runtime.ApprovalStatus
import app.conductor.storage.ConductorRecordStore
import app.conductor.storage.StoredApprovalDecision

class ApprovalDecisionStore(
    context: Context,
    private val recordStore: ConductorRecordStore? = null
) {
    private val prefs = context.getSharedPreferences("conductor_approval_decisions", Context.MODE_PRIVATE)

    fun approvedIds(): Set<String> =
        prefs.getStringSet(KEY_APPROVED_IDS, emptySet<String>()).orEmpty()

    fun deniedIds(): Set<String> =
        prefs.getStringSet(KEY_DENIED_IDS, emptySet<String>()).orEmpty()

    fun approvedDecisions(): List<StoredApprovalDecision> =
        recordStore?.approvalDecisions()
            ?.filter { it.status == ApprovalStatus.APPROVED }
            .orEmpty()

    fun approve(id: String) {
        approveDecision(id = id)
    }

    fun approve(card: ApprovalCard) {
        approveDecision(
            id = card.id,
            actionType = card.actionType,
            exactContent = card.exactContent
        )
    }

    private fun approveDecision(
        id: String,
        actionType: String? = null,
        exactContent: String? = null
    ) {
        val approved = approvedIds() + id
        val denied = deniedIds() - id
        prefs.edit()
            .putStringSet(KEY_APPROVED_IDS, approved)
            .putStringSet(KEY_DENIED_IDS, denied)
            .apply()
        recordStore?.saveApprovalDecision(
            StoredApprovalDecision(
                id = id,
                status = ApprovalStatus.APPROVED,
                decidedAtIso = SystemClock.nowIso(),
                actionType = actionType,
                exactContent = exactContent
            )
        )
    }

    fun deny(id: String) {
        val approved = approvedIds() - id
        val denied = deniedIds() + id
        prefs.edit()
            .putStringSet(KEY_APPROVED_IDS, approved)
            .putStringSet(KEY_DENIED_IDS, denied)
            .apply()
        recordStore?.saveApprovalDecision(
            StoredApprovalDecision(
                id = id,
                status = ApprovalStatus.DENIED,
                decidedAtIso = SystemClock.nowIso()
            )
        )
    }

    private companion object {
        const val KEY_APPROVED_IDS = "approved_ids"
        const val KEY_DENIED_IDS = "denied_ids"
    }
}
