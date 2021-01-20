@file:Suppress("unused", "PackageName")

package com.LucasRomier.LamaSign.Views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.LucasRomier.LamaSign.Classification.Recognition

class RecognitionScoreView(context: Context, attrs: AttributeSet?, defStyle: Int) : View(context, attrs, defStyle), ResultsView {

    companion object
    {
        private const val TEXT_SIZE_DIP = 16f
    }

    private var textSizePx = 0f
    private var fgPaint: Paint? = null
    private var bgPaint: Paint? = null
    private var results: List<Recognition?>? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        fgPaint = Paint()
        fgPaint!!.textSize = textSizePx

        bgPaint = Paint()
        bgPaint!!.color = -0x33bd7a0c
    }

    override fun setResults(results: List<Recognition?>?) {
        this.results = results
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val x = 10
        var y = (fgPaint!!.textSize * 1.5f).toInt()
        canvas.drawPaint(bgPaint!!)
        if (results != null) {
            for (recognition in results!!) {
                canvas.drawText(
                    recognition!!.title.toString() + ": " + recognition.confidence,
                    x.toFloat(),
                    y.toFloat(),
                    fgPaint!!
                )
                y += (fgPaint!!.textSize * 1.5f).toInt()
            }
        }
    }

}