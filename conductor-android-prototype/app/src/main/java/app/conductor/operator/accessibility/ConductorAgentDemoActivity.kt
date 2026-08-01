package app.conductor.operator.accessibility

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.widget.doAfterTextChanged

/**
 * Deterministic in-app surface for proving live AccessibilityService operation.
 * Labels are stable so G4 (live tree verification) can pass without OEM Messages.
 */
class ConductorAgentDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            contentDescription = "Conductor agent demo root"
        }
        val proof = TextView(this).apply {
            text = ACCOUNT_PROOF
            textSize = 18f
            contentDescription = ACCOUNT_PROOF
        }
        val title = TextView(this).apply {
            text = "Agent demo surface"
            textSize = 22f
        }
        val input = EditText(this).apply {
            hint = INPUT_LABEL
            contentDescription = INPUT_LABEL
            // Seed a unique searchable label so findAccessibilityNodeInfosByText can target the field.
            setText(INPUT_LABEL)
            textSize = 16f
        }
        val status = TextView(this).apply {
            text = "demo idle"
            textSize = 16f
            contentDescription = "demo status"
        }
        input.doAfterTextChanged { editable ->
            val value = editable?.toString().orEmpty()
            status.text = if (value.isNotBlank() && value != INPUT_LABEL) {
                "demo draft ready"
            } else {
                "demo idle"
            }
            status.contentDescription = status.text
        }
        root.addView(proof)
        root.addView(title)
        root.addView(input)
        root.addView(status)
        setContentView(root)
    }

    companion object {
        const val ACCOUNT_PROOF = "Conductor demo signed in"
        const val INPUT_LABEL = "demo input field"
        const val READY_LABEL = "demo draft ready"
    }
}
