@file:Suppress("unused", "PackageName", "FunctionName", "Deprecation")

package com.LucasRomier.LamaSign.Util

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface

import androidx.annotation.IntDef
import androidx.renderscript.Allocation
import androidx.renderscript.Element
import androidx.renderscript.RenderScript
import androidx.renderscript.Type

import com.LucasRomier.LamaSign.ScriptC_yuv2rgb_complex

class YuvConverter: AutoCloseable {

    private var rs: RenderScript? = null
    private var script: ScriptC_yuv2rgb_complex? = null
    private var bmp: Bitmap? = null

    private var init = false

    fun YuvConverter(ctx: Context?, ySize: Int, uvSize: Int, width: Int, height: Int) {
        rs = RenderScript.create(ctx)
        script = ScriptC_yuv2rgb_complex(rs)
        init(ySize, uvSize, width, height)
    }

    private var allocY: Allocation? = null
    private  var allocU: Allocation? = null
    private  var allocV:Allocation? = null
    private  var allocOut:Allocation? = null

    override fun close() {
        if (allocY != null) allocY!!.destroy()
        if (allocU != null) allocU!!.destroy()
        if (allocV != null) allocV!!.destroy()
        if (allocOut != null) allocOut!!.destroy()
        bmp = null
        allocY = null
        allocU = null
        allocV = null
        allocOut = null
        script!!.destroy()
        script = null
        rs = null
    }

    fun init(ySize: Int, uvSize: Int, width: Int, height: Int) {
        if (init) return
        init = true

        if (bmp == null || bmp!!.width != width || bmp!!.height != height) {
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (allocOut != null) allocOut!!.destroy()
            allocOut = null
        }
        if (allocY == null || allocY!!.bytesSize !== ySize) {
            if (allocY != null) allocY!!.destroy()
            val yBuilder: Type.Builder = Type.Builder(rs, Element.U8(rs)).setX(ySize)
            allocY = Allocation.createTyped(rs, yBuilder.create(), Allocation.USAGE_SCRIPT)
        }
        if (allocU == null || allocU!!.bytesSize !== uvSize || allocV == null || allocV!!.bytesSize !== uvSize) {
            if (allocU != null) allocU!!.destroy()
            if (allocV != null) allocV!!.destroy()
            val uvBuilder: Type.Builder = Type.Builder(rs, Element.U8(rs)).setX(uvSize)
            allocU = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT)
            allocV = Allocation.createTyped(rs, uvBuilder.create(), Allocation.USAGE_SCRIPT)
        }
        if (allocOut == null || allocOut!!.bytesSize !== width * height * 4) {
            val rgbType: Type = Type.createXY(rs, Element.RGBA_8888(rs), width, height)
            if (allocOut != null) allocOut!!.destroy()
            allocOut = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT)
        }
    }

    @Retention(AnnotationRetention.SOURCE) // Enumerate valid values for this interface
    @IntDef(
        Surface.ROTATION_0,
        Surface.ROTATION_90,
        Surface.ROTATION_180,
        Surface.ROTATION_270
    ) // Create an interface for validating int types
    annotation class Rotation

    /**
     * Converts an YUV_420 image into Bitmap.
     * @param yPlane  byte[] of Y, with pixel stride 1
     * @param uPlane  byte[] of U, with pixel stride 2
     * @param vPlane  byte[] of V, with pixel stride 2
     * @param yLine   line stride of Y
     * @param uvLine  line stride of U and V
     * @param width   width of the output image (note that it is swapped with height for portrait rotation)
     * @param height  height of the output image
     * @param rotation  rotation to apply. ROTATION_90 is for portrait back-facing camera.
     * @return RGBA_8888 Bitmap image.
     */
    fun YUV420toRGB(
        yPlane: ByteArray, uPlane: ByteArray, vPlane: ByteArray?,
        yLine: Int, uvLine: Int, width: Int, height: Int,
        @Rotation rotation: Int
    ): Bitmap? {
        init(yPlane.size, uPlane.size, width, height)
        allocY!!.copyFrom(yPlane)
        allocU!!.copyFrom(uPlane)
        allocV!!.copyFrom(vPlane)
        script!!._Width = width.toLong()
        script!!._Height = height.toLong()
        script!!._Yline = yLine.toLong()
        script!!._UVline = uvLine.toLong()
        script!!._Yplane = allocY
        script!!._Uplane = allocU
        script!!._Vplane = allocV
        when (rotation) {
            Surface.ROTATION_0 -> script!!.forEach_YUV420toRGB(allocOut)
            Surface.ROTATION_90 -> script!!.forEach_YUV420toRGB_90(allocOut)
            Surface.ROTATION_180 -> script!!.forEach_YUV420toRGB_180(allocOut)
            Surface.ROTATION_270 -> script!!.forEach_YUV420toRGB_270(allocOut)
        }
        allocOut!!.copyTo(bmp)
        return bmp
    }

}