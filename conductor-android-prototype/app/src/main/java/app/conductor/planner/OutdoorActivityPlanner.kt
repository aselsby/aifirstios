package app.conductor.planner

import app.conductor.audit.AuditLedger
import app.conductor.runtime.ContextBundle
import app.conductor.runtime.Plan
import app.conductor.runtime.PlanStep
import app.conductor.runtime.Recommendation
import app.conductor.runtime.Risk
import app.conductor.runtime.Task

class OutdoorActivityPlanner(private val auditLedger: AuditLedger) {
    fun createPlan(task: Task, context: ContextBundle): Plan {
        val preferenceSummary = context.items["preferences"]?.summary.orEmpty()
        val eventsSummary = context.items["events"]?.summary.orEmpty()
        val weatherSummary = context.items["weather"]?.summary.orEmpty()
        val calendarSummary = context.items["calendar"]?.summary.orEmpty()
        val recommendation = recommendationFor(
            preferenceSummary = preferenceSummary,
            eventsSummary = eventsSummary,
            weatherSummary = weatherSummary,
            calendarSummary = calendarSummary
        )

        val invite = "Want to check out ${recommendation.title} at ${recommendation.startsAtIso.toFriendlyTime()}? Weather looks good and it is about ${recommendation.distanceMiles.toTransitMinutes()} minutes away."
        val publicPost = "Heading to ${recommendation.title} at ${recommendation.startsAtIso.toFriendlyTime()}. Weather looks perfect."

        val preferenceStep = context.items["preferences"]?.let { preferences ->
            PlanStep(
                id = "apply_user_preferences",
                title = "Apply remembered activity preferences",
                tool = "memory.preference.read",
                actionType = "memory.preference.read",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf(
                    "summary" to preferences.summary,
                    "factId" to preferences.factId,
                    "source" to preferences.source
                )
            )
        }

        val contextSteps = listOf(
            PlanStep(
                id = "check_calendar",
                title = "Check calendar availability",
                tool = "calendar.free_busy",
                actionType = "calendar.read",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf("summary" to context.items.getValue("calendar").summary)
            ),
            PlanStep(
                id = "check_weather",
                title = "Check outdoor weather window",
                tool = "weather.hourly",
                actionType = "weather.read",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf("summary" to context.items.getValue("weather").summary)
            ),
            PlanStep(
                id = "rank_events",
                title = "Rank nearby outdoor events",
                tool = "events.rank",
                actionType = "events.read",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf("source" to context.items.getValue("events").source)
            )
        ) + listOfNotNull(preferenceStep)

        val steps = contextSteps + listOf(
            PlanStep(
                id = "draft_invite",
                title = "Draft invite to Maya",
                tool = "messages.create_draft",
                actionType = "outbound_message.create_draft",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf("recipient" to "contact_maya", "body" to invite)
            ),
            PlanStep(
                id = "hold_calendar",
                title = "Create tentative calendar hold",
                tool = "calendar.create_hold",
                actionType = "calendar.hold.create",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf(
                    "title" to recommendation.title,
                    "startsAtIso" to recommendation.startsAtIso
                )
            ),
            PlanStep(
                id = "open_route",
                title = "Open route to the event",
                tool = "maps.open_route",
                actionType = "maps.route.open",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf("destination" to recommendation.title)
            ),
            PlanStep(
                id = "send_invite",
                title = "Send invite to Maya",
                tool = "messages.send",
                actionType = "outbound_message.send",
                risk = Risk.MEDIUM,
                externalSideEffect = true,
                input = mapOf("recipient" to "contact_maya", "exactBody" to invite)
            )
        ) + taughtTaskStepIfRequested(task.goal, recommendation.title) +
            publicPostStepIfRequested(task.goal, publicPost)

        auditLedger.record(
            type = "plan.created",
            detail = "Outdoor activity plan with ${steps.size} steps and recommendation ${recommendation.title}; preferences=${preferenceSummary.isNotBlank()}"
        )

        return Plan(
            id = "plan_${task.id}",
            taskId = task.id,
            goal = task.goal,
            recommendation = recommendation,
            steps = steps
        )
    }

