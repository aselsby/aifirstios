package app.conductor.runtime

import android.content.Context
import app.conductor.audit.AuditLedger
import app.conductor.context.MockContextBroker
import app.conductor.operator.accessibility.AppOperationPlaybookRegistry
import app.conductor.planner.OutdoorActivityPlanner
import app.conductor.policy.PolicyEngine
import app.conductor.storage.ConductorRecordStore
import app.conductor.storage.StoredApprovalDecision
import app.conductor.tools.ToolRegistry

class ConductorRuntime(
    private val auditLedger: AuditLedger = AuditLedger(),
    private val policyEngine: PolicyEngine = PolicyEngine(),
    private val toolRegistry: ToolRegistry = ToolRegistry(auditLedger),
    private val recordStore: ConductorRecordStore? = null,
    private val androidContext: Context? = null
) {
    fun runMobileIntentWorkflow(
        intentType: String,
        policy: UserPolicy = UserPolicy(mode = AutonomyMode.DRAFT_ONLY),
        approvedApprovalIds: Set<String> = emptySet(),
        approvedApprovalDecisions: List<StoredApprovalDecision> = emptyList(),
        deniedApprovalIds: Set<String> = emptySet(),
        userId: String = "user_001",
        utterance: String = "Find me something outdoors to do this afternoon and draft an invite to Maya.",
        forcedPlaybookId: String = ""
    ): WorkflowResult =
        when (intentType) {
            "app_task" -> runAppTaskWorkflow(
                policy = policy,
                approvedApprovalIds = approvedApprovalIds,
                approvedApprovalDecisions = approvedApprovalDecisions,
                deniedApprovalIds = deniedApprovalIds,
                userId = userId,
                utterance = utterance
            )
            "outdoor_activity" -> runOutdoorActivityWorkflow(
                policy = policy,
                approvedApprovalIds = approvedApprovalIds,
                approvedApprovalDecisions = approvedApprovalDecisions,
                deniedApprovalIds = deniedApprovalIds,
                userId = userId,
                utterance = utterance
            )
            else -> runGeneralMobileIntentWorkflow(
                intentType = intentType,
                policy = policy,
                approvedApprovalIds = approvedApprovalIds,
                approvedApprovalDecisions = approvedApprovalDecisions,
                deniedApprovalIds = deniedApprovalIds,
                userId = userId,
                utterance = utterance,
                forcedPlaybookId = forcedPlaybookId
            )
        }

    fun runOutdoorActivityWorkflow(
        policy: UserPolicy = UserPolicy(mode = AutonomyMode.DRAFT_ONLY),
        approvedApprovalIds: Set<String> = emptySet(),
        approvedApprovalDecisions: List<StoredApprovalDecision> = emptyList(),
        deniedApprovalIds: Set<String> = emptySet(),
        userId: String = "user_001",
        utterance: String = "Find me something outdoors to do this afternoon and draft an invite to Maya."
    ): WorkflowResult {
        val task = Task(
            id = "task_outdoor_activity",
            userId = userId,
            goal = utterance,
            intentType = "outdoor_activity",
            mode = policy.mode,
            createdAtIso = SystemClock.nowIso()
        )

        recordStore?.saveTask(task)
        auditLedger.record("task.started", "Started ${task.id} in ${task.mode}")

        val context = MockContextBroker(auditLedger, recordStore, androidContext)
            .gatherOutdoorActivityContext(task, policy.mode)
        if (context.items.isEmpty()) {
            auditLedger.record("task.context_blocked", "No cross-app context available in ${policy.mode}")
            val plan = Plan(
                id = "plan_${task.id}",
                taskId = task.id,
                goal = task.goal,
                recommendation = Recommendation(
                    id = "context_blocked",
                    title = "Context blocked by autonomy",
                    startsAtIso = task.createdAtIso,
                    distanceMiles = 0.0,
                    score = 0
                ),
                steps = emptyList()
            )
            return WorkflowResult(
                task = task,
                context = context,
                plan = plan,
                firstPassResults = emptyList(),
                approvedResults = emptyList(),
                approvedApprovalIds = approvedApprovalIds,
                deniedApprovalIds = deniedApprovalIds,
                audit = auditLedger.all()
            )
        }

        val plan = OutdoorActivityPlanner(auditLedger).createPlan(task, context)
        val firstPass = plan.steps.map {
            runStep(
                step = it,
                userPolicy = policy,
                approvedApprovalIds = approvedApprovalIds,
                approvedApprovalDecisions = approvedApprovalDecisions,
                deniedApprovalIds = deniedApprovalIds,
                userId = task.userId
            )
        }
        val approved = firstPass.filter { it.status == StepStatus.SUCCEEDED && it.approval != null }
        persistOperationTimeline(task, plan, firstPass)

        auditLedger.record(
            "task.completed",
            "firstPass=${firstPass.count { it.status == StepStatus.SUCCEEDED }}, approvals=${approved.size}"
        )

        return WorkflowResult(
            task = task,
            context = context,
            plan = plan,
            firstPassResults = firstPass,
            approvedResults = approved,
            approvedApprovalIds = approvedApprovalIds,
            deniedApprovalIds = deniedApprovalIds,
            audit = auditLedger.all()
        )
    }

    private fun runAppTaskWorkflow(
        policy: UserPolicy,
        approvedApprovalIds: Set<String>,
        approvedApprovalDecisions: List<StoredApprovalDecision>,
        deniedApprovalIds: Set<String>,
        userId: String,
        utterance: String,
        forcedPlaybookId: String = ""
    ): WorkflowResult {
        val task = Task(
            id = "task_app_task",
            userId = userId,
            goal = utterance,
            intentType = "app_task",
            mode = policy.mode,
            createdAtIso = SystemClock.nowIso()
        )
        recordStore?.saveTask(task)
        auditLedger.record("task.started", "Started ${task.id} in ${task.mode}")
        auditLedger.record("intent.routed", "app_task:${task.goal}")

        val context = ContextBundle(
            id = "ctx_${task.id}",
            taskId = task.id,
            purpose = "app_task",
            items = emptyMap()
        )
        val title = utterance
            .replace("remind me to", "", ignoreCase = true)
            .replace("add a task to", "", ignoreCase = true)
            .replace("add task to", "", ignoreCase = true)
            .replace("todo", "", ignoreCase = true)
            .trim()
            .ifBlank { "Follow up from voice request" }
        val plan = Plan(
            id = "plan_${task.id}",
            taskId = task.id,
            goal = task.goal,
            recommendation = Recommendation(
                id = "app_task",
                title = "App task",
                startsAtIso = task.createdAtIso,
                distanceMiles = 0.0,
                score = 100
            ),
            steps = listOf(
                PlanStep(
                    id = "add_voice_task",
                    title = "Add task in taught app",
                    tool = "app_agent.custom",
                    actionType = "tasks.add",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf("title" to title)
                )
            )
        )

        val firstPass = plan.steps.map {
            runStep(
                step = it,
                userPolicy = policy,
                approvedApprovalIds = approvedApprovalIds,
                approvedApprovalDecisions = approvedApprovalDecisions,
                deniedApprovalIds = deniedApprovalIds,
                userId = task.userId
            )
        }
        val approved = firstPass.filter { it.status == StepStatus.SUCCEEDED && it.approval != null }
        persistOperationTimeline(task, plan, firstPass)
        auditLedger.record(
            "task.completed",
            "intent=app_task, firstPass=${firstPass.count { it.status == StepStatus.SUCCEEDED }}, approvals=${approved.size}"
        )
        return WorkflowResult(
            task = task,
            context = context,
            plan = plan,
            firstPassResults = firstPass,
            approvedResults = approved,
            approvedApprovalIds = approvedApprovalIds,
            deniedApprovalIds = deniedApprovalIds,
            audit = auditLedger.all()
        )
    }

    private fun runGeneralMobileIntentWorkflow(
        intentType: String,
        policy: UserPolicy,
        approvedApprovalIds: Set<String>,
        approvedApprovalDecisions: List<StoredApprovalDecision>,
        deniedApprovalIds: Set<String>,
        userId: String,
        utterance: String,
        forcedPlaybookId: String = ""
    ): WorkflowResult {
        val task = Task(
            id = "task_general_mobile_intent",
            userId = userId,
            goal = utterance,
            intentType = intentType,
            mode = policy.mode,
            createdAtIso = SystemClock.nowIso()
        )
        recordStore?.saveTask(task)
        auditLedger.record("task.started", "Started ${task.id} in ${task.mode}")
        auditLedger.record("intent.routed", "$intentType:${task.goal}")

        val context = ContextBundle(
            id = "ctx_${task.id}",
            taskId = task.id,
            purpose = intentType,
            items = emptyMap()
        )
        val storedPlaybooks = recordStore?.appOperationPlaybooks().orEmpty()
        val registry = AppOperationPlaybookRegistry(customPlaybooks = storedPlaybooks)
        val forcedPlaybook = forcedPlaybookId.takeIf { it.isNotBlank() }?.let { registry.find(it) }
        val playbookMatch = if (forcedPlaybook == null) {
            registry.matchUtterance(utterance)
        } else {
            null
        }
        val observedPlaybook = forcedPlaybook ?: if (playbookMatch?.isAmbiguous == true) {
            null
        } else {
            playbookMatch?.matches?.firstOrNull()
                ?: storedPlaybooks.firstOrNull { it.actionType.startsWith("app_agent.observed.") }
        }
        val steps = when {
            playbookMatch?.isAmbiguous == true -> {
                val options = playbookMatch.matches.joinToString("|") {
                    "${it.packageName}:${it.actionType}"
                }
                auditLedger.record("intent.app_route_ambiguous", options)
                listOf(
                    PlanStep(
                        id = "clarify_app_agent_route",
                        title = "Ask which app-agent should handle this",
                        tool = "assistant.answer",
                        actionType = "app_agent.route.clarify",
                        risk = Risk.LOW,
                        externalSideEffect = false,
                        input = mapOf(
                            "utterance" to utterance,
                            "options" to options
                        )
                    )
                )
            }
            observedPlaybook != null -> listOf(
                PlanStep(
                    id = "operate_observed_app",
                    title = "Operate taught app",
                    tool = "app_agent.custom",
                    actionType = observedPlaybook.actionType,
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = inputForObservedPlaybook(observedPlaybook, utterance)
                )
            )
            else -> listOf(
                PlanStep(
                    id = "answer_general_mobile_intent",
                    title = "Summarize mobile request",
                    tool = "assistant.answer",
                    actionType = "answer.generate",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf("utterance" to utterance, "intentType" to intentType)
                )
            )
        }
        if (observedPlaybook != null) {
            auditLedger.record(
                if (forcedPlaybook != null) "intent.app_route_clarified" else "intent.observed_app_route",
                "${observedPlaybook.packageName}:${observedPlaybook.actionType}"
            )
        }
        val plan = Plan(
            id = "plan_${task.id}",
            taskId = task.id,
            goal = task.goal,
            recommendation = Recommendation(
                id = "general_mobile_intent",
                title = "General mobile request",
                startsAtIso = task.createdAtIso,
                distanceMiles = 0.0,
                score = 50
            ),
            steps = steps
        )
        val firstPass = plan.steps.map {
            runStep(
                step = it,
                userPolicy = policy,
                approvedApprovalIds = approvedApprovalIds,
                approvedApprovalDecisions = approvedApprovalDecisions,
                deniedApprovalIds = deniedApprovalIds,
                userId = task.userId
            )
        }
        val approved = firstPass.filter { it.status == StepStatus.SUCCEEDED && it.approval != null }
        persistOperationTimeline(task, plan, firstPass)
        auditLedger.record(
            "task.completed",
            "intent=$intentType, firstPass=${firstPass.count { it.status == StepStatus.SUCCEEDED }}, approvals=${approved.size}"
        )
        return WorkflowResult(
            task = task,
            context = context,
            plan = plan,
            firstPassResults = firstPass,
            approvedResults = approved,
            approvedApprovalIds = approvedApprovalIds,
            deniedApprovalIds = deniedApprovalIds,
            audit = auditLedger.all()
        )
    }

    private fun inputForObservedPlaybook(
        playbook: app.conductor.operator.accessibility.AppOperationPlaybook,
        utterance: String
    ): Map<String, String> {
        val input = playbook.requiredInputKeys.associateWith { key ->
            when (key) {
                "title" -> utterance
                    .replace("add", "", ignoreCase = true)
                    .replace("create", "", ignoreCase = true)
                    .trim()
                    .ifBlank { "Follow up from voice request" }
                "body", "exactBody", "text", "query" -> utterance
                else -> utterance
            }
        }.toMutableMap()
        if (playbook.requiredSourceIds.isNotEmpty()) {
            input["__requiredSourceIds"] = playbook.requiredSourceIds.toList().sorted().joinToString(",")
        }
        return input
    }

    private fun persistOperationTimeline(
        task: Task,
        plan: Plan,
        results: List<ToolResult>
    ) {
        val store = recordStore ?: return
        val resultsByStep = results.associateBy { it.stepId }
        val playbooks = AppOperationPlaybookRegistry(customPlaybooks = store.appOperationPlaybooks()).all()
        val queued = store.queuedAppOperations()
        val events = plan.steps.mapIndexed { index, step ->
            val result = resultsByStep[step.id]
            val queuedForStep = queued.firstOrNull { item ->
                val queuedPlaybook = playbooks.firstOrNull {
                    it.id == item.request.playbookId &&
                        it.packageName == item.request.packageName
                }
                queuedPlaybook?.actionType == step.actionType
            }
            val verification = result?.verification?.let { "${it.status}:${it.method}" }
            val summary = listOfNotNull(
                result?.error?.takeIf { it.isNotBlank() },
                queuedForStep?.let { "${it.primaryActionLabel}: ${it.reason}" },
                verification
            ).firstOrNull().orEmpty()
            OperationTimelineEvent(
                id = "${task.id}_event_${index + 1}",
                stepId = step.id,
                actionType = step.actionType,
                tool = step.tool,
                status = queuedForStep?.let { "QUEUED_HANDOFF" } ?: (result?.status?.name ?: "PLANNED"),
                policyDecision = result?.policy?.decision?.name.orEmpty(),
                approvalId = result?.approval?.id.orEmpty(),
                packageName = queuedForStep?.request?.packageName.orEmpty(),
                playbookId = queuedForStep?.request?.playbookId.orEmpty(),
                sourceScope = queuedForStep?.request?.requiredSourceIds?.toList()?.sorted()
                    ?: step.input["__requiredSourceIds"]
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.sorted()
                        .orEmpty(),
                summary = summary.ifBlank { step.title }
            )
        }
        store.saveOperationTimeline(
            OperationTimeline(
                id = "timeline_${task.id}",
                taskId = task.id,
                userId = task.userId,
                intentType = task.intentType,
                autonomyMode = task.mode,
                startedAtIso = task.createdAtIso,
                updatedAtIso = SystemClock.nowIso(),
                events = events
            )
        )
        auditLedger.record("operation.timeline_saved", "${task.id}:events=${events.size}")
    }

    private fun runStep(
        step: PlanStep,
        userPolicy: UserPolicy,
        approvedApprovalIds: Set<String>,
        approvedApprovalDecisions: List<StoredApprovalDecision>,
        deniedApprovalIds: Set<String>,
        userId: String
    ): ToolResult {
        val policy = policyEngine.evaluate(step, userPolicy)
        auditLedger.record("policy.evaluated", "${step.id} ${step.actionType} ${policy.decision}")

        if (policy.decision == PolicyDecision.BLOCK) {
            return ToolResult(stepId = step.id, status = StepStatus.BLOCKED, policy = policy)
        }

        val approval = ApprovalCard(
            id = "approval_${step.id}",
            stepId = step.id,
            actionType = step.actionType,
            exactContent = step.input["exactBody"] ?: step.input["body"],
            recipient = step.input["recipient"],
            reason = policy.reason
        )

        if (policy.decision == PolicyDecision.REQUIRE_APPROVAL && deniedApprovalIds.contains(approval.id)) {
            auditLedger.record("approval.denied", "Denied ${approval.id} for ${step.actionType}")
            return ToolResult(
                stepId = step.id,
                status = StepStatus.BLOCKED,
                policy = policy,
                approval = approval.copy(status = ApprovalStatus.DENIED),
                error = "User denied ${approval.id}"
            )
        }

        val exactApproved = approvedApprovalDecisions.any {
            it.id == approval.id &&
                it.status == ApprovalStatus.APPROVED &&
                it.actionType == approval.actionType &&
                it.exactContent == approval.exactContent
        }
        if (policy.decision == PolicyDecision.REQUIRE_APPROVAL && !exactApproved) {
            if (approvedApprovalIds.contains(approval.id)) {
                auditLedger.record("approval.rejected", "Rejected ${approval.id} because stored exact content did not match ${step.actionType}")
            }
            auditLedger.record("approval.queued", "Queued ${approval.id} for ${step.actionType}")
            return ToolResult(
                stepId = step.id,
                status = StepStatus.AWAITING_APPROVAL,
                policy = policy,
                approval = approval
            )
        }

        if (policy.decision == PolicyDecision.REQUIRE_APPROVAL) {
            auditLedger.record("approval.granted", "Approved ${approval.id} for ${step.actionType}")
        }

        val approvedApproval = if (policy.decision == PolicyDecision.REQUIRE_APPROVAL) {
            approval.copy(status = ApprovalStatus.APPROVED)
        } else {
            null
        }
        val result = toolRegistry.execute(step, approvedApproval, userId = userId)
        return if (policy.decision == PolicyDecision.REQUIRE_APPROVAL) {
            result.copy(policy = policy, approval = approvedApproval)
        } else {
            result.copy(policy = policy)
        }
    }
}

data class WorkflowResult(
    val task: Task,
    val context: ContextBundle,
    val plan: Plan,
    val firstPassResults: List<ToolResult>,
    val approvedResults: List<ToolResult>,
    val approvedApprovalIds: Set<String>,
    val deniedApprovalIds: Set<String>,
    val audit: List<AuditEvent>
)
