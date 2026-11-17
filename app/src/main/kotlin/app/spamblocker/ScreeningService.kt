package app.spamblocker

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService

/** Called by Android for every incoming call once this app holds the call screening role. */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val response = CallResponse.Builder()
        try {
            val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
            val number = details.handle?.schemeSpecificPart?.let { Uri.decode(it) }
            if (incoming && !number.isNullOrBlank()) {
                val store = Store(this)
                val rule = RuleMatcher.firstMatch(store.rules(), number)
                if (rule != null) {
                    // Reject silently; the call still shows up in the phone's call log.
                    response.setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                    store.addLog(LogEntry(number, rule.pattern, System.currentTimeMillis()))
                }
            }
        } catch (_: Exception) {
            // Never block a call because of a bug: fall through and allow it.
        }
        respondToCall(details, response.build())
    }
}
