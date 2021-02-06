@file:Suppress("unused", "PackageName", "FunctionName", "Deprecation")

package com.LucasRomier.LamaSign.Activities

import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.ImageReader.OnImageAvailableListener
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.widget.Toast
import com.LucasRomier.LamaSign.Classification.*
import com.LucasRomier.LamaSign.R
import com.LucasRomier.LamaSign.Util.BorderedText
import com.LucasRomier.LamaSign.Util.ImageUtils
import java.io.IOException


class ClassifierActivity : CameraActivity() {

    companion object
    {
        private val DESIRED_PREVIEW_SIZE = Size(640, 480)
        private const val TEXT_SIZE_DIP = 10f
    }

    private var rgbFrameBitmap: Bitmap? = null
    private var lastProcessingTimeMs: Long = 0
    private var sensorOrientation: Int? = null
    //private var classifier: ClassifierLib? = null
    private var classifier: ClassifierTask? = null
    private var borderedText: BorderedText? = null

    /** Input image size of the model along x axis.  */
    private var imageSizeX = 0

    /** Input image size of the model along y axis.  */
    private var imageSizeY = 0

    override fun getLayoutId(): Int {
        return R.layout.camera_connection_fragment
    }

    override fun getDesiredPreviewFrameSize(): Size {
        return DESIRED_PREVIEW_SIZE
    }

    override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = BorderedText(textSizePx)
        borderedText!!.setTypeface(Typeface.MONOSPACE)
        recreateClassifier(getModel(), getDevice(), getNumThreads())
        if (classifier == null) {
            Log.e("Classifier Activity", "No classifier on preview!")
            return
        }
        previewWidth = size!!.width
        previewHeight = size.height
        sensorOrientation = rotation - getScreenOrientation()
        Log.i("Classifier Activity", "Camera orientation relative to screen canvas: $sensorOrientation")
        Log.i("Classifier Activity", "Initializing at size ${previewWidth}x${previewHeight}")
    }

    override fun processImage() {
        rgbFrameBitmap = ImageUtils.YUV_toRGB(this, getImagePreviewBytes(), previewWidth, previewHeight)

        val cropSize = previewWidth.coerceAtMost(previewHeight)
        runInBackground {
            if (classifier != null) {
                val startTime = SystemClock.uptimeMillis()
                val results: List<Recognition?> = classifier!!.recognizeImage(rgbFrameBitmap!!, sensorOrientation!!)

                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                Log.v("Classifier Activity", "Detect $results")

                runOnUiThread {
                    showResultsInBottomSheet(results)
                    showFrameInfo(previewWidth.toString() + "x" + previewHeight)
                    showCropInfo(imageSizeX.toString() + "x" + imageSizeY)
                    showCameraResolution(cropSize.toString() + "x" + cropSize)
                    showRotationInfo(sensorOrientation.toString())
                    showInference(lastProcessingTimeMs.toString() + "ms")
                }

                requestRender(results)
            }
            readyForNextImage()
        }
    }

    override fun onInferenceConfigurationChanged() {
        if (rgbFrameBitmap == null) {
            // Defer creation until we're getting camera frames.
            return
        }
        val device: Device? = getDevice()
        val model: Model? = getModel()
        val numThreads = getNumThreads()
        runInBackground { recreateClassifier(model, device, numThreads) }
    }

    override fun cameraCapture(crop: Boolean) {
        if (rgbFrameBitmap != null) {
            //ImageUtils.SaveYUV(this, getImagePreviewBytes(), getRawParameters()!!.previewFormat, previewWidth, previewHeight, crop)
            ImageUtils.SaveBitmapRGB(this, rgbFrameBitmap!!, previewWidth, previewHeight, crop)
        }
    }

    private fun recreateClassifier(model: Model?, device: Device?, numThreads: Int) {
        if (classifier != null) {
            Log.d("Classifier Activity", "Closing classifier.")
            classifier!!.close()
            classifier = null
        }
        classifier = try {
            Log.d("Classifier Activity", "Creating classifier (model=$model, device=$device, numThreads=$numThreads)")
            //ClassifierLib(this, device!!, numThreads, model!!)
            ClassifierTask(this, device!!, numThreads, model!!)
        } catch (e: IOException) {
            Log.e("Classifier Activity", "Failed to create classifier.")
            runOnUiThread { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            return
        } catch (e: IllegalArgumentException) {
            Log.e("Classifier Activity", "Failed to create classifier.")
            runOnUiThread { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            return
        }

        // Updates the input image size.
        imageSizeX = classifier!!.imageSizeX
        imageSizeY = classifier!!.imageSizeY
    }

}