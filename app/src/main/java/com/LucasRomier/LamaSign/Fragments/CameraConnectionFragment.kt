@file:Suppress("unused", "PackageName", "FunctionName", "Deprecation")

package com.LucasRomier.LamaSign.Fragments

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.os.Bundle
import android.os.HandlerThread
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.LucasRomier.LamaSign.R
import com.LucasRomier.LamaSign.Util.CompareSizesByArea
import com.LucasRomier.LamaSign.Util.ImageUtils
import com.LucasRomier.LamaSign.Views.AutoFitTextureView
import com.LucasRomier.LamaSign.Views.OverlayView
import java.io.IOException
import java.util.*

class CameraConnectionFragment(
    private var imageListener: PreviewCallback,
    private var layout: Int,
    private val inputSize: Size) : Fragment() {

    companion object
    {
        /** Conversion from screen rotation to JPEG orientation.  */
        private val ORIENTATIONS = SparseIntArray()

        /**
         * The camera preview size will be chosen to be the smallest frame by pixel size capable of
         * containing a DESIRED_SIZE x DESIRED_SIZE square.
         */
        private const val MINIMUM_PREVIEW_SIZE = 320

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
         * width and height are at least as large as the minimum of both, or an exact match if possible.
         *
         * @param choices The list of sizes that the camera supports for the intended output class
         * @param width The minimum desired width
         * @param height The minimum desired height
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        fun ChooseOptimalSize(choices: Array<Size?>, width: Int, height: Int): Size {
            val minSize = width.coerceAtMost(height).coerceAtLeast(MINIMUM_PREVIEW_SIZE)
            val desiredSize = Size(width, height)

            // Collect the supported resolutions that are at least as big as the preview Surface
            var exactSizeFound = false
            val bigEnough: MutableList<Size?> = ArrayList()
            val tooSmall: MutableList<Size?> = ArrayList()
            for (option in choices) {
                if (option == desiredSize) {
                    // Set the size but don't return yet so that remaining sizes will still be logged.
                    exactSizeFound = true
                }
                if (option!!.height >= minSize && option.width >= minSize) {
                    bigEnough.add(option)
                } else {
                    tooSmall.add(option)
                }
            }
            Log.i("Camera Fragment", "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize)
            Log.i("Camera Fragment", "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]")
            Log.i("Camera Fragment", "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]")
            if (exactSizeFound) {
                Log.i("Camera Fragment", "Exact size match found.")
                return desiredSize
            }

            // Pick the smallest of those, assuming we found any
            return if (bigEnough.size > 0) {
                val chosenSize = Collections.min(bigEnough, CompareSizesByArea())
                Log.i("Camera Fragment", "Chosen size: " + chosenSize!!.width + "x" + chosenSize.height)
                chosenSize
            } else {
                Log.e("Camera Fragment", "Couldn't find any suitable preview size")
                choices[0]!!
            }
        }

    }

    var camera: Camera? = null

    /** An [AutoFitTextureView] for camera preview.  */
    private var textureView: AutoFitTextureView? = null

    /** An [OverlayView] for bounding boxes preview.  */
    //private var overlayView: OverlayView? = null

    /**
     * Handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            val index = getCameraId()
            camera = Camera.open(index)
            try {
                val parameters = camera!!.parameters
                val focusModes = parameters.supportedFocusModes
                if (focusModes != null
                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                ) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }
                val cameraSizes = parameters.supportedPreviewSizes
                val sizes = arrayOfNulls<Size>(cameraSizes.size)
                var i = 0
                for (size in cameraSizes) {
                    sizes[i++] = Size(size.width, size.height)
                }
                val previewSize: Size = ChooseOptimalSize(
                    sizes, inputSize.width, inputSize.height
                )
                parameters.setPreviewSize(previewSize.width, previewSize.height)
                camera!!.setDisplayOrientation(90)
                camera!!.parameters = parameters
                camera!!.setPreviewTexture(texture)
            } catch (exception: IOException) {
                camera!!.release()
            }
            camera!!.setPreviewCallbackWithBuffer(imageListener)
            val s = camera!!.parameters.previewSize
            camera!!.addCallbackBuffer(ByteArray(ImageUtils.GetYUVByteSize(s.height, s.width)))
            textureView!!.setAspectRatio(s.height, s.width)
            camera!!.startPreview()
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
        //overlayView = view.findViewById<View>(R.id.overlay) as OverlayView
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView!!.isAvailable) {
            if (camera != null) {
                camera!!.startPreview()
            }
        } else {
            textureView!!.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        stopCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /** Starts a background thread and its Handler.  */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread!!.start()
    }

    /** Stops the background thread and its Handler.  */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
        } catch (e: InterruptedException) {
            Log.e("Camera Activity", "Exception!", e)
        }
    }

    private fun stopCamera() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            camera!!.release()
            camera = null
        }
    }

    private fun getCameraId(): Int {
        val ci = CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, ci)
            if (ci.facing == CameraInfo.CAMERA_FACING_BACK) return i
        }
        return -1 // No camera found
    }

}