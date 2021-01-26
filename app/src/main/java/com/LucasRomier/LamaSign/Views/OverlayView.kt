@file:Suppress("unused", "PackageName", "Deprecation")

package com.LucasRomier.LamaSign.Views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import com.LucasRomier.LamaSign.Classification.Recognition
import java.util.*


class OverlayView(context: Context, attrs: AttributeSet?, defStyle: Int) : View(context, attrs, defStyle) {

    companion object
    {
        private const val INPUT_SIZE = 300
    }

    private val callbacks: LinkedList<DrawCallback> = LinkedList()
    private var paint: Paint = Paint()
    private var results: List<Recognition?>? = null
    private val colors: List<Int>? = null
    private var resultsViewHeight: Float

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics)
        resultsViewHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 112f, resources.displayMetrics)
    }

    fun addCallback(callback: DrawCallback) {
        callbacks.add(callback)
    }

    @Synchronized
    override fun draw(canvas: Canvas?) {
        super.draw(canvas)

        for (callback in callbacks) {
            callback.drawCallback(canvas)
        }
    }

    /** Interface defining the callback for client classes.  */
    interface DrawCallback {
        fun drawCallback(canvas: Canvas?)
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        for (callback in callbacks) {
            callback.drawCallback(canvas)
        }
        if (results != null) {
            for (i in results!!.indices) {
                if (results!![i] != null) {
                    if (results!![i]!!.confidence!! > 0.3) {
                        Log.d("Overlay view", "Result #$i: ${results!![i]}")
                        val box = reCalcSize(results!![i]!!.getLocation())
                        val title = results!![i]!!.title + String.format(" %2.2f", results!![i]!!.confidence!! * 100) + "%"
                        paint.color = Color.RED
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(box, paint)
                        paint.strokeWidth = 2.0f
                        paint.style = Paint.Style.FILL_AND_STROKE
                        canvas.drawText(title, box.left, box.top, paint)
                    }
                }
            }
        }
    }

    fun setResults(results: List<Recognition?>?) {
        this.results = results
        postInvalidate()
    }

    private fun reCalcSize(rect: RectF): RectF {
        val padding = 5
        val overlayViewHeight = height - resultsViewHeight
        val sizeMultiplier = (width.toFloat() / INPUT_SIZE.toFloat()).coerceAtMost(overlayViewHeight / INPUT_SIZE.toFloat())
        val offsetX: Float = (width - INPUT_SIZE * sizeMultiplier) / 2
        val offsetY: Float = (overlayViewHeight - INPUT_SIZE * sizeMultiplier) / 2 + resultsViewHeight
        val left = padding.toFloat().coerceAtLeast(sizeMultiplier * rect.left + offsetX)
        val top = (offsetY + padding).coerceAtLeast(sizeMultiplier * rect.top + offsetY)
        val right = (rect.right * sizeMultiplier).coerceAtMost((width - padding).toFloat())
        val bottom = (rect.bottom * sizeMultiplier + offsetY).coerceAtMost((height - padding).toFloat())
        return RectF(left, top, right, bottom)
    }

}