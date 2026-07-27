package app.conductor.tools

import app.conductor.audit.AuditLedger
import app.conductor.operator.accessibility.AppOperationApprovalReceipt
import app.conductor.operator.accessibility.AppOperationExecutor
import app.conductor.operator.accessibility.AppOperationStatus
import app.conductor.runtime.ApprovalCard
import app.conductor.runtime.PlanStep
import app.conductor.runtime.StepStatus
import app.conductor.runtime.ToolResult
import app.conductor.runtime.Verification
import app.conductor.tools.intents.AndroidIntentLauncher
import app.conductor.tools.intents.AndroidIntentPlanner
import app.conductor.tools.intents.AndroidIntentLaunchStatus
import app.conductor.tools.intents.AndroidIntentStatus
import app.conductor.tools.intents.RecordingAndroidIntentLauncher

class ToolRegistry(
    private val auditLedger: AuditLedger,
    private val appOperationExecutor: AppOperationExecutor = AppOperationExecutor(auditLedger),
    private val androidIntentPlanner: AndroidIntentPlanner = AndroidIntentPlanner(),
    private val androidIntentLauncher: AndroidIntentLauncher = RecordingAndroidIntentLauncher()
) {
    fun execute(step: PlanStep, approval: ApprovalCard? = null, userId: String = "user_001"): ToolResult {
        if (
            agentOperatedActionTypes.contains(step.actionType) ||
            agentOperatedToolNames.contains(step.tool) ||
            appOperationExecutor.supportsActionType(step.actionType)
        ) {
            return executeAppOperation(step, approval, userId)
        }

        val method = when (step.tool) {
            "assistant.answer" -> "local_mobile_intent_summary"
            "calendar.free_busy" -> "provider_response_schema"
            "weather.hourly" -> "provider_response_schema"
            "events.rank" -> "deterministic_ranker"
            "memory.preference.read" -> "purpose_scoped_graph_memory"
            else -> null
        }

        if (method == null) {
            auditLedger.record("tool.missing", "Missing tool ${step.tool} for step ${step.id}")
            return ToolResult(
                stepId = step.id,
                status = StepStatus.FAILED,
                error = "Missing tool ${step.tool}"
            )
        }

        auditLedger.record("tool.executed", "Executed ${step.tool} for ${step.id} with $method")
        return ToolResult(
            stepId = step.id,
            status = StepStatus.SUCCEEDED,
            verification = Verification(status = "verified", method = method)
        )
    }

    private fun executeAndroidIntent(step: PlanStep): ToolResult {
        val result = androidIntentPlanner.plan(step)
        return when (result.status) {
            AndroidIntentStatus.READY -> {
                val plan = result.plan
                    ?: return ToolResult(stepId = step.id, status = StepStatus.FAILED, error = "Missing intent plan")
                auditLedger.record("intent.planned", "${step.id} ${plan.action} ${plan.verificationMethod}")
                val launch = androidIntentLauncher.launch(plan)
                auditLedger.record("intent.launch_result", "${step.id} ${launch.status} ${launch.detail}")
                when (launch.status) {
                    AndroidIntentLaunchStatus.LAUNCHED -> ToolResult(
                        stepId = step.id,
                        status = StepStatus.SUCCEEDED,
                        verification = Verification(status = "launched", method = launch.verificationMethod)
                    )
                    AndroidIntentLaunchStatus.NEEDS_HANDOFF -> ToolResult(
                        stepId = step.id,
                        status = StepStatus.AWAITING_APPROVAL,
                        verification = Verification(status = "handoff", method = launch.verificationMethod),
                        error = launch.detail
                    )
                    AndroidIntentLaunchStatus.FAILED -> ToolResult(
                        stepId = step.id,
                        status = StepStatus.FAILED,
                        error = launch.detail
                    )
                }
            }
            AndroidIntentStatus.NEEDS_HANDOFF -> {
                auditLedger.record("intent.needs_handoff", "${step.id} ${result.reason}")
                ToolResult(
                    stepId = step.id,
                    status = StepStatus.AWAITING_APPROVAL,
                    error = result.reason
                )
            }
            AndroidIntentStatus.UNSUPPORTED -> {
                auditLedger.record("intent.unsupported", "${step.id} ${result.reason}")
                ToolResult(
                    stepId = step.id,
                    status = StepStatus.FAILED,
                    error = result.reason
                )
            }
        }
    }

    private fun executeAppOperation(step: PlanStep, approval: ApprovalCard?, userId: String): ToolResult {
        val result = appOperationExecutor.executeRouted(
            actionType = step.actionType,
            input = step.input,
            approvalReceipt = approval?.let {
                AppOperationApprovalReceipt(
                    approvalId = it.id,
                    actionType = it.actionType,
                    approvedExactContent = it.exactContent.orEmpty()
                )
            },
            userId = userId,
            requiredSourceIds = requiredSourceIdsFor(step)
        )

        return when (result.status) {
            AppOperationStatus.VERIFIED -> ToolResult(
                stepId = step.id,
                status = StepStatus.SUCCEEDED,
                verification = result.verification
            )
            AppOperationStatus.NEEDS_HANDOFF -> ToolResult(
                stepId = step.id,
                status = StepStatus.AWAITING_APPROVAL,
                error = result.detail
            )
            AppOperationStatus.READY -> ToolResult(
                stepId = step.id,
                status = StepStatus.AWAITING_APPROVAL,
                error = result.detail
            )
            AppOperationStatus.BLOCKED -> ToolResult(
                stepId = step.id,
                status = StepStatus.BLOCKED,
                error = result.detail
            )
        }
    }

    private fun requiredSourceIdsFor(step: PlanStep): Set<String> =
        when (step.actionType) {
            "outbound_message.create_draft" -> setOf("device_contacts")
            "outbound_message.send" -> setOf("device_contacts")
            "public_post.create" -> setOf("facebook_events")
            "calendar.hold.create" -> setOf("google_calendar")
            "maps.route.open" -> setOf("maps")
            else -> step.input["__requiredSourceIds"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        }

    private companion object {
        val agentOperatedActionTypes = setOf(
            "outbound_message.create_draft",
            "outbound_message.send",
            "calendar.hold.create",
            "maps.route.open",
            "public_post.create"
        )

        val agentOperatedToolNames = setOf(
            "messages.create_draft",
            "messages.send",
            "calendar.create_hold",
            "maps.open_route",
            "facebook.post"
        )
    }
}
