package dev.athena.creamkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

internal data class KAction(val name: String, val value: String = "")
private data class Cell(val label: String, val action: KAction? = null, val kind: String = "key")

internal class KeyboardView(
    context: Context,
    private val dispatch: (KAction) -> Unit
) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var english = false
    private var symbols = false
    private var secure = false
    private var caps = 0 // 0=off, 1=one-shot, 2=locked
    private var firstCapsTap: Runnable? = null
    private var longPress: Runnable? = null
    private var longFired = false
    private val gap = (1 * resources.displayMetrics.density).toInt()
    private val rowHeight = (42 * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setPadding(gap, gap, gap, gap)
        setBackgroundColor(Color.rgb(240, 239, 231))
        render()
    }

    fun update(english: Boolean = this.english, symbols: Boolean = this.symbols,
               secure: Boolean = this.secure, caps: Int = this.caps) {
        this.english = english
        this.symbols = symbols
        this.secure = secure
        this.caps = caps
        render()
    }

    private fun c(label: String, action: String? = null, value: String = "", kind: String = "key") =
        Cell(label, action?.let { KAction(it, value) }, kind)

    private fun blank() = Cell("", null, "blank")
    private fun render() {
        removeAllViews()
        val tool = listOf(
            c("Past", "PASTE", kind = "small"), c("UnDo", "UNDO", kind = "small"),
            c("ReDo", "REDO", kind = "small"), c("Select", "SELECT", kind = "small"),
            c("Tool", "TOOL", kind = "small"), c("Copy", "COPY", kind = "small"),
            c("←", "ARROW", "LEFT"), c("→", "ARROW", "RIGHT"),
            c("↑", "ARROW", "UP"), c("↓", "ARROW", "DOWN"), blank(), blank()
        )
        val words = if (secure) emptyList() else listOf("سلام", "خوب", "من", "برای", "این", "و", "که", "را", "به")
        val suggestion = words.map { c(it, "SUGGEST", it, "small") }.toMutableList()
        while (suggestion.size < 9) suggestion.add(blank())
        suggestion += c("File", "FILE", kind = "feature")
        suggestion += c("Send", "SEND", kind = "feature")
        suggestion += blank()

        val digits = if (symbols) listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
                     else listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰")
        val numberRow = digits.map { c(it, "INSERT", it, "number") }.toMutableList()
        numberRow += c("⌫", "DELETE", kind = "feature")
        numberRow += blank()

        val fa1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج")
        val fa2 = listOf("ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ")
        val fa3 = listOf("پ", "چ", "ژ", "ز", "ر", "ذ", "د", "ط", "ظ", "و")
        val en1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
        val en2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
        val en3 = listOf("Z", "X", "C", "V", "B", "N", "M")
        val first = (if (english) en1 else fa1).map { letterCell(it) }.toMutableList()
        while (first.size < 12) first.add(blank())
        val second = (if (english) en2 else fa2).map { letterCell(it) }.toMutableList()
        while (second.size < 12) second.add(blank())
        val third = mutableListOf(c(capsLabel(), "CAPS", kind = "feature"))
        third += (if (english) en3 else fa3).map { letterCell(it) }
        third += c("؟", "INSERT", "؟", "punct")
        while (third.size < 12) third.add(blank())

        val bottom = listOf(
            c("☺", "EMOJI", kind = "feature"), c("123", "SYMBOLS", kind = "small"),
            c(if (english) "EN" else "FA", "LANG", kind = "feature"),
            c("Space", "SPACE", kind = "small"), c("؟", "INSERT", "؟", "punct"),
            c(".", "INSERT", ".", "punct"), c("،", "INSERT", "،", "punct"),
            c("⌫", "DELETE", kind = "feature"), c("↵", "ENTER", kind = "feature"),
            blank(), blank(), blank()
        )
        listOf(tool, suggestion, numberRow, first, second, third, bottom).forEach { addRow(it) }
    }

    private fun letterCell(letter: String): Cell {
        val shown = if (english && caps > 0) letter.uppercase() else letter
        return c(shown, "LETTER", letter, "letter")
    }

    private fun capsLabel(): String = when (caps) {
        1 -> "Caps ↑"
        2 -> "Caps ●"
        else -> "Caps"
    }

    private fun addRow(cells: List<Cell>) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val rowParams = LayoutParams(LayoutParams.MATCH_PARENT, rowHeight)
        rowParams.setMargins(0, gap, 0, gap)
        addView(row, rowParams)
        cells.take(12).forEach { cell ->
            val view = TextView(context).apply {
                text = cell.label
                gravity = Gravity.CENTER
                includeFontPadding = true
                setTextColor(when (cell.kind) {
                    "punct" -> Color.rgb(143, 63, 47)
                    "letter" -> Color.rgb(36, 61, 79)
                    else -> Color.rgb(47, 57, 64)
                })
                textSize = when (cell.kind) {
                    "letter" -> 22f
                    "number" -> 16f
                    "feature" -> 10f
                    "small" -> 9f
                    else -> 12f
                }
                if (cell.kind == "feature") setTypeface(typeface, Typeface.BOLD)
                background = backgroundFor(cell.kind)
                isClickable = cell.action != null
                contentDescription = cell.label.ifBlank { "" }
            }
            val params = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            params.setMargins(gap, 0, gap, 0)
            row.addView(view, params)
            if (cell.action?.name == "CAPS") installCapsGestures(view)
            else view.setOnClickListener { cell.action?.let(dispatch) }
        }
    }

    private fun backgroundFor(kind: String) = GradientDrawable().apply {
        cornerRadius = 5f * resources.displayMetrics.density
        val fill = when (kind) {
            "blank" -> Color.TRANSPARENT
            "feature" -> Color.rgb(244, 242, 233)
            else -> Color.rgb(250, 249, 243)
        }
        setColor(fill)
        if (kind != "blank") setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(211, 208, 198))
    }

    private fun installCapsGestures(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longFired = false
                    longPress?.let(handler::removeCallbacks)
                    longPress = Runnable {
                        longFired = true
                        firstCapsTap?.let(handler::removeCallbacks)
                        firstCapsTap = null
                        dispatch(KAction("CAPS_LONG"))
                    }.also { handler.postDelayed(it, 520) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPress?.let(handler::removeCallbacks)
                    if (longFired) return@setOnTouchListener true
                    if (firstCapsTap != null) {
                        firstCapsTap?.let(handler::removeCallbacks)
                        firstCapsTap = null
                        dispatch(KAction("CAPS_DOUBLE"))
                    } else {
                        firstCapsTap = Runnable {
                            firstCapsTap = null
                            dispatch(KAction("CAPS_SINGLE"))
                        }.also { handler.postDelayed(it, 280) }
                    }
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPress?.let(handler::removeCallbacks)
                    true
                }
                else -> true
            }
        }
    }
}
