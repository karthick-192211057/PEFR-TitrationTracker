package com.example.pefrtitrationtracker

data class TriageResult(
    val urgency: String,
    val message: String,
    val actions: List<String>
)

object SymptomTriage {
    /**
     * Simple rule-based triage for asthma symptoms.
     * Inputs are 0-5 severity ratings and boolean exposures.
     * Returns urgency: "Green", "Yellow", or "Red" and guidance.
     */
    fun assess(
        wheeze: Int,
        cough: Int,
        dyspnea: Int,
        nightSymptoms: Int,
        dustExposure: Boolean,
        smokeExposure: Boolean
    ): TriageResult {
        val total = wheeze + cough + dyspnea + nightSymptoms

        // Red: severe shortness of breath or very high total
        if (dyspnea >= 4 || wheeze >= 4 || total >= 15) {
            return TriageResult(
                urgency = "Red",
                message = "Severe symptoms detected. Please seek immediate medical attention or emergency services.",
                actions = listOf("Use reliever inhaler now", "Call emergency services", "Notify caregiver")
            )
        }

        // Yellow: moderate symptoms, recommend contacting clinician soon
        if (dyspnea >= 3 || wheeze >= 3 || nightSymptoms >= 3 || total >= 8 || dustExposure || smokeExposure) {
            return TriageResult(
                urgency = "Yellow",
                message = "Moderate symptoms — use reliever inhaler and contact your clinician if symptoms persist.",
                actions = listOf("Use reliever inhaler", "Contact your doctor", "Monitor symptoms closely")
            )
        }

        // Green: mild or no symptoms
        return TriageResult(
            urgency = "Green",
            message = "Symptoms are mild or absent. Continue regular treatment and monitor.",
            actions = listOf("Continue controller medication", "Monitor symptoms", "Record PEFR if available")
        )
    }
}
