package app.spamblocker

import android.app.Activity
import android.app.role.RoleManager
import android.content.res.Configuration
import android.graphics.Insets
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Date

/** One simple screen. All decisions live in RuleMatcher; this only shows and edits state. */
class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var roles: RoleManager

    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var lastSeen: TextView
    private lateinit var rulesBox: LinearLayout
    private lateinit var addInput: EditText
    private lateinit var addError: TextView
    private lateinit var testResult: TextView
    private lateinit var testInput: EditText
    private lateinit var logBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        roles = getSystemService(RoleManager::class.java)
        setContentView(buildUi())
        fixBarIcons()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Plain Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refresh()
    }

    // ---- UI construction -------------------------------------------------

    private fun buildUi(): View {
        val root = column().apply { setPadding(dp(16), dp(24), dp(16), dp(24)) }

        root.addView(text("Spam Blocker", 24f, bold = true))

        status = text("", 15f).apply { setPadding(0, dp(8), 0, dp(8)) }
        root.addView(status)
        enableButton = Button(this).apply {
            text = "Turn on blocking"
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 1)
            }
        }
        root.addView(enableButton)
        lastSeen = text("", 12f).apply { typeface = Typeface.MONOSPACE; setPadding(0, dp(8), 0, 0) }
        root.addView(lastSeen)

        root.addView(heading("Rules"))
        root.addView(text("A rule is a regex that must match the whole number, written like +38618889909 " +
            "(spaces and dashes are removed first). Use .* for prefixes, e.g. \\+35799.*", 13f))
        rulesBox = column()
        root.addView(rulesBox)

        addInput = EditText(this).apply {
            hint = "New regex, e.g. \\+386[12]888\\d{4}"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            typeface = Typeface.MONOSPACE
            setSingleLine()
        }
        addError = text("", 13f).apply { setTextColor(0xFFD32F2F.toInt()); visibility = View.GONE }
        val addButton = Button(this).apply {
            text = "Add rule"
            setOnClickListener { addRule() }
        }
        root.addView(addInput)
        root.addView(addError)
        root.addView(addButton)

        root.addView(heading("Test a number"))
        testInput = EditText(this).apply {
            hint = "+38618889909"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = updateTest()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        testResult = text("", 14f)
        root.addView(testInput)
        root.addView(testResult)

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(24), 0, dp(4))
        }
        logHeader.addView(text("Blocked calls", 18f, bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        logHeader.addView(Button(this).apply {
            text = "Clear"
            setOnClickListener { store.clearLog(); refresh() }
        })
        root.addView(logHeader)
        logBox = column()
        root.addView(logBox)

        return ScrollView(this).apply {
            addView(root)
            clipToPadding = false
            // Android 15+ draws edge-to-edge: keep content clear of the status bar,
            // navigation bar and the keyboard, so fields stay visible while typing.
            setOnApplyWindowInsetsListener { v, insets ->
                val pad = barInsets(insets)
                v.setPadding(pad.left, pad.top, pad.right, pad.bottom)
                WindowInsets.CONSUMED
            }
        }
    }

    private fun barInsets(insets: WindowInsets): Insets =
        if (Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        } else {
            @Suppress("DEPRECATION")
            Insets.of(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
            )
        }

    /** Dark status/navigation icons on the light theme (they came out white on white). */
    private fun fixBarIcons() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        if (night || Build.VERSION.SDK_INT < 30) return
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(light, light)
    }

    // ---- State -> views --------------------------------------------------

    private fun refresh() {
        val held = roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        status.text = if (held) "Blocking is ON. Matching calls are rejected silently."
        else "Blocking is OFF. Allow this app as your call screening app."
        enableButton.visibility = if (held) View.GONE else View.VISIBLE
        lastSeen.text = "Last call seen by the app:\n" + (store.lastSeen()
            ?: "none yet. If a call comes in and this stays empty, blocking is not switched on.")

        rulesBox.removeAllViews()
        store.rules().forEachIndexed { index, rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(Switch(this).apply {
                text = rule.pattern
                typeface = Typeface.MONOSPACE
                isChecked = rule.enabled
                setOnCheckedChangeListener { _, on -> updateRules { it[index] = rule.copy(enabled = on) } }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener { updateRules { it.removeAt(index) }; refresh() }
            })
            rulesBox.addView(row)
        }

        logBox.removeAllViews()
        val log = store.log()
        if (log.isEmpty()) logBox.addView(text("Nothing blocked yet.", 14f))
        log.forEach {
            val whenText = DateFormat.format("d MMM, HH:mm", Date(it.timeMillis))
            logBox.addView(text("${it.number}  ·  $whenText", 15f).apply { setPadding(0, dp(6), 0, 0) })
            logBox.addView(text("rule ${it.pattern}", 12f).apply { typeface = Typeface.MONOSPACE })
        }
        updateTest()
    }

    private fun updateRules(change: (MutableList<Rule>) -> Unit) {
        store.saveRules(store.rules().toMutableList().also(change))
        updateTest()
    }

    private fun addRule() {
        val pattern = addInput.text.toString().trim()
        val error = when {
            pattern.isEmpty() -> "Type a regex first."
            RuleMatcher.compile(pattern).isFailure -> "Not a valid regex."
            store.rules().any { it.pattern == pattern } -> "That rule already exists."
            else -> null
        }
        addError.text = error ?: ""
        addError.visibility = if (error == null) View.GONE else View.VISIBLE
        if (error != null) return
        store.saveRules(store.rules() + Rule(pattern))
        addInput.text.clear()
        refresh()
    }

    private fun updateTest() {
        val number = testInput.text.toString()
        testResult.text = when {
            RuleMatcher.normalize(number).isEmpty() -> ""
            else -> CallNumbers.firstMatch(this, store.rules(), number)
                ?.let { "Would be BLOCKED by ${it.pattern}" }
                ?: "Would be allowed."
        }
    }

    // ---- Small view helpers ----------------------------------------------

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun heading(s: String) = text(s, 18f, bold = true).apply { setPadding(0, dp(24), 0, dp(4)) }

    private fun text(s: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sp
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
