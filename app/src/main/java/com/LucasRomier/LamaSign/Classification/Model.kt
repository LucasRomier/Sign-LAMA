@file:Suppress("unused", "PackageName", "FunctionName", "Deprecation")

package com.LucasRomier.LamaSign.Classification

/** The model type used for classification. */
enum class Model(val path: String) {

    DEFAULT("default_metadata.tflite"),
    OPTIMIZED("optimized_metadata.tflite");

    companion object
    {
        fun FromString(str: String) : Model? {
            when(str) {
                "Default" -> return DEFAULT
                "Optimized" -> return OPTIMIZED
            }

            return null
        }
    }

}