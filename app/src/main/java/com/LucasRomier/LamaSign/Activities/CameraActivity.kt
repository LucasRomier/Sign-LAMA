@file:Suppress("unused", "PackageName", "DuplicatedCode", "Deprecation")

package com.LucasRomier.LamaSign.Activities

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.browse.MediaBrowser
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.LucasRomier.LamaSign.Classification.Device
import com.LucasRomier.LamaSign.Classification.Model
import com.LucasRomier.LamaSign.Classification.Recognition
import com.LucasRomier.LamaSign.Fragments.CameraConnectionFragment
import com.LucasRomier.LamaSign.Fragments.LegacyCameraConnectionFragment
import com.LucasRomier.LamaSign.R
import com.LucasRomier.LamaSign.Util.ImageUtils
import com.LucasRomier.LamaSign.Views.OverlayView
import com.LucasRomier.LamaSign.Views.OverlayView.DrawCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior


abstract class CameraActivity : AppCompatActivity(), OnImageAvailableListener, PreviewCallback, View.OnClickListener, OnItemSelectedListener {

    companion object
    {
        private const val PERMISSIONS_REQUEST = 1

        private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
    }

    protected var previewWidth = 0
    protected var previewHeight = 0

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var useCamera2API = false
    private var isProcessingFrame = false

    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null

    private var yRowStride = 0

    private var postInferenceCallback: Runnable? = null

    private var imageConverter: Runnable? = null

    private lateinit var bottomSheetLayout: LinearLayout
    private lateinit var gestureLayout: LinearLayout
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private lateinit var recognitionTextView: TextView
    private lateinit var recognition1TextView: TextView
    private lateinit var recognition2TextView: TextView
    private lateinit var recognitionValueTextView: TextView
    private lateinit var recognition1ValueTextView: TextView
    private lateinit var recognition2ValueTextView: TextView
    private lateinit var frameValueTextView: TextView
    private lateinit var cropValueTextView: TextView
    private lateinit var cameraResolutionTextView: TextView
    private lateinit var rotationTextView: TextView
    private lateinit var inferenceTimeTextView: TextView

    protected lateinit var bottomSheetArrowImageView: ImageView
    private lateinit var plusImageView: ImageView
    private lateinit var minusImageView: ImageView

    private lateinit var modelSpinner: Spinner
    private lateinit var deviceSpinner: Spinner

    private lateinit var threadsTextView: TextView

    private var model: Model = Model.SIGNS
    private var device: Device = Device.CPU
    private var numThreads = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Camera Activity", "onCreate $this")

