package xyz.aprildown.timer.domain.entities

/**
 * Lightweight summary of a timer for the timer list: its total duration plus the difficulty
 * metrics derived from its step labels.
 *
 * @param totalDuration Total play time in milliseconds, including warm-ups, cool-downs and
 * short notifier steps.
 * @param activeDuration Milliseconds of the steps that contribute to [hardness], i.e. excluding
 * warm-ups, cool-downs and notifier steps shorter than 15 seconds.
 * @param hardness Weighted duration (in seconds) of the active steps, where each second is
 * scaled by the intensity, cadence and body position of the current step.
 */
data class TimerSummary(
    val id: Int,
    val name: String,
    val folderId: Long = FolderEntity.FOLDER_DEFAULT,
    val totalDuration: Long,
    val activeDuration: Long,
    val hardness: Double,
) {
    /**
     * Dimensionless difficulty relative to a flat effort at 100% intensity, 85 RPM seated.
     * 0 when there is no active step.
     */
    val difficultyFactor: Double
        get() = if (activeDuration <= 0L) 0.0 else hardness / (activeDuration / 1000.0)

    val difficultyLevel: TimerDifficultyLevel
        get() = TimerDifficultyLevel.of(difficultyFactor)
}

enum class TimerDifficultyLevel {
    EASY,
    MODERATE,
    HARD,
    VERY_HARD;

    companion object {
        const val EASY_MAX = 0.90
        const val MODERATE_MAX = 1.00
        const val HARD_MAX = 1.10

        fun of(factor: Double): TimerDifficultyLevel = when {
            factor < EASY_MAX -> EASY
            factor < MODERATE_MAX -> MODERATE
            factor < HARD_MAX -> HARD
            else -> VERY_HARD
        }
    }
}