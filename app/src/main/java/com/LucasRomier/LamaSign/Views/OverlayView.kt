@file:Suppress("unused", "PackageName")

package com.LucasRomier.LamaSign.Views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.util.*

class OverlayView(context: Context, attrs: AttributeSet?, defStyle: Int) : View(context, attrs, defStyle) {

    private val callbacks: LinkedList<DrawCallback> = LinkedList()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

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

}