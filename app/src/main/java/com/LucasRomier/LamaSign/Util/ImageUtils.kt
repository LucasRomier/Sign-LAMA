@file:Suppress("unused", "PackageName", "FunctionName")

package com.LucasRomier.LamaSign.Util

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class ImageUtils {

    companion object
    {
        // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
        // are normalized to eight bits.
        private const val kMaxChannelValue = 262143

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

        /**
         * Saves a Bitmap object to disk for analysis.
         *
         * @param bitmap The bitmap to save.
         */
        fun SaveBitmap(bitmap: Bitmap) {
            SaveBitmap(bitmap, "preview.png")
        }

        /**
         * Saves a Bitmap object to disk for analysis.
         *
         * @param bitmap The bitmap to save.
         * @param filename The location to save the bitmap to.
         */
        private fun SaveBitmap(bitmap: Bitmap, filename: String) {
            val root = Environment.getExternalStorageDirectory().absolutePath + File.separator + "tensorflow"
            Log.i("Image utils", "Saving ${bitmap.width}x${bitmap.height} bitmap to $root.")
            val myDir = File(root)
            if (!myDir.mkdirs()) {
                Log.i("Image utils", "Make dir failed")
            }
            val file = File(myDir, filename)
            if (file.exists()) {
                file.delete()
            }
            try {
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 99, out)
                out.flush()
                out.close()
            } catch (e: Exception) {
                Log.e("Image utils", "Exception!", e)
            }
        }

        fun ConvertYUV420SPToARGB8888(input: ByteArray, width: Int, height: Int, output: IntArray) {
            val frameSize = width * height
            var j = 0
            var yp = 0
            while (j < height) {
                var uvp = frameSize + (j shr 1) * width
                var u = 0
                var v = 0
                var i = 0
                while (i < width) {
                    val y = 0xff and input[yp].toInt()
                    if (i and 1 == 0) {
                        v = 0xff and input[uvp++].toInt()
                        u = 0xff and input[uvp++].toInt()
                    }
                    output[yp] = YUV2RGB(y, u, v)
                    i++
                    yp++
                }
                j++
            }
        }

        private fun YUV2RGB(y: Int, u: Int, v: Int): Int {
            // Adjust and check YUV values
            //var yy = if (y - 16 < 0) 0 else y - 16
            var uu = u
            var vv = v
            uu -= 128
            vv -= 128

            // This is the floating point equivalent. We do the conversion in integer
            // because some Android devices do not have floating point in hardware.
            // nR = (int)(1.164 * nY + 2.018 * nU);
            // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
            // nB = (int)(1.164 * nY + 1.596 * nV);
            val y1192 = 1192 * y
            var r = y1192 + 1634 * v
            var g = y1192 - 833 * v - 400 * u
            var b = y1192 + 2066 * u

            // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
            r = if (r > kMaxChannelValue) kMaxChannelValue else if (r < 0) 0 else r
            g = if (g > kMaxChannelValue) kMaxChannelValue else if (g < 0) 0 else g
            b = if (b > kMaxChannelValue) kMaxChannelValue else if (b < 0) 0 else b
            return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
        }

        fun ConvertYUV420ToARGB8888(
            yData: ByteArray,
            uData: ByteArray,
            vData: ByteArray,
            width: Int,
            height: Int,
            yRowStride: Int,
            uvRowStride: Int,
            uvPixelStride: Int,
            out: IntArray
        ) {
            var yp = 0
            for (j in 0 until height) {
                val pY = yRowStride * j
                val pUV = uvRowStride * (j shr 1)
                for (i in 0 until width) {
                    val uvOffset = pUV + (i shr 1) * uvPixelStride
                    out[yp++] = YUV2RGB(
                        0xff and yData[pY + i].toInt(), 0xff and uData[uvOffset].toInt(), 0xff and vData[uvOffset].toInt()
                    )
                }
            }
        }
    }

}