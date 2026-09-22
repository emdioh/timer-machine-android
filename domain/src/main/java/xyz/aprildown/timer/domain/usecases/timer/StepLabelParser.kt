package xyz.aprildown.timer.domain.usecases.timer

/**
 * Parses workout information out of a step or group label.
 *
 * There is no structured intensity/cadence/position data in a timer, so we infer it from
 * the free-form label, e.g. `100% @85RPM Seated`. Everything that cannot be inferred falls
 * back to the defaults documented on [StepLabelInfo].
 */
object StepLabelParser {

    private const val INTENSITY_RANGE = """(\d+(?:\.\d+)?)\s*(?:-|–|to)\s*(\d+(?:\.\d+)?)\s*%"""
    private const val INTENSITY = """(\d+(?:\.\d+)?)\s*%"""
    private const val CADENCE_RANGE = """(\d+)\s*(?:-|–|to)\s*(\d+)\s*rpm"""
    private const val CADENCE = """(\d+)\s*\+?\s*rpm"""
    private const val STANDING = """\bstand(?:ing)?\b"""
    private const val SEATED = """\b(?:sit|seated|seat)\b"""
    private const val SPRINT = """\bsprint"""
    private const val GEAR_ADD = """add\s+(?:one|1)\s+gear"""
    private const val GEAR_PLUS = """\+\s*(\d+)\s*gear"""
    private const val GEAR_DROP = """drop\s+(\d+)\s+gears?"""
    private const val WARM_UP_OR_COOL_DOWN =
        """warm\s*-?\s*up|warmup|cool\s*-?\s*down|cooldown"""

    private val intensityRangeRegex = Regex(INTENSITY_RANGE, RegexOption.IGNORE_CASE)
    private val intensityRegex = Regex(INTENSITY, RegexOption.IGNORE_CASE)
    private val cadenceRangeRegex = Regex(CADENCE_RANGE, RegexOption.IGNORE_CASE)
    private val cadenceRegex = Regex(CADENCE, RegexOption.IGNORE_CASE)
    private val standingRegex = Regex(STANDING, RegexOption.IGNORE_CASE)
    private val seatedRegex = Regex(SEATED, RegexOption.IGNORE_CASE)
    private val sprintRegex = Regex(SPRINT, RegexOption.IGNORE_CASE)
    private val gearAddRegex = Regex(GEAR_ADD, RegexOption.IGNORE_CASE)
    private val gearPlusRegex = Regex(GEAR_PLUS, RegexOption.IGNORE_CASE)
    private val gearDropRegex = Regex(GEAR_DROP, RegexOption.IGNORE_CASE)
    private val warmUpOrCoolDownRegex = Regex(WARM_UP_OR_COOL_DOWN, RegexOption.IGNORE_CASE)

    /**
     * @param intensityPercent Explicit intensity, or null when unspecified. Ranges (`30-50%`)
     * use their average. Defaults to 100.
     * @param cadenceRpm Explicit cadence, or null when unspecified. Ranges (`85-90 RPM`) use
     * their average; `>= 85 RPM` and `110+ RPM` use the bound; `sprint` maps to 110.
     * Defaults to the enclosing group cadence or 85.
     * @param isStanding True for standing, false for seated, null when unspecified.
     * Defaults to seated.
     * @param gearDelta Number of gears to increase (negative to drop). Each gear is worth
     * 10% intensity. Always 0 when an explicit intensity is present or the line is standing,
     * because gear counts there are annotations rather than intensity changes.
     * @param isWarmUpOrCoolDown True for warm-up/cool-down labels, which are excluded from
     * the hardness calculation.
     */
    data class StepLabelInfo(
        val intensityPercent: Double? = null,
        val cadenceRpm: Double? = null,
        val isStanding: Boolean? = null,
        val gearDelta: Int = 0,
        val isWarmUpOrCoolDown: Boolean = false,
    )

    fun parse(label: String): StepLabelInfo {
        val intensity = parseIntensity(label)
        val isStanding = parseStanding(label)
        return StepLabelInfo(
            intensityPercent = intensity,
            cadenceRpm = parseCadence(label),
            isStanding = isStanding,
            gearDelta = if (intensity != null || isStanding == true) 0 else parseGearDelta(label),
            isWarmUpOrCoolDown = warmUpOrCoolDownRegex.containsMatchIn(label),
        )
    }

    internal fun parseIntensity(label: String): Double? {
        intensityRangeRegex.find(label)?.let {
            return (it.groupValues[1].toDouble() + it.groupValues[2].toDouble()) / 2.0
        }
        return intensityRegex.find(label)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    internal fun parseCadence(label: String): Double? {
        cadenceRangeRegex.find(label)?.let {
            return (it.groupValues[1].toDouble() + it.groupValues[2].toDouble()) / 2.0
        }
        cadenceRegex.find(label)?.let {
            return it.groupValues[1].toDoubleOrNull()
        }
        return if (sprintRegex.containsMatchIn(label)) SPRINT_RPM else null
    }

    private fun parseStanding(label: String): Boolean? {
        return when {
            standingRegex.containsMatchIn(label) -> true
            seatedRegex.containsMatchIn(label) -> false
            else -> null
        }
    }

    private fun parseGearDelta(label: String): Int {
        if (gearAddRegex.containsMatchIn(label)) return 1
        gearPlusRegex.find(label)?.let { return it.groupValues[1].toInt() }
        gearDropRegex.find(label)?.let { return -it.groupValues[1].toInt() }
        return 0
    }

    const val SPRINT_RPM = 110.0
}