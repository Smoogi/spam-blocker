package app.spamblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private object DateFormatter {
    fun now(): String = java.text.SimpleDateFormat("d MMM HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date())
}

/** Rules and the blocked-call log, kept in private SharedPreferences as JSON. */
class Store(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("spam_blocker", Context.MODE_PRIVATE)

    fun rules(): List<Rule> = synchronized(lock) {
        val json = prefs.getString(KEY_RULES, null)
            ?: return RuleMatcher.suggested.also { saveRulesLocked(it) }
        val arr = JSONArray(json)
        List(arr.length()) {
            val o = arr.getJSONObject(it)
            Rule(o.getString("p"), o.optBoolean("e", true))
        }
    }

    fun saveRules(rules: List<Rule>) = synchronized(lock) { saveRulesLocked(rules) }

    fun log(): List<LogEntry> = synchronized(lock) {
        val arr = JSONArray(prefs.getString(KEY_LOG, "[]"))
        List(arr.length()) {
            val o = arr.getJSONObject(it)
            LogEntry(o.getString("n"), o.getString("p"), o.getLong("t"))
        }
    }

    fun addLog(entry: LogEntry) = synchronized(lock) {
        val kept = (listOf(entry) + log()).take(MAX_LOG)
        val arr = JSONArray()
        kept.forEach {
            arr.put(JSONObject().put("n", it.number).put("p", it.pattern).put("t", it.timeMillis))
        }
        prefs.edit().putString(KEY_LOG, arr.toString()).apply()
    }

    /** Diagnostic: what the last screened call looked like and what we did. */
    fun lastSeen(): String? = prefs.getString(KEY_LAST, null)

    fun setLastSeen(text: String) {
        prefs.edit().putString(KEY_LAST, DateFormatter.now() + "  " + text).apply()
    }

    fun clearLog() = synchronized(lock) { prefs.edit().remove(KEY_LOG).apply() }

    private fun saveRulesLocked(rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(JSONObject().put("p", it.pattern).put("e", it.enabled)) }
        prefs.edit().putString(KEY_RULES, arr.toString()).apply()
    }

    private companion object {
        val lock = Any()
        const val KEY_RULES = "rules"
        const val KEY_LOG = "log"
        const val KEY_LAST = "last_seen"
        const val MAX_LOG = 200
    }
}
