package xyz.aprildown.timer.domain.usecases.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepLabelParserTest {

    @Test
    fun intensity() {
        assertEquals(100.0, StepLabelParser.parse("100%").intensityPercent)
        assertEquals(90.0, StepLabelParser.parse("90% effort").intensityPercent)
        assertEquals(90.0, StepLabelParser.parse("5m @ 90% (100 RPM)").intensityPercent)
        assertEquals(130.0, StepLabelParser.parse("130% Same gear (RPM🆙)").intensityPercent)
    }

    @Test
    fun `intensity range uses average`() {
        assertEquals(40.0, StepLabelParser.parse("30-50% effort").intensityPercent)
        assertEquals(35.0, StepLabelParser.parse("20 - 50 %").intensityPercent)
    }

    @Test
    fun `intensity unspecified`() {
        assertNull(StepLabelParser.parse("recovery").intensityPercent)
        assertNull(StepLabelParser.parse("Seated").intensityPercent)
    }

    @Test
    fun cadence() {
        assertEquals(85.0, StepLabelParser.parse("85 RPM").cadenceRpm)
        assertEquals(110.0, StepLabelParser.parse("120% 110rpm").cadenceRpm)
        assertEquals(110.0, StepLabelParser.parse("120% @ 110+ rpm").cadenceRpm)
        assertEquals(85.0, StepLabelParser.parse("+1 Gear >=85 RPM").cadenceRpm)
        assertEquals(100.0, StepLabelParser.parse("5m @ 90% (100 RPM)").cadenceRpm)
        assertEquals(110.0, StepLabelParser.parse("Sprint").cadenceRpm)
    }

    @Test
    fun `cadence range uses average`() {
        assertEquals(95.0, StepLabelParser.parse("(90-100 RPM)").cadenceRpm)
        assertEquals(92.5, StepLabelParser.parse("Warmup 90-95 RPM").cadenceRpm)
        assertEquals(67.5, StepLabelParser.parse("2m Recovery - 65-70 RPM").cadenceRpm)
    }

    @Test
    fun `cadence unspecified`() {
        assertNull(StepLabelParser.parse("recovery").cadenceRpm)
    }

    @Test
    fun position() {
        assertEquals(true, StepLabelParser.parse("Stand").isStanding)
        assertEquals(true, StepLabelParser.parse("Standing 100% @70RPM").isStanding)
        assertEquals(true, StepLabelParser.parse("Stand (+2 gear)").isStanding)
        assertEquals(false, StepLabelParser.parse("Sit").isStanding)
        assertEquals(false, StepLabelParser.parse("Seated 90%").isStanding)
        assertNull(StepLabelParser.parse("recovery").isStanding)
    }

    @Test
    fun `gear ramp`() {
        assertEquals(1, StepLabelParser.parse("+1 Gear").gearDelta)
        assertEquals(1, StepLabelParser.parse("add one gear").gearDelta)
        assertEquals(1, StepLabelParser.parse("Add 1 gear").gearDelta)
        assertEquals(1, StepLabelParser.parse("Start at 10, +1 gear").gearDelta)
        assertEquals(1, StepLabelParser.parse("+1 gear 90 RPM").gearDelta)
        assertEquals(-3, StepLabelParser.parse("Drop 3 gears 85RPM").gearDelta)
        assertEquals(0, StepLabelParser.parse("Gear 10").gearDelta)
    }

    @Test
    fun `gear count is an annotation when intensity or standing is present`() {
        assertEquals(0, StepLabelParser.parse("90% - 2 gears").gearDelta)
        assertEquals(0, StepLabelParser.parse("90% -1 gear 90 RPM").gearDelta)
        assertEquals(0, StepLabelParser.parse("Stand 100% (+3 gears)").gearDelta)
        assertEquals(0, StepLabelParser.parse("standing +2G").gearDelta)
        assertEquals(0, StepLabelParser.parse("Stand (+2 gear)").gearDelta)
    }

    @Test
    fun `warm up and cool down`() {
        assertEquals(true, StepLabelParser.parse("Warm up").isWarmUpOrCoolDown)
        assertEquals(true, StepLabelParser.parse("warmup").isWarmUpOrCoolDown)
        assertEquals(true, StepLabelParser.parse("5m Warmup").isWarmUpOrCoolDown)
        assertEquals(true, StepLabelParser.parse("Cool down").isWarmUpOrCoolDown)
        assertEquals(true, StepLabelParser.parse("Cooldown").isWarmUpOrCoolDown)
        assertEquals(true, StepLabelParser.parse("3m Cool Down").isWarmUpOrCoolDown)
        assertEquals(true, StepLabelParser.parse("Cool down/Stretch").isWarmUpOrCoolDown)
        assertEquals(false, StepLabelParser.parse("Recovery").isWarmUpOrCoolDown)
    }
}