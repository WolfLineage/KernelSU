package com.vorthas.yusu.ui.screen.texteditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.min

/**
 * Native zoomable image view. A native View is used instead of Compose transform
 * gestures because it keeps pinch/pan responsive during fast scrolling and works
 * consistently with the terminal/editor touch handling on this device.
 */
class ZoomableImageView(context: Context) : ImageView(context) {
    private val matrix = Matrix()
    private var bitmap: Bitmap? = null
    private var baseScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val before = currentScale()
                val factor = detector.scaleFactor
                val next = (before * factor).coerceIn(baseScale * 0.5f, baseScale * 12f)
                matrix.postScale(next / before, next / before, detector.focusX, detector.focusY)
                imageMatrix = matrix
                return true
            }
        })

    private val panDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (!scaleDetector.isInProgress) {
                    matrix.postTranslate(-dx, -dy)
                    imageMatrix = matrix
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetToFit()
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix
    }

    fun setZoomBitmap(value: Bitmap?) {
        bitmap = value
        setImageBitmap(value)
        post { resetToFit() }
    }

    private fun currentScale(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun resetToFit() {
        val image = bitmap ?: return
        if (width <= 0 || height <= 0) return
        baseScale = min(width.toFloat() / image.width, height.toFloat() / image.height)
        matrix.reset()
        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(
            (width - image.width * baseScale) / 2f,
            (height - image.height * baseScale) / 2f,
        )
        imageMatrix = matrix
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetToFit()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        panDetector.onTouchEvent(event)
        // Consume all touch events: the viewer owns pinch/pan and must not let the
        // surrounding pager treat the gesture as a swipe.
        return true
    }
}
