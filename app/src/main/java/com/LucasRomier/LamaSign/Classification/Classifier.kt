@file:Suppress("unused", "PackageName")

package com.LucasRomier.LamaSign.Classification

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions
import java.util.*

/**
 * A classifier specialized to label images using TensorFlow Lite.
 *
 * @param activity The current Activity.
 * @param device The device to use for classification.
 * @param numThreads The number of threads to use for classification.
 * @param model The model to use for classification.
 * @return A classifier with the desired configuration.
 * */
class Classifier(
        activity: Activity?,
        device: Device?,
        numThreads: Int,
        model: Model) {

    companion object
    {
        private const val TAG = "ClassifierWithTaskApi"

        /** Number of results to show in the UI.  */
        private const val MAX_RESULTS = 3
    }

    /** Image size along the x axis.  */
    var imageSizeX = 0
        private set

    /** Image size along the y axis.  */
    var imageSizeY = 0
        private set

    /** An instance of the driver class to run model inference with Tensorflow Lite.  */
    private var imageClassifier: ImageClassifier? = null

    init {
        require(!(device != Device.CPU || numThreads != 1)) {
            ("Manipulating the hardware accelerators and numbers of threads is not allowed in the Task library currently. Only CPU + single thread is allowed.")
        }

        // Create the ImageClassifier instance.
        val options = ImageClassifierOptions.builder().setMaxResults(MAX_RESULTS).build()
        imageClassifier = ImageClassifier.createFromFileAndOptions(activity, model.path, options)
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.")

        // Get the input image size information of the underlying tflite model.
        val tfliteModel = FileUtil.loadMappedFile(activity!!, model.path)
        val metadataExtractor = MetadataExtractor(tfliteModel)
        // Image shape is in the format of {1, height, width, 3}.
        val imageShape = metadataExtractor.getInputTensorShape( /*inputIndex=*/0)
        imageSizeY = imageShape[1]
        imageSizeX = imageShape[2]
    }

    /** Runs inference and returns the classification results.  */
    fun recognizeImage(bitmap: Bitmap, sensorOrientation: Int): List<Recognition?> {
        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")
        val inputImage = TensorImage.fromBitmap(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val cropSize = width.coerceAtMost(height)
        // TODO(b/169379396): investigate the impact of the resize algorithm on accuracy.
        // Task Library resize the images using bilinear interpolation, which is slightly different from
        // the nearest neighbor sampling algorithm used in lib_support. See
        // https://github.com/tensorflow/examples/blob/0ef3d93e2af95d325c70ef3bcbbd6844d0631e07/lite/examples/image_classification/android/lib_support/src/main/java/org/tensorflow/lite/examples/classification/tflite/Classifier.java#L310.
        val imageOptions = ImageProcessingOptions.builder()
                .setOrientation(getOrientation(sensorOrientation)) // Set the ROI to the center of the image.
                .setRoi(
                        Rect( /*left=*/
                                (width - cropSize) / 2,  /*top=*/
                                (height - cropSize) / 2,  /*right=*/
                                (width + cropSize) / 2,  /*bottom=*/
                                (height + cropSize) / 2))
                .build()

        // Runs the inference call.
        Trace.beginSection("runInference")
        val startTimeForReference = SystemClock.uptimeMillis()
        val results = imageClassifier!!.classify(inputImage, imageOptions)
        val endTimeForReference = SystemClock.uptimeMillis()
        Trace.endSection()
        Log.v(TAG, "Timecost to run model inference: " + (endTimeForReference - startTimeForReference))
        Trace.endSection()
        return getRecognitions(results)
    }

    /** Closes the interpreter and model to release resources.  */
    fun close() {
        imageClassifier?.close()
    }

    /**
     * Converts a list of [Classifications] objects into a list of [Recognition] objects
     * to match the interface of other inference method, such as using the [TFLite
     * Support Library.](https://github.com/tensorflow/examples/tree/master/lite/examples/image_classification/android/lib_support).
     */
    private fun getRecognitions(classifications: List<Classifications>): List<Recognition?> {
        val recognitions = ArrayList<Recognition?>()
        // All the demo models are single head models. Get the first Classifications in the results.
        for (category in classifications[0].categories) {
            recognitions.add(
                    Recognition(
                            "" + category.label, category.label, category.score, null))
        }
        return recognitions
    }

    /* Convert the camera orientation in degree into {@link ImageProcessingOptions#Orientation}.*/
    private fun getOrientation(cameraOrientation: Int): ImageProcessingOptions.Orientation {
        return when (cameraOrientation / 90) {
            3 -> ImageProcessingOptions.Orientation.BOTTOM_LEFT
            2 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
            1 -> ImageProcessingOptions.Orientation.TOP_RIGHT
            else -> ImageProcessingOptions.Orientation.TOP_LEFT
        }
    }

}