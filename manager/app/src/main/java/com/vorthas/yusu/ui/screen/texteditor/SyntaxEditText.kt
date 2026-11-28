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
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.widget.EditText
import android.widget.OverScroller
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

    /** MT-style line-number gutter drawn directly on the canvas so it tracks scrolling. */
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#6E7681")
        textSize = 26f
    }
    private var gutterWidth = 0
    private var textSizeSp = 13f
    private var pinchActive = false
    private var language = SourceSyntax.Lang.PLAIN
    private var logicalStarts = intArrayOf(0)

    // TextView drags with the finger but does not fling when horizontally scrolling is
    // enabled. Track the release velocity and continue the gesture with an OverScroller
    // so fast swipes keep their momentum instead of stopping dead on ACTION_UP.
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val flingTick = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) return
            scrollTo(scroller.currX, scroller.currY)
            postOnAnimation(this)
        }
    }

    /** Two-finger pinch changes the text size without losing selection or scroll state. */
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (textSizeSp * detector.scaleFactor).coerceIn(8f, 40f)
            if (kotlin.math.abs(next - textSizeSp) < 0.01f) return true
            textSizeSp = next
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            gutterPaint.textSize = textSizeSp * 0.72f * resources.displayMetrics.scaledDensity
            updateGutter()
            return true
        }
    })

    init {
        typeface = Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.START
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(Color.parseColor("#C8C8C8"))
        setBackgroundColor(Color.TRANSPARENT)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // Long logical lines wrap into several visual rows (MT behaviour). The gutter
        // maps each visual row back to its logical line, so wrapped rows do not add
        // line numbers.
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        setPadding(12, 8, 12, 24)
        gutterPaint.textSize = textSizeSp * 0.72f * resources.displayMetrics.scaledDensity
        post { updateGutter() }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (mutating) return
                updateLogicalStarts(s.toString())
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

    fun setLanguage(path: String) {
        val next = SourceSyntax.langFor(path)
        if (next == language) return
        language = next
        scheduleHighlight()
    }

    fun setDocument(value: String) {
        if (text?.toString() == value) return
        val position = selectionStart.coerceAtMost(value.length)
        mutating = true
        setText(value, BufferType.EDITABLE)
        setSelection(position)
        mutating = false
        updateLogicalStarts(value)
        scheduleHighlight()
    }

    private fun updateLogicalStarts(value: String) {
        val starts = ArrayList<Int>(16).apply { add(0) }
        for (i in value.indices) if (value[i] == '\n') starts.add(i + 1)
        logicalStarts = starts.toIntArray()
    }

    /** Largest logical-line start that is <= the visual row's first character offset. */
    private fun logicalLineFor(offset: Int): Int {
        var low = 0
        var high = logicalStarts.lastIndex
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (logicalStarts[mid] <= offset) { answer = mid; low = mid + 1 } else high = mid - 1
        }
        return answer
    }

    private fun scheduleHighlight() {
        highlightJob?.cancel()
        val value = text?.toString().orEmpty()
        val syntax = language
        highlightJob = scope.launch {
            delay(90)
            val tokens = SourceSyntax.tokenize(value, syntax)
            withContext(Dispatchers.Main) {
                if (text?.toString() != value || language != syntax) return@withContext
                val editable = text ?: return@withContext
                editable.getSpans(0, editable.length, Span::class.java).forEach { editable.removeSpan(it) }
                tokens.forEach { token ->
                    val color = SourceSyntax.color(token.kind)
                    if (token.start < token.end && token.end <= editable.length) {
                        editable.setSpan(Span(color), token.start, token.end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    private fun updateGutter() {
        val width = (gutterPaint.measureText("9999") + 24f).toInt()
        if (width != gutterWidth) {
            gutterWidth = width
            setPadding(width, paddingTop, paddingRight, paddingBottom)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            pinchActive = false
            scroller.abortAnimation()
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        if (event.pointerCount == 1) velocityTracker?.addMovement(event)

        if (pinchActive) {
            // The native EditText only saw the first DOWN. Send it a CANCEL when the
            // gesture ends so its touch/selection state is cleared; otherwise the next
            // single-finger drag is interpreted as a text selection and fling is lost.
            if (event.pointerCount <= 1 && (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_CANCEL)) {
                val cancel = MotionEvent.obtain(event).apply { this.action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                pinchActive = false
                releaseVelocityTracker()
            }
            return true
        }
        if (event.pointerCount > 1) {
            pinchActive = true
            releaseVelocityTracker()
            return true
        }

        val handled = super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP) {
            velocityTracker?.computeCurrentVelocity(1000)
            val velocityY = velocityTracker?.yVelocity?.toInt() ?: 0
            if (kotlin.math.abs(velocityY) > 400) startFling(velocityY)
            releaseVelocityTracker()
        } else if (action == MotionEvent.ACTION_CANCEL) {
            releaseVelocityTracker()
        }
        return handled
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun startFling(velocityY: Int) {
        val maxY = maxOf(0, computeVerticalScrollRange() - height)
        val maxX = maxOf(0, computeHorizontalScrollRange() - width)
        scroller.fling(scrollX, scrollY, 0, -velocityY, 0, maxX, 0, maxY, 0, 0)
        postOnAnimation(flingTick)
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
                    val rowStart = layout.getLineStart(line)
                    val logical = logicalLineFor(rowStart)
                    // Only the first visual row of a logical line gets a number; wrapped
                    // continuation rows stay blank just like MT Manager.
                    if (logicalStarts[logical] == rowStart) {
                        canvas.drawText((logical + 1).toString(), scrollX + 8f, baseline.toFloat(), gutterPaint)
                    }
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
