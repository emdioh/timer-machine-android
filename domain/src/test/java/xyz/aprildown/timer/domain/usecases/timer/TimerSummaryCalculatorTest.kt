package xyz.aprildown.timer.domain.usecases.timer

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.aprildown.timer.domain.entities.BehaviourEntity
import xyz.aprildown.timer.domain.entities.BehaviourType
import xyz.aprildown.timer.domain.entities.StepEntity
import xyz.aprildown.timer.domain.entities.StepType
import xyz.aprildown.timer.domain.entities.TimerEntity

private const val DELTA = 0.001

class TimerSummaryCalculatorTest {

    private fun step(
        label: String,
        length: Long,
        type: StepType = StepType.NORMAL,
        behaviour: List<BehaviourEntity> = emptyList(),
    ) = StepEntity.Step(label = label, length = length, behaviour = behaviour, type = type)

    private fun timer(
        loop: Int = 1,
        steps: List<StepEntity>,
        start: StepEntity.Step? = null,
        end: StepEntity.Step? = null,
    ) = TimerEntity(id = 1, name = "Timer", loop = loop, steps = steps, startStep = start, endStep = end)

    @Test
    fun `flat effort is one to one`() {
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(step("", 60_000))))
        assertEquals(60_000L, summary.totalDuration)
        assertEquals(60_000L, summary.activeDuration)
        assertEquals(60.0, summary.hardness, DELTA)
        assertEquals(1.0, summary.difficultyFactor, DELTA)
    }

    @Test
    fun `intensity is squared`() {
        val half = TimerSummaryCalculator.calculate(timer(steps = listOf(step("50%", 60_000))))
        assertEquals(15.0, half.hardness, DELTA)

        val high = TimerSummaryCalculator.calculate(timer(steps = listOf(step("120%", 60_000))))
        assertEquals(86.4, high.hardness, DELTA)
    }

    @Test
    fun `intensity range uses average`() {
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(step("30-50%", 60_000))))
        assertEquals(9.6, summary.hardness, DELTA)
    }

    @Test
    fun `cadence parabola`() {
        val low = TimerSummaryCalculator.calculate(timer(steps = listOf(step("55 RPM", 60_000))))
        assertEquals(65.4, low.hardness, DELTA)

        val high = TimerSummaryCalculator.calculate(timer(steps = listOf(step("115 RPM", 60_000))))
        assertEquals(65.4, high.hardness, DELTA)
    }

    @Test
    fun `standing multiplies by 1_12`() {
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(step("Stand", 60_000))))
        assertEquals(67.2, summary.hardness, DELTA)
    }

    @Test
    fun `warm up and cool down are excluded from hardness but not duration`() {
        val summary = TimerSummaryCalculator.calculate(
            timer(steps = listOf(step("Warm up", 60_000), step("100%", 60_000)))
        )
        assertEquals(120_000L, summary.totalDuration)
        assertEquals(60_000L, summary.activeDuration)
        assertEquals(60.0, summary.hardness, DELTA)
    }

    @Test
    fun `group name warm up applies to children`() {
        val group = StepEntity.Group(
            name = "Warmup 90-95 RPM",
            loop = 1,
            steps = listOf(step("90%", 60_000)),
        )
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(group)))
        assertEquals(60_000L, summary.totalDuration)
        assertEquals(0L, summary.activeDuration)
        assertEquals(0.0, summary.hardness, DELTA)
    }

    @Test
    fun `short notifier is excluded from hardness but not duration`() {
        val summary = TimerSummaryCalculator.calculate(
            timer(steps = listOf(step("End of Run", 10_000, type = StepType.NOTIFIER)))
        )
        assertEquals(10_000L, summary.totalDuration)
        assertEquals(0L, summary.activeDuration)
        assertEquals(0.0, summary.hardness, DELTA)
    }

    @Test
    fun `long notifier counts`() {
        val summary = TimerSummaryCalculator.calculate(
            timer(steps = listOf(step("End of Run", 20_000, type = StepType.NOTIFIER)))
        )
        assertEquals(20_000L, summary.totalDuration)
        assertEquals(20_000L, summary.activeDuration)
        assertEquals(20.0, summary.hardness, DELTA)
    }

    @Test
    fun `timer loop multiplies`() {
        val summary = TimerSummaryCalculator.calculate(
            timer(loop = 3, steps = listOf(step("100%", 10_000)))
        )
        assertEquals(30_000L, summary.totalDuration)
        assertEquals(30.0, summary.hardness, DELTA)
    }

    @Test
    fun `group loop multiplies`() {
        val group = StepEntity.Group(
            name = "Set",
            loop = 4,
            steps = listOf(step("100%", 10_000), step("50%", 10_000)),
        )
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(group)))
        assertEquals(80_000L, summary.totalDuration)
        assertEquals(40.0 + 10.0, summary.hardness, DELTA)
    }

    @Test
    fun `step inherits group cadence and can override it`() {
        val group = StepEntity.Group(
            name = "70 rpm set",
            loop = 1,
            steps = listOf(step("90%", 60_000), step("90% 100 RPM", 60_000)),
        )
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(group)))
        val inherited = 60.0 * 0.81 * (1 + 0.0001 * (70 - 85) * (70 - 85))
        val overridden = 60.0 * 0.81 * (1 + 0.0001 * (100 - 85) * (100 - 85))
        assertEquals(inherited + overridden, summary.hardness, DELTA)
    }

    @Test
    fun `gear ramp is cumulative`() {
        val group = StepEntity.Group(
            name = "10 Increases",
            loop = 3,
            steps = listOf(step("+1 gear", 10_000)),
        )
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(group)))
        val expected = 10.0 * (1.21 + 1.44 + 1.69)
        assertEquals(expected, summary.hardness, DELTA)
    }

    @Test
    fun `explicit intensity resets the gear ramp`() {
        val group = StepEntity.Group(
            name = "Set",
            loop = 2,
            steps = listOf(step("50%", 10_000), step("+1 gear", 10_000), step("+1 gear", 10_000)),
        )
        val summary = TimerSummaryCalculator.calculate(timer(steps = listOf(group)))
        val expected = 2 * 10.0 * (0.25 + 0.36 + 0.49)
        assertEquals(expected, summary.hardness, DELTA)
    }

    @Test
    fun `start and end steps are included`() {
        val summary = TimerSummaryCalculator.calculate(
            timer(
                steps = listOf(step("100%", 60_000)),
                start = step("Timer Start", 5_000),
                end = step("Timer End", 5_000),
            )
        )
        assertEquals(70_000L, summary.totalDuration)
        assertEquals(70_000L, summary.activeDuration)
        assertEquals(70.0, summary.hardness, DELTA)
    }

    @Test
    fun `skip behaviour excludes iterations`() {
        val skipLast = step(
            label = "100%",
            length = 10_000,
            behaviour = listOf(BehaviourEntity(BehaviourType.SKIP, str1 = "-2")),
        )
        val summary = TimerSummaryCalculator.calculate(
            timer(loop = 3, steps = listOf(skipLast))
        )
        assertEquals(20_000L, summary.totalDuration)
        assertEquals(20.0, summary.hardness, DELTA)
    }

    @Test
    fun `difficulty level bands`() {
        assertEquals(
            xyz.aprildown.timer.domain.entities.TimerDifficultyLevel.EASY,
            TimerSummaryCalculator.calculate(timer(steps = listOf(step("80%", 60_000)))).difficultyLevel
        )
        assertEquals(
            xyz.aprildown.timer.domain.entities.TimerDifficultyLevel.MODERATE,
            TimerSummaryCalculator.calculate(timer(steps = listOf(step("95%", 60_000)))).difficultyLevel
        )
        assertEquals(
            xyz.aprildown.timer.domain.entities.TimerDifficultyLevel.HARD,
            TimerSummaryCalculator.calculate(timer(steps = listOf(step("102%", 60_000)))).difficultyLevel
        )
        assertEquals(
            xyz.aprildown.timer.domain.entities.TimerDifficultyLevel.VERY_HARD,
            TimerSummaryCalculator.calculate(timer(steps = listOf(step("120%", 60_000)))).difficultyLevel
        )
    }
}