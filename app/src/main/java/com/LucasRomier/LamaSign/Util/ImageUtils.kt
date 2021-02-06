@file:Suppress("unused", "PackageName", "FunctionName", "Deprecation")

package com.LucasRomier.LamaSign.Util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.renderscript.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ImageUtils {

    companion object
    {
        // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
        // are normalized to eight bits.
        private const val kMaxChannelValue = 262143

        fun YUV_toRGB(context: Context, yuv: ByteArray?, W: Int, H: Int): Bitmap? {
            val rs = RenderScript.create(context)

            val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

            val yuvType: Type.Builder = Type.Builder(rs, Element.U8(rs)).setX(yuv!!.size)
            val allocIn = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)

            val rgbaType: Type.Builder = Type.Builder(rs, Element.RGBA_8888(rs)).setX(W).setY(H)
            val allocOut = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)

            allocIn.copyFrom(yuv)

            yuvToRgbIntrinsic.setInput(allocIn)
            yuvToRgbIntrinsic.forEach(allocOut)

            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            allocOut.copyTo(bmp)

            allocIn.destroy()
            allocOut.destroy()

            return bmp
        }

        fun SaveBitmapRGB(context: Context, rgb: Bitmap, width: Int, height: Int, crop: Boolean) {
            val out = PrepareSave(context)

            val btm = if (crop) {
                val cropSize = width.coerceAtMost(height)
                val area = Rect(
                    (width - cropSize) / 2,
                    (height - cropSize) / 2,
                    (width + cropSize) / 2,
                    (height + cropSize) / 2
                )

                Bitmap.createBitmap(rgb, area.left, area.top, area.width(), area.height())
            } else {
                rgb
            }
            btm.compress(Bitmap.CompressFormat.JPEG, 100, out.first);

            SaveInternal(context, out.second, out.first, crop)
        }

        fun SaveYUV(context: Context, yuv: ByteArray?, format: Int, width: Int, height: Int, crop: Boolean) {
            val out = PrepareSave(context)

            val image = YuvImage(yuv, format, width, height, null)

            val area = if (!crop) {
                Rect(0, 0, image.width, image.height)
            } else {
                val cropSize = image.width.coerceAtMost(image.height)
                Rect(
                    (image.width - cropSize) / 2,
                    (image.height - cropSize) / 2,
                    (image.width + cropSize) / 2,
                    (image.height + cropSize) / 2
                )
            }

            image.compressToJpeg(area, 100, out.first)

            SaveInternal(context, out.second, out.first, crop)
        }

        private fun PrepareSave(context: Context) : Pair<FileOutputStream, File> {
            val root = "${context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.absolutePath}${File.separator}LAMA"
            val path = File(root)
            path.mkdirs()

            val name = String.format("%s_%d.jpg", "Capture", System.currentTimeMillis())
            val file = File(path, name)

            return Pair(FileOutputStream(file), file)
        }

        private fun SaveInternal(context: Context, file: File, out: FileOutputStream, crop: Boolean) {
            out.flush()
            out.close()

            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "LAMA Capture")
            values.put(MediaStore.Images.Media.DESCRIPTION, "Capture during runtime")
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            values.put(MediaStore.Images.ImageColumns.BUCKET_ID, file.toString().toLowerCase(Locale.US).hashCode())
            values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, file.name.toLowerCase(Locale.US))
            values.put("_data", file.absolutePath)
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            Toast.makeText(context, "Capture saved to Gallery${if (crop) " (cropped)" else ""}", Toast.LENGTH_LONG).show()
        }

        /**
         * Utility method to compute the allocated size in bytes of a YUV420SP image of the given
         * dimensions.
         */
        fun GetYUVByteSize(width: Int, height: Int): Int {
            // The luminance plane requires 1 byte per pixel.
            val ySize = width * height

            // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
            // Each 2x2 block takes 2 bytes to encode, one each for U and V.
            val uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2
            return ySize + uvSize
        }


    }

}