        super.onCreate(null)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }

        threadsTextView = findViewById(R.id.threads)
        plusImageView = findViewById(R.id.plus)
        minusImageView = findViewById(R.id.minus)
        modelSpinner = findViewById(R.id.model_spinner)
        deviceSpinner = findViewById(R.id.device_spinner)
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout)
        gestureLayout = findViewById(R.id.gesture_layout)
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow)

        recognitionTextView = findViewById(R.id.detected_item)
        recognitionValueTextView = findViewById(R.id.detected_item_value)
        recognition1TextView = findViewById(R.id.detected_item1)
        recognition1ValueTextView = findViewById(R.id.detected_item1_value)
        recognition2TextView = findViewById(R.id.detected_item2)
        recognition2ValueTextView = findViewById(R.id.detected_item2_value)
        frameValueTextView = findViewById(R.id.frame_info)
        cropValueTextView = findViewById(R.id.crop_info)
        cameraResolutionTextView = findViewById(R.id.view_info)
        rotationTextView = findViewById(R.id.rotation_info)
        inferenceTimeTextView = findViewById(R.id.inference_info)

        val vto = gestureLayout.viewTreeObserver
        vto.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        gestureLayout.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    } else {
                        gestureLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                    // int width = bottomSheetLayout.getMeasuredWidth();
                    val height = gestureLayout.measuredHeight
                    sheetBehavior.peekHeight = height
                }
            }
        )

        sheetBehavior.isHideable = false
        sheetBehavior.setBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up)
                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {
                        }
                        BottomSheetBehavior.STATE_SETTLING -> bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up)
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            }
        )

        modelSpinner.onItemSelectedListener = this
        deviceSpinner.onItemSelectedListener = this
        plusImageView.setOnClickListener(this)
        minusImageView.setOnClickListener(this)
        model = Model.FromString(modelSpinner.selectedItem.toString())!!
        device = Device.FromString(deviceSpinner.selectedItem.toString())!!
        numThreads = threadsTextView.text.toString().trim { it <= ' ' }.toInt()
    }

    protected open fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return rgbBytes
    }

    protected open fun getLuminanceStride(): Int {
        return yRowStride
    }

    protected open fun getLuminance(): ByteArray? {
        return yuvBytes[0]
    }

    /** Callback for android.hardware.Camera API  */
    override fun onPreviewFrame(bytes: ByteArray?, camera: Camera) {
        if (isProcessingFrame) {
            Log.w("Camera Activity", "Dropping frame!")
            return
        }
        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                val previewSize = camera.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            Log.e("Camera Activity", "Exception!", e)
            return
        }
        isProcessingFrame = true
        yuvBytes[0] = bytes
        yRowStride = previewWidth
        imageConverter =
            Runnable { ImageUtils.ConvertYUV420SPToARGB8888(bytes!!, previewWidth, previewHeight, rgbBytes!!) }
        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }
        processImage()
    }

    /** Callback for Camera2 API  */
    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.ConvertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            Log.e("Camera Activity", "Exception!", e)
            Trace.endSection()
            return
        }

        Trace.endSection()
    }

    @Synchronized
    override fun onStart() {
        Log.d("Camera Activity", "onStart $this")
        super.onStart()
    }

    @Synchronized
    override fun onResume() {
        Log.d("Camera Activity", "onResume $this")
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    override fun onPause() {
        Log.d("Camera Activity", "onPause $this")
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            Log.e("Camera Activity", "Exception!", e)
        }
        super.onPause()
    }

    @Synchronized
    override fun onStop() {
        Log.d("Camera Activity", "onStop $this")
        super.onStop()
    }

    @Synchronized
    override fun onDestroy() {
        Log.d("Camera Activity", "onDestroy $this")
        super.onDestroy()
    }

    @Synchronized
    protected open fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler!!.post(r)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                setFragment()
            } else {
                requestPermission()
            }
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    this@CameraActivity,
                    "Camera permission is required for this demo",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else requiredLevel <= deviceLevel
        // deviceLevel is not LEGACY, can use numerical sort
    }

    private fun chooseCamera(): String? {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL
                        || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                ))
                Log.i("Camera Activity", "Camera API lv2?: $useCamera2API")
                return cameraId
            }
        } catch (e: CameraAccessException) {
            Log.e("Camera Activity", "Not allowed to access camera")
        }
        return null
    }

    protected open fun setFragment() {
        val cameraId = chooseCamera()
        val fragment: Fragment
        if (useCamera2API) {
            val camera2Fragment: CameraConnectionFragment = CameraConnectionFragment.NewInstance(
                object : MediaBrowser.ConnectionCallback() {
                    fun onPreviewSizeChosen(size: Size, rotation: Int) {
                        previewHeight = size.height
                        previewWidth = size.width
                        this@CameraActivity.onPreviewSizeChosen(size, rotation)
                    }
                },
                this,
                getLayoutId(),
                getDesiredPreviewFrameSize()!!
            )
            camera2Fragment.setCamera(cameraId)
            fragment = camera2Fragment
        } else {
            fragment = LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize()!!)
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    protected open fun fillBytes(planes: Array<Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                Log.d("Camera Activity", "Initializing buffer $i at size ${buffer.capacity()}")
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]!!]
        }
    }

    protected open fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    protected open fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    open fun requestRender(results: List<Recognition?>) {
        val overlay = findViewById<View>(R.id.overlay) as OverlayView

        overlay.setResults(results)
        overlay.postInvalidate()
    }

    open fun addCallback(callback: DrawCallback?) {
        val overlay = findViewById<View>(R.id.overlay) as OverlayView
        overlay.addCallback(callback!!)
    }

    @UiThread
    protected open fun showResultsInBottomSheet(results: List<Recognition?>?) {
        if (results != null && results.size >= 3) {
            val recognition: Recognition? = results[0]
            if (recognition != null) {
                if (recognition.title != null) recognitionTextView.text = recognition.title
                if (recognition.confidence != null) recognitionValueTextView.text =
                    java.lang.String.format("%.2f", 100 * recognition.confidence) + "%"
            }
            val recognition1: Recognition? = results[1]
            if (recognition1 != null) {
                if (recognition1.title != null) recognition1TextView.text = recognition1.title
                if (recognition1.confidence != null) recognition1ValueTextView.text =
                    java.lang.String.format("%.2f", 100 * recognition1.confidence) + "%"
            }
            val recognition2: Recognition? = results[2]
            if (recognition2 != null) {
                if (recognition2.title != null) recognition2TextView.text = recognition2.title
                if (recognition2.confidence != null) recognition2ValueTextView.text =
                    java.lang.String.format("%.2f", 100 * recognition2.confidence) + "%"
            }
        }
    }

    protected open fun showFrameInfo(frameInfo: String?) {
        frameValueTextView.text = frameInfo
    }

    protected open fun showCropInfo(cropInfo: String?) {
        cropValueTextView.text = cropInfo
    }

    protected open fun showCameraResolution(cameraInfo: String?) {
        cameraResolutionTextView.text = cameraInfo
    }

    protected open fun showRotationInfo(rotation: String?) {
        rotationTextView.text = rotation
    }

    protected open fun showInference(inferenceTime: String?) {
        inferenceTimeTextView.text = inferenceTime
    }

    protected open fun getModel(): Model? {
        return model
    }

    private fun setModel(model: Model) {
        if (this.model !== model) {
            Log.d("Camera Activity", "Updating  model: $model")
            this.model = model
            onInferenceConfigurationChanged()
        }
    }

    protected open fun getDevice(): Device? {
        return device
    }

    private fun setDevice(device: Device) {
        if (this.device !== device) {
            Log.d("Camera Activity", "Updating  device: $device")
            this.device = device
            val threadsEnabled = device === Device.CPU
            plusImageView.isEnabled = threadsEnabled
            minusImageView.isEnabled = threadsEnabled
            threadsTextView.text = if (threadsEnabled) numThreads.toString() else "N/A"
            onInferenceConfigurationChanged()
        }
    }

    protected open fun getNumThreads(): Int {
        return numThreads
    }

    private fun setNumThreads(numThreads: Int) {
        if (this.numThreads != numThreads) {
            Log.d("Camera Activity", "Updating  numThreads: $numThreads")
            this.numThreads = numThreads
            onInferenceConfigurationChanged()
        }
    }

    protected abstract fun processImage()

    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)

    protected abstract fun getLayoutId(): Int

    protected abstract fun getDesiredPreviewFrameSize(): Size?

    protected abstract fun onInferenceConfigurationChanged()

    override fun onClick(v: View) {
        if (v.id == R.id.plus) {
            val threads = threadsTextView.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads >= 9) return
            setNumThreads(++numThreads)
            threadsTextView.text = numThreads.toString()
        } else if (v.id == R.id.minus) {
            val threads = threadsTextView.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads == 1) {
                return
            }
            setNumThreads(--numThreads)
            threadsTextView.text = numThreads.toString()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        if (parent === modelSpinner) {
            setModel(Model.FromString(parent.getItemAtPosition(pos).toString())!!)
        } else if (parent === deviceSpinner) {
            setDevice(Device.FromString(parent.getItemAtPosition(pos).toString())!!)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing.
    }

}