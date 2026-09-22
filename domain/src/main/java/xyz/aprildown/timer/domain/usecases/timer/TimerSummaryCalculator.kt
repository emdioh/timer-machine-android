package xyz.aprildown.timer.domain.usecases.timer

import kotlin.math.pow
import xyz.aprildown.timer.domain.entities.BehaviourType
import xyz.aprildown.timer.domain.entities.SkipAction
import xyz.aprildown.timer.domain.entities.StepEntity
import xyz.aprildown.timer.domain.entities.StepType
import xyz.aprildown.timer.domain.entities.TimerEntity
import xyz.aprildown.timer.domain.entities.TimerSummary
import xyz.aprildown.timer.domain.entities.toSkipAction

/**
 * Computes a [TimerSummary] by walking a timer the same way `TimerEntity.getTotalTime()` does:
 * over the start step, the looped steps and groups, and the end step, honoring skip behaviours.
 *
 * Per step the difficulty is
 * `seconds * (intensity / 100)^2 * positionMultiplier * cadenceMultiplier`.
 */
object TimerSummaryCalculator {

    private const val DEFAULT_INTENSITY = 100.0
    private const val DEFAULT_CADENCE_RPM = 85.0
    private const val GEAR_INTENSITY_STEP = 10.0
    private const val MIN_INTENSITY = 0.0
    private const val STANDING_MULTIPLIER = 1.12
    private const val CADENCE_BASELINE_RPM = 85.0
    private const val CADENCE_COEFFICIENT = 0.0001
    private const val SHORT_NOTIFIER_MS = 15_000L

    fun calculate(timer: TimerEntity): TimerSummary {
        val accumulator = Accumulator()
        val intensityState = IntensityState()
        timer.startStep?.let { step ->
            visitStep(
                step = step,
                context = null,
                intensityState = intensityState,
                accumulator = accumulator,
                loopIndex = 0,
                maxLoop = timer.loop,
            )
        }
        for (loopIndex in 0 until timer.loop) {
            timer.steps.forEach { step ->
                visit(
                    step = step,
                    context = null,
                    intensityState = intensityState,
                    accumulator = accumulator,
                    loopIndex = loopIndex,
                    maxLoop = timer.loop,
                )
            }
        }
        timer.endStep?.let { step ->
            visitStep(
                step = step,
                context = null,
                intensityState = intensityState,
                accumulator = accumulator,
                loopIndex = timer.loop - 1,
                maxLoop = timer.loop,
            )
        }
        return TimerSummary(
            id = timer.id,
            name = timer.name,
            folderId = timer.folderId,
            totalDuration = accumulator.totalDuration,
            activeDuration = accumulator.activeDuration,
            hardness = accumulator.hardness,
        )
    }

    private fun visit(
        step: StepEntity,
        context: GroupContext?,
        intensityState: IntensityState,
        accumulator: Accumulator,
        loopIndex: Int,
        maxLoop: Int,
    ) {
        when (step) {
            is StepEntity.Step -> visitStep(
                step = step,
                context = context,
                intensityState = intensityState,
                accumulator = accumulator,
                loopIndex = loopIndex,
                maxLoop = maxLoop,
            )

            is StepEntity.Group -> {
                val info = StepLabelParser.parse(step.name)
                val childContext = GroupContext(
                    cadenceRpm = info.cadenceRpm ?: context?.cadenceRpm,
                    isStanding = info.isStanding ?: context?.isStanding,
                    isWarmUpOrCoolDown =
                        info.isWarmUpOrCoolDown || context?.isWarmUpOrCoolDown == true,
                )
                for (groupLoopIndex in 0 until step.loop) {
                    step.steps.forEach { child ->
                        visit(
                            step = child,
                            context = childContext,
                            intensityState = intensityState,
                            accumulator = accumulator,
                            loopIndex = groupLoopIndex,
                            maxLoop = step.loop,
                        )
                    }
                }
            }
        }
    }

    private fun visitStep(
        step: StepEntity.Step,
        context: GroupContext?,
        intensityState: IntensityState,
        accumulator: Accumulator,
        loopIndex: Int,
        maxLoop: Int,
    ) {
        if (step.shouldSkip(loopIndex = loopIndex, maxLoop = maxLoop)) return

        val length = step.length
        accumulator.totalDuration += length

        val info = StepLabelParser.parse(step.label)
        if (info.isWarmUpOrCoolDown || context?.isWarmUpOrCoolDown == true) return
        if (step.type == StepType.NOTIFIER && length < SHORT_NOTIFIER_MS) return

        val intensity = resolveIntensity(info = info, intensityState = intensityState)
        val cadence = info.cadenceRpm ?: context?.cadenceRpm ?: DEFAULT_CADENCE_RPM
        val isStanding = info.isStanding ?: context?.isStanding ?: false

        val factor = (intensity / 100.0).pow(2) *
            (if (isStanding) STANDING_MULTIPLIER else 1.0) *
            (1.0 + CADENCE_COEFFICIENT * (cadence - CADENCE_BASELINE_RPM).pow(2))

        accumulator.activeDuration += length
        accumulator.hardness += length / 1000.0 * factor
    }

    private fun resolveIntensity(
        info: StepLabelParser.StepLabelInfo,
        intensityState: IntensityState,
    ): Double {
        info.intensityPercent?.let {
            intensityState.intensity = it
            return it
        }
        if (info.gearDelta != 0) {
            intensityState.intensity =
                (intensityState.intensity + info.gearDelta * GEAR_INTENSITY_STEP)
                    .coerceAtLeast(MIN_INTENSITY)
            return intensityState.intensity
        }
        return DEFAULT_INTENSITY
    }

    private fun StepEntity.Step.shouldSkip(loopIndex: Int, maxLoop: Int): Boolean {
        val target = behaviour.find { it.type == BehaviourType.SKIP }
            ?.toSkipAction()?.target
            ?: return false
        return when (target) {
            SkipAction.Target.Last -> loopIndex == maxLoop - 1
            SkipAction.Target.First -> loopIndex == 0
            is SkipAction.Target.Loops -> target.loopIndices.any { it == loopIndex }
        }
    }

    private class Accumulator {
        var totalDuration = 0L
        var activeDuration = 0L
        var hardness = 0.0
    }

    private class IntensityState {
        var intensity = DEFAULT_INTENSITY
    }

    private data class GroupContext(
        val cadenceRpm: Double?,
        val isStanding: Boolean?,
        val isWarmUpOrCoolDown: Boolean,
    )
}