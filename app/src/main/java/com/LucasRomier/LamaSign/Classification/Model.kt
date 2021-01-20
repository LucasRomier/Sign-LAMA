@file:Suppress("unused", "PackageName", "FunctionName")

package com.LucasRomier.LamaSign.Classification

/** The model type used for classification. */
enum class Model(val path: String) {

    FLOAT_MOBILE_NET("mobilenet_v1_1.0_224.tflite"),
    QUANTIZED_MOBILE_NET("mobilenet_v1_1.0_224_quant.tflite"),
    FLOAT_EFFICIENT_NET("efficientnet-lite0-fp32.tflite"),
    QUANTIZED_EFFICIENT_NET("efficientnet-lite0-int8.tflite");

    companion object
    {
        fun FromString(str: String) : Model? {
            when(str) {
                "Quantized Efficient Net" -> return QUANTIZED_EFFICIENT_NET
                "Float Efficient Net" -> return FLOAT_EFFICIENT_NET
                "Quantized Mobile Net" -> return QUANTIZED_MOBILE_NET
                "Float Mobile Net" -> return FLOAT_MOBILE_NET
            }

            return null
        }
    }

}