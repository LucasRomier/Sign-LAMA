@file:Suppress("unused", "PackageName")

package com.LucasRomier.LamaSign.Util

import android.graphics.*
import android.graphics.Paint.Align
import java.util.*

class BorderedText(interiorColor: Int,
                   exteriorColor: Int,
                   textSize: Float) {

    private var interiorPaint: Paint = Paint()
    private var exteriorPaint: Paint = Paint()

    private var textSize = 0f

    init {
        interiorPaint.textSize = textSize
        interiorPaint.color = interiorColor
        interiorPaint.style = Paint.Style.FILL
        interiorPaint.isAntiAlias = false
        interiorPaint.alpha = 255
        exteriorPaint.textSize = textSize
        exteriorPaint.color = exteriorColor
        exteriorPaint.style = Paint.Style.FILL_AND_STROKE
        exteriorPaint.strokeWidth = textSize / 8
        exteriorPaint.isAntiAlias = false
        exteriorPaint.alpha = 255
        this.textSize = textSize
    }

    constructor(textSize: Float) : this(Color.WHITE, Color.BLACK, textSize)

    fun setTypeface(typeface: Typeface?) {
        interiorPaint.typeface = typeface
        exteriorPaint.typeface = typeface
    }

    private fun drawText(canvas: Canvas, posX: Float, posY: Float, text: String?) {
        canvas.drawText(text!!, posX, posY, exteriorPaint)
        canvas.drawText(text, posX, posY, interiorPaint)
    }

    fun drawLines(canvas: Canvas, posX: Float, posY: Float, lines: Vector<String?>) {
        for ((lineNum, line) in lines.withIndex()) {
            drawText(canvas, posX, posY - getTextSize() * (lines.size - lineNum - 1), line)
        }
    }

    fun setInteriorColor(color: Int) {
        interiorPaint.color = color
    }

    fun setExteriorColor(color: Int) {
        exteriorPaint.color = color
    }

    private fun getTextSize(): Float {
        return textSize
    }

    fun setAlpha(alpha: Int) {
        interiorPaint.alpha = alpha
        exteriorPaint.alpha = alpha
    }

    fun getTextBounds(
        line: String?, index: Int, count: Int, lineBounds: Rect?
    ) {
        interiorPaint.getTextBounds(line, index, count, lineBounds)
    }

    fun setTextAlign(align: Align?) {
        interiorPaint.textAlign = align
        exteriorPaint.textAlign = align
    }

}