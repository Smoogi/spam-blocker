package app.spamblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private val reported = listOf(
        "+38628884022", "+38618889909", "+38618888014", "+31205326320", "+38618888306",
        "+38618888462", "+35799498134", "+35799877886", "+393406032290", "+393357599148",
        "+359885688802", "+38631504633", "+385994033852",
    )

    @Test
    fun suggestedRulesCatchEveryReportedNumber() {
        for (n in reported) {
            assertNotNull("not blocked: $n", RuleMatcher.firstMatch(RuleMatcher.suggested, n))
        }
    }

    @Test
    fun ordinaryNumbersAreNotBlocked() {
        listOf("+38640123456", "+14155551234", "+393201234567", "+31201234567", "112").forEach {
            assertNull("wrongly blocked: $it", RuleMatcher.firstMatch(RuleMatcher.suggested, it))
        }
    }

    @Test
    fun formattingAndDoubleZeroPrefixAreNormalized() {
        assertEquals("+38618889909", RuleMatcher.normalize("+386 1 888-9909"))
        assertEquals("+38618889909", RuleMatcher.normalize("0038618889909"))
        assertEquals("0123456", RuleMatcher.normalize("(01) 23 456"))
        assertNotNull(RuleMatcher.firstMatch(RuleMatcher.suggested, "00386 1 888 9909"))
    }

    @Test
    fun disabledAndInvalidRulesAreIgnored() {
        val rules = listOf(Rule("""\+1.*""", enabled = false), Rule("(unclosed"))
        assertNull(RuleMatcher.firstMatch(rules, "+14155551234"))
        assertTrue(RuleMatcher.compile("(unclosed").isFailure)
    }

    @Test
    fun matchMustCoverWholeNumber() {
        assertNull(RuleMatcher.firstMatch(listOf(Rule("""\+386""")), "+38618889909"))
        assertNotNull(RuleMatcher.firstMatch(listOf(Rule("""\+386.*""")), "+38618889909"))
    }
}
