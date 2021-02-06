@file:Suppress("unused", "PackageName", "Deprecation")

package com.LucasRomier.LamaSign.Classification

import android.app.Activity
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.TensorProcessor

import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

import org.tensorflow.lite.support.image.TensorImage

import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil

import org.tensorflow.lite.gpu.GpuDelegate

import org.tensorflow.lite.support.image.ops.Rot90Op

import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod

import org.tensorflow.lite.support.image.ops.ResizeOp

import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp

import org.tensorflow.lite.support.image.ImageProcessor

import android.graphics.Bitmap

import org.tensorflow.lite.support.label.TensorLabel

import android.os.SystemClock
import android.os.Trace
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.util.*
import kotlin.collections.ArrayList


class ClassifierLib(
    activity: Activity?,
    device: Device?,
    numThreads: Int,
    model: Model) {

    companion object {
        private const val TAG = "ClassifierWithSupport"

        /** Number of results to show in the UI.  */
        private const val MAX_RESULTS = 3

        /**
         * The quantized model does not require normalization, thus set mean as 0.0f, and std as 1.0f to
         * bypass the normalization.
         */
        private const val IMAGE_MEAN = 0.0f

        private const val IMAGE_STD = 1.0f

        /** Quantized MobileNet requires additional dequantization to the output probability.  */
        private const val PROBABILITY_MEAN = 0.0f

        private const val PROBABILITY_STD = 255.0f
    }

    /** The loaded TensorFlow Lite model. */

    /** The loaded TensorFlow Lite model.  */
    /** Image size along the x axis.  */
    var imageSizeX = 0

    /** Image size along the y axis.  */
    var imageSizeY = 0

    /** Optional GPU delegate for accleration.  */
    private var gpuDelegate: GpuDelegate? = null

    /** Optional NNAPI delegate for accleration.  */
    private var nnApiDelegate: NnApiDelegate? = null

    /** An instance of the driver class to run model inference with Tensorflow Lite.  */
    private var tflite: Interpreter? = null

    /** Options for configuring the Interpreter.  */
    private val tfliteOptions: Interpreter.Options = Interpreter.Options()

    /** Labels corresponding to the output of the vision model.  */
    private var labels: List<String>? = null

    /** Input image TensorBuffer.  */
    private var inputImageBuffer: TensorImage? = null

    /** Output probability TensorBuffer.  */
    private var outputProbabilityBuffer: TensorBuffer? = null

    /** Processer to apply post processing of the output probability.  */
    private var probabilityProcessor: TensorProcessor? = null

    init {
        val tfliteModel = FileUtil.loadMappedFile(activity!!, model.path)
        when (device) {
            Device.NN_API -> {
                nnApiDelegate = NnApiDelegate()
                tfliteOptions.addDelegate(nnApiDelegate)
            }
            Device.GPU -> {
                gpuDelegate = GpuDelegate()
                tfliteOptions.addDelegate(gpuDelegate)
            }
            Device.CPU -> tfliteOptions.setUseXNNPACK(true)
        }

        tfliteOptions.setNumThreads(numThreads)
        tflite = Interpreter(tfliteModel, tfliteOptions)

        // Loads labels out from the label file.

        // Loads labels out from the label file.
        labels = FileUtil.loadLabels(activity, "labels.txt")

        // Reads type and shape of input and output tensors, respectively.

        // Reads type and shape of input and output tensors, respectively.
        val imageTensorIndex = 0
        val imageShape = tflite!!.getInputTensor(imageTensorIndex).shape() // {1, height, width, 3}

        imageSizeY = imageShape[1]
        imageSizeX = imageShape[2]
        val imageDataType: DataType = tflite!!.getInputTensor(imageTensorIndex).dataType()
        val probabilityTensorIndex = 0
        val probabilityShape = tflite!!.getOutputTensor(probabilityTensorIndex).shape() // {1, NUM_CLASSES}

        val probabilityDataType: DataType = tflite!!.getOutputTensor(probabilityTensorIndex).dataType()

        // Creates the input tensor.

        // Creates the input tensor.
        inputImageBuffer = TensorImage(imageDataType)

        // Creates the output tensor and its processor.

        // Creates the output tensor and its processor.
        outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)

        // Creates the post processor for the output probability.

        // Creates the post processor for the output probability.
        probabilityProcessor = TensorProcessor.Builder()
            .add(NormalizeOp(PROBABILITY_MEAN, PROBABILITY_STD))
            .build()

        Log.d(TAG, "Created a Tensorflow Lite Image Classifier with shape $probabilityShape")
    }

    /** Runs inference and returns the classification results.  */
    fun recognizeImage(bitmap: Bitmap, sensorOrientation: Int): List<Recognition?> {
        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")
        Trace.beginSection("loadImage")
        val startTimeForLoadImage = SystemClock.uptimeMillis()
        inputImageBuffer = loadImage(bitmap, sensorOrientation)
        val endTimeForLoadImage = SystemClock.uptimeMillis()
        Trace.endSection()
        Log.v(TAG, "Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage))

        // Runs the inference call.
        Trace.beginSection("runInference")
        val startTimeForReference = SystemClock.uptimeMillis()
        tflite!!.run(inputImageBuffer!!.buffer, outputProbabilityBuffer!!.buffer.rewind())
        val endTimeForReference = SystemClock.uptimeMillis()
        Trace.endSection()
        Log.v(TAG, "Timecost to run model inference: " + (endTimeForReference - startTimeForReference))

        // Gets the map of label and probability.
        val labeledProbability = TensorLabel(labels!!, probabilityProcessor!!.process(outputProbabilityBuffer))
            .mapWithFloatValue
        Trace.endSection()

        // Gets top-k results.
        return getTopKProbability(labeledProbability)
    }

    /** Closes the interpreter and model to release resources.  */
    fun close() {
        if (tflite != null) {
            tflite!!.close()
            tflite = null
        }
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
            gpuDelegate = null
        }
        if (nnApiDelegate != null) {
            nnApiDelegate!!.close()
            nnApiDelegate = null
        }
    }

    /** Loads input image, and applies preprocessing.  */
    private fun loadImage(bitmap: Bitmap, sensorOrientation: Int): TensorImage {
        // Loads bitmap into a TensorImage.
        inputImageBuffer!!.load(bitmap)

        // Creates processor for the TensorImage.
        val cropSize: Int = bitmap.width.coerceAtMost(bitmap.height)
        val numRotation = sensorOrientation / 90
        // TODO(b/143564309): Fuse ops inside ImageProcessor.
        val imageProcessor = ImageProcessor.Builder()
            .add(
                ResizeWithCropOrPadOp(
                    cropSize,
                    cropSize
                )
            ) // TODO(b/169379396): investigate the impact of the resize algorithm on accuracy.
            // To get the same inference results as lib_task_api, which is built on top of the Task
            // Library, use ResizeMethod.BILINEAR.
            .add(ResizeOp(imageSizeX, imageSizeY, ResizeMethod.NEAREST_NEIGHBOR))
            .add(Rot90Op(numRotation))
            .add(NormalizeOp(IMAGE_MEAN, IMAGE_STD))
            .build()
        return imageProcessor.process(inputImageBuffer)
    }

    /** Gets the top-k results.  */
    private fun getTopKProbability(labelProb: Map<String, Float>): List<Recognition?> {
        // Find the best classifications.
        // Intentionally reversed to put high confidence at the head of the queue.
        val pq: PriorityQueue<Recognition> = PriorityQueue(MAX_RESULTS) { lhs, rhs ->
            (rhs.confidence!!).compareTo(lhs.confidence!!)
        }
        for ((key, value) in labelProb) {
            pq.add(Recognition("" + key, key, value, null))
        }
        val recognitions: ArrayList<Recognition?> = ArrayList()
        val recognitionsSize: Int = pq.size.coerceAtMost(MAX_RESULTS)
        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll())
        }
        return recognitions
    }

}