package xyz.aprildown.timer.app.timer.list

import android.content.Context
import xyz.aprildown.timer.app.base.utils.produceTime
import xyz.aprildown.timer.domain.entities.TimerDifficultyLevel
import xyz.aprildown.timer.domain.entities.TimerSummary
import xyz.aprildown.timer.app.base.R as RBase

internal fun TimerSummary.toDisplayText(context: Context): String {
    val levelRes = when (difficultyLevel) {
        TimerDifficultyLevel.EASY -> RBase.string.timer_difficulty_easy
        TimerDifficultyLevel.MODERATE -> RBase.string.timer_difficulty_moderate
        TimerDifficultyLevel.HARD -> RBase.string.timer_difficulty_hard
        TimerDifficultyLevel.VERY_HARD -> RBase.string.timer_difficulty_very_hard
    }
    return context.getString(
        RBase.string.timer_summary_format,
        totalDuration.produceTime(),
        context.getString(levelRes),
        difficultyFactor,
    )
}