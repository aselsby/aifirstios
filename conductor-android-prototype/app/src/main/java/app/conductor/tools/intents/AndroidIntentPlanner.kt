package app.conductor.tools.intents

import app.conductor.runtime.PlanStep

class AndroidIntentPlanner {
    fun plan(step: PlanStep): AndroidIntentResult =
        when (step.tool) {
            "messages.create_draft" -> messageDraft(step)
            "calendar.create_hold" -> calendarHold(step)
            "maps.open_route" -> mapsRoute(step)
            else -> AndroidIntentResult(
                status = AndroidIntentStatus.UNSUPPORTED,
                reason = "No safe Android intent plan for ${step.tool}"
            )
        }

    private fun messageDraft(step: PlanStep): AndroidIntentResult {
        val body = step.input["body"]
        val recipient = step.input["recipient"]
        if (body.isNullOrBlank() || recipient.isNullOrBlank()) {
            return handoff("Message draft requires recipient and body.")
        }

        return AndroidIntentResult(
            status = AndroidIntentStatus.READY,
            plan = AndroidIntentPlan(
                id = "intent_${step.id}",
                action = "android.intent.action.SENDTO",
                dataUri = "smsto:$recipient",
                extras = mapOf("sms_body" to body),
                verificationMethod = "android_intent:draft_message_prefilled",
                requiresUserFinalTap = false
            )
        )
    }

    private fun calendarHold(step: PlanStep): AndroidIntentResult {
        val title = step.input["title"]
        val startsAtIso = step.input["startsAtIso"]
        if (title.isNullOrBlank() || startsAtIso.isNullOrBlank()) {
            return handoff("Calendar hold requires title and start time.")
        }

        return AndroidIntentResult(
            status = AndroidIntentStatus.READY,
            plan = AndroidIntentPlan(
                id = "intent_${step.id}",
                action = "android.intent.action.INSERT",
                dataUri = "content://com.android.calendar/events",
                extras = mapOf(
                    "title" to title,
                    "beginTimeIso" to startsAtIso,
                    "availability" to "tentative"
                ),
                verificationMethod = "android_intent:calendar_hold_tentative",
                requiresUserFinalTap = true
            )
        )
    }

    private fun mapsRoute(step: PlanStep): AndroidIntentResult {
        val destination = step.input["destination"]
        if (destination.isNullOrBlank()) {
            return handoff("Maps route requires destination.")
        }

        return AndroidIntentResult(
            status = AndroidIntentStatus.READY,
            plan = AndroidIntentPlan(
                id = "intent_${step.id}",
                action = "android.intent.action.VIEW",
                dataUri = "google.navigation:q=${destination.replace(" ", "+")}",
                packageName = "com.google.android.apps.maps",
                verificationMethod = "android_intent:maps_route_preview",
                requiresUserFinalTap = false
            )
        )
    }

    private fun handoff(reason: String): AndroidIntentResult =
        AndroidIntentResult(
            status = AndroidIntentStatus.NEEDS_HANDOFF,
            reason = reason
        )
}
