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
            val store = Store(this)
            if (incoming && !number.isNullOrBlank()) {
                val rule = CallNumbers.firstMatch(this, store.rules(), number)
                store.setLastSeen("$number -> " + CallNumbers.candidates(this, number).joinToString(" / ") +
                    if (rule != null) " : BLOCKED" else " : allowed")
                if (rule != null) {
                    // Reject silently; the call still shows up in the phone's call log.
                    response.setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                    store.addLog(LogEntry(number, rule.pattern, System.currentTimeMillis()))
                }
            } else {
                store.setLastSeen(
                    "incoming=$incoming, number=" + (number ?: "hidden / none") + " : allowed",
                )
            }
        } catch (_: Exception) {
            // Never block a call because of a bug: fall through and allow it.
        }
        respondToCall(details, response.build())
    }
}
