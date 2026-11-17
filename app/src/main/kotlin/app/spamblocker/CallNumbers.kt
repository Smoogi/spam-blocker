package app.spamblocker

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/**
 * Android may deliver a caller as "+38641123456" or in national form ("041 123 456").
 * Rules are written with "+", so we also try the international form of a national number,
 * using the country of the SIM / network.
 */
object CallNumbers {

    fun candidates(context: Context, raw: String): List<String> {
        val plain = RuleMatcher.normalize(raw)
        if (plain.isEmpty()) return emptyList()
        val result = mutableListOf(plain)
        if (!plain.startsWith("+")) {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val countries = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso)
                .filter { it.isNotBlank() }
                .map { it.uppercase() }
                .distinct()
            for (iso in countries) {
                PhoneNumberUtils.formatNumberToE164(plain, iso)?.let { if (it !in result) result += it }
            }
        }
        return result
    }

    /** First enabled rule matching any form of the number. */
    fun firstMatch(context: Context, rules: List<Rule>, raw: String): Rule? =
        candidates(context, raw).firstNotNullOfOrNull { RuleMatcher.firstMatch(rules, it) }
}