    private fun publicPostStepIfRequested(goal: String, exactBody: String): List<PlanStep> =
        if (goal.contains("post", ignoreCase = true) || goal.contains("share publicly", ignoreCase = true)) {
            listOf(
                PlanStep(
                    id = "publish_event_post",
                    title = "Post event update",
                    tool = "facebook.post",
                    actionType = "public_post.create",
                    risk = Risk.HIGH,
                    externalSideEffect = true,
                    input = mapOf("exactBody" to exactBody)
                )
            )
        } else {
            emptyList()
        }

    private fun taughtTaskStepIfRequested(goal: String, title: String): List<PlanStep> =
        if (
            goal.contains("task", ignoreCase = true) ||
            goal.contains("todo", ignoreCase = true) ||
            goal.contains("remind", ignoreCase = true)
        ) {
            listOf(
                PlanStep(
                    id = "add_followup_task",
                    title = "Add follow-up task",
                    tool = "app_agent.custom",
                    actionType = "tasks.add",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf(
                        "title" to "Follow up on $title",
                        "__requiredSourceIds" to "google_calendar"
                    )
                )
            )
        } else {
            emptyList()
        }

    private fun recommendationFor(
        preferenceSummary: String,
        eventsSummary: String,
        weatherSummary: String,
        calendarSummary: String
    ): Recommendation {
        val lower = listOf(preferenceSummary, eventsSummary).joinToString(" ").lowercase()
        val weatherBoost = if (
            weatherSummary.contains("clear", ignoreCase = true) ||
            weatherSummary.contains("sunny", ignoreCase = true) ||
            weatherSummary.contains("good", ignoreCase = true)
        ) {
            2
        } else {
            0
        }
        val freeWindowBoost = if (
            calendarSummary.contains("free", ignoreCase = true) ||
            calendarSummary.contains("available", ignoreCase = true)
        ) {
            1
        } else {
            0
        }
        val base = when {
            lower.contains("free") || lower.contains("cheap") -> Recommendation(
                id = "event_free_park_walk",
                title = "Free Riverfront Park Walk",
                startsAtIso = "2026-07-27T15:00:00-05:00",
                distanceMiles = 1.1,
                score = 97
            )
            lower.contains("quiet") || lower.contains("low key") || lower.contains("low-key") -> Recommendation(
                id = "event_quiet_garden",
                title = "Quiet Botanical Garden Hour",
                startsAtIso = "2026-07-27T16:00:00-05:00",
                distanceMiles = 2.0,
                score = 96
            )
            lower.contains("walk") || lower.contains("walking") || lower.contains("trail") -> Recommendation(
                id = "event_walking_trail",
                title = "Shaded Lake Trail Meetup",
                startsAtIso = "2026-07-27T15:15:00-05:00",
                distanceMiles = 1.8,
                score = 95
            )
            lower.contains("jazz") || lower.contains("music") || lower.contains("concert") -> Recommendation(
                id = "event_001",
                title = "Outdoor Jazz At The Garden",
                startsAtIso = "2026-07-27T15:30:00-05:00",
                distanceMiles = 2.4,
                score = 94
            )
            else -> Recommendation(
                id = "event_001",
                title = "Outdoor Jazz At The Garden",
                startsAtIso = "2026-07-27T15:30:00-05:00",
                distanceMiles = 2.4,
                score = 94
            )
        }
        return base.copy(score = (base.score + weatherBoost + freeWindowBoost).coerceAtMost(100))
    }

    private fun String.toFriendlyTime(): String =
        substringAfter("T")
            .substringBeforeLast(":")
            .removePrefix("0")

    private fun Double.toTransitMinutes(): Int =
        (this * 5).toInt().coerceAtLeast(5)
}
