@file:Suppress("unused", "PackageName", "DuplicatedCode", "Deprecation")

package com.LucasRomier.LamaSign.Activities

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.os.*
import android.util.Log
import android.util.Size
import android.view.*
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.LucasRomier.LamaSign.Classification.Device
import com.LucasRomier.LamaSign.Classification.Model
import com.LucasRomier.LamaSign.Classification.Recognition
import com.LucasRomier.LamaSign.Fragments.CameraConnectionFragment
import com.LucasRomier.LamaSign.R
import com.LucasRomier.LamaSign.Views.OverlayView.DrawCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton

abstract class CameraActivity : AppCompatActivity(), PreviewCallback, View.OnClickListener, OnItemSelectedListener {

    companion object
    {
        private const val PERMISSIONS_REQUEST = 1

        private val PERMISSION_LIST = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    protected var previewWidth = 0
    protected var previewHeight = 0

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var isProcessingFrame = false

    private var locked = false

    private var rawBytes: ByteArray? = null
    private var rawParameters: Camera.Parameters? = null

    private var yRowStride = 0

    private var postInferenceCallback: Runnable? = null

    private lateinit var fragment: CameraConnectionFragment

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

    private lateinit var captureButton: MaterialButton
    private lateinit var cropCaptureButton: MaterialButton

    protected lateinit var bottomSheetArrowImageView: ImageView
    private lateinit var plusImageView: ImageView
    private lateinit var minusImageView: ImageView

    private lateinit var modelSpinner: Spinner
    private lateinit var deviceSpinner: Spinner

    private lateinit var threadsTextView: TextView

    private var model: Model = Model.OPTIMIZED
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

        captureButton = findViewById(R.id.capture_button)
        cropCaptureButton = findViewById(R.id.capture_crop_button)

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

        captureButton.setOnClickListener(this)
        cropCaptureButton.setOnClickListener(this)

        plusImageView.setOnClickListener(this)
        minusImageView.setOnClickListener(this)

        model = Model.FromString(modelSpinner.selectedItem.toString())!!
        device = Device.FromString(deviceSpinner.selectedItem.toString())!!
        numThreads = threadsTextView.text.toString().trim { it <= ' ' }.toInt()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val item: MenuItem = menu!!.findItem(R.id.toggle_item)
        item.setActionView(R.layout.switch_layout)

        item.actionView.findViewById<SwitchCompat>(R.id.toggle_switch).setOnCheckedChangeListener { _, isChecked ->
            locked = isChecked

            if (isChecked) {
                fragment.camera!!.stopPreview()
            } else {
                fragment.camera!!.startPreview()
            }
        }

        return true
    }

    protected open fun getImagePreviewBytes(): ByteArray? {
        return rawBytes
    }

    protected open fun getRawParameters(): Camera.Parameters? {
        return rawParameters
    }

    /** Callback for android.hardware.Camera API  */
    override fun onPreviewFrame(bytes: ByteArray?, camera: Camera) {
        if (isProcessingFrame) {
            Log.w("Camera Activity", "Dropping frame!")
            return
        }
        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (previewWidth == 0 && previewHeight == 0) {
                rawParameters = camera.parameters

                val previewSize = rawParameters!!.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width

                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            Log.e("Camera Activity", "Exception!", e)
            return
        }
        isProcessingFrame = true

        // Allow locking of screen
        if (!locked) rawBytes = bytes!!

        yRowStride = previewWidth

        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }
        processImage()
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
            PERMISSION_LIST.all { permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PERMISSION_LIST.forEach { permission ->
                if (shouldShowRequestPermissionRationale(permission)) {
                    Toast.makeText(
                            this@CameraActivity,
                            "Camera permission is required for this demo",
                            Toast.LENGTH_LONG
                    ).show()
                }
            }

            requestPermissions(PERMISSION_LIST, PERMISSIONS_REQUEST)
        }
    }

    protected open fun setFragment() {
        fragment = CameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize()!!)
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
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
        //val overlay = findViewById<View>(R.id.overlay) as OverlayView

        //overlay.setResults(results)
        //overlay.postInvalidate()
    }

    open fun addCallback(callback: DrawCallback?) {
        //val overlay = findViewById<View>(R.id.overlay) as OverlayView
        //overlay.addCallback(callback!!)
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

    protected abstract fun cameraCapture(crop: Boolean)

    override fun onClick(v: View) {
        when (v.id) {
            R.id.plus -> {
                val threads = threadsTextView.text.toString().trim { it <= ' ' }
                var numThreads = threads.toInt()
                if (numThreads >= 9) return
                setNumThreads(++numThreads)
                threadsTextView.text = numThreads.toString()
            }
            R.id.minus -> {
                val threads = threadsTextView.text.toString().trim { it <= ' ' }
                var numThreads = threads.toInt()
                if (numThreads == 1) {
                    return
                }
                setNumThreads(--numThreads)
                threadsTextView.text = numThreads.toString()
            }
            R.id.capture_button -> {
                cameraCapture(crop = false)
            }
            R.id.capture_crop_button -> {
                cameraCapture(crop = true)
            }
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