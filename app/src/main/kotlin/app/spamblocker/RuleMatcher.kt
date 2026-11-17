package app.spamblocker

data class Rule(val pattern: String, val enabled: Boolean = true)

data class LogEntry(val number: String, val pattern: String, val timeMillis: Long)

/** Pure logic, no Android types, so it is unit-testable. */
object RuleMatcher {

    /**
     * Reduces a number to an optional leading "+" followed by digits only:
     * "+386 1 888-9909" -> "+38618889909", "0038618889909" -> "+38618889909".
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    fun compile(pattern: String): Result<Regex> = runCatching { Regex(pattern) }

    /** The first enabled rule whose regex matches the WHOLE normalized number, or null. */
    fun firstMatch(rules: List<Rule>, number: String): Rule? {
        val n = normalize(number)
        if (n.isEmpty()) return null
        return rules.firstOrNull { rule ->
            rule.enabled && compile(rule.pattern).getOrNull()?.matches(n) == true
        }
    }

    /** Starter rules covering the numbers the user listed as spam. */
    val suggested: List<Rule> = listOf(
        """\+386[12]888\d{4}""",   // Slovenia +386 1 888 xxxx / +386 2 888 xxxx
        """\+3863150\d{4}""",      // Slovenia +386 3150 xxxx
        """\+35799\d{6}""",        // Cyprus mobile +357 99
        """\+31205326320""",       // Netherlands (Amsterdam), exact number
        """\+39(340|335)\d{7}""",  // Italy mobile 340 / 335
        """\+359885\d{6}""",       // Bulgaria mobile +359 885
        """\+385994\d{6}""",       // Croatia +385 99 4
    ).map { Rule(it) }
}
