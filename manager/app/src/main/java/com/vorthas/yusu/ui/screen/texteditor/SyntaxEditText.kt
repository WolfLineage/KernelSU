package com.vorthas.yusu.ui.screen.texteditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native editor with decoration-only shell syntax spans. Using a real EditText keeps
 * selection, IME, undo/redo and long-paste behaviour intact; the spans only recolour
 * ranges and are rebuilt incrementally off the main thread.
 */
class SyntaxEditText(context: Context) : EditText(context) {
    var onDocumentChange: ((String) -> Unit)? = null

    private var mutating = false
    private var highlightJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Span(color: Int) : ForegroundColorSpan(color)

    // Colours sampled from MT Manager's editor: keyword blue, string/assignment amber,
    // shebang and comments muted grey.
    private val colors = mapOf(
        ShellToken.KEY to Color.parseColor("#79B8FF"),
        ShellToken.TAG to Color.parseColor("#E8C07D"),
        ShellToken.VALUE to Color.parseColor("#E8C07D"),
        ShellToken.COMMENT to Color.parseColor("#6B7785"),
        ShellToken.TIMESTAMP to Color.parseColor("#6B7785"),
        ShellToken.ERROR to Color.parseColor("#FF8A80"),
        ShellToken.WARNING to Color.parseColor("#FFD180"),
    )

    init {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Color.parseColor("#B0BEC5"))
        setBackgroundColor(Color.TRANSPARENT)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        setPadding(12, 8, 12, 24)
        post { updateGutter() }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (mutating) return
                scheduleHighlight()
                onDocumentChange?.invoke(s.toString())
            }
        })
        scheduleHighlight()
    }

    // TextView.undo()/redo() are hidden from the compile SDK; call them reflectively so
    // the toolbar buttons have real behaviour on supported devices.
    private fun invokeEditAction(name: String) {
        runCatching {
            val method = android.widget.TextView::class.java.getMethod(name)
            method.isAccessible = true
            method.invoke(this)
        }
    }

    fun undoEdit() = invokeEditAction("undo")

    fun redoEdit() = invokeEditAction("redo")

    /** Insert text at the current selection (used by the MT-style symbol bar). */
    fun insertAtCursor(value: String) {
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        text?.replace(minOf(start, end), maxOf(start, end), value)
        setSelection((minOf(start, end) + value.length).coerceAtMost(text?.length ?: 0))
    }

    fun setDocument(value: String) {
        if (text?.toString() == value) return
        val position = selectionStart.coerceAtMost(value.length)
        mutating = true
        setText(value, BufferType.EDITABLE)
        setSelection(position)
        mutating = false
        scheduleHighlight()
    }

    private fun scheduleHighlight() {
        highlightJob?.cancel()
        val value = text?.toString().orEmpty()
        highlightJob = scope.launch {
            delay(90)
            val tokens = ShellSyntax.tokenize(value)
            withContext(Dispatchers.Main) {
                if (text?.toString() != value) return@withContext
                val editable = text ?: return@withContext
                editable.getSpans(0, editable.length, Span::class.java).forEach { editable.removeSpan(it) }
                tokens.forEach { token ->
                    val color = colors[token.kind] ?: return@forEach
                    if (token.start < token.end && token.end <= editable.length) {
                        editable.setSpan(Span(color), token.start, token.end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    /** MT-style line-number gutter drawn directly on the canvas so it tracks scrolling. */
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#3A4452")
        textSize = 26f
    }
    private var gutterWidth = 0

    private fun updateGutter() {
        val width = (gutterPaint.measureText("9999") + 24f).toInt()
        if (width != gutterWidth) {
            gutterWidth = width
            setPadding(width, paddingTop, paddingRight, paddingBottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        if (layout != null && layout.lineCount > 0) {
            val top = (scrollY - totalPaddingTop).coerceAtLeast(0)
            val first = layout.getLineForVertical(top).coerceAtLeast(0)
            val last = layout.getLineForVertical(scrollY + height).coerceAtMost(layout.lineCount - 1)
            if (first <= last) {
                for (line in first..last) {
                    val baseline = layout.getLineBaseline(line) + totalPaddingTop
                    canvas.drawText((line + 1).toString(), scrollX + 8f, baseline.toFloat(), gutterPaint)
                }
            }
        }
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        highlightJob?.cancel()
        super.onDetachedFromWindow()
    }
}
