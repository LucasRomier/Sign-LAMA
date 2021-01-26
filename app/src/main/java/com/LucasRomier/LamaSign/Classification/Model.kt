@file:Suppress("unused", "PackageName", "FunctionName", "Deprecation")

package com.LucasRomier.LamaSign.Classification

/** The model type used for classification. */
enum class Model(val path: String) {

    SIGNS("signs.tflite");

    companion object
    {
        fun FromString(str: String) : Model? {
            when(str) {
                "Signs" -> return SIGNS
            }

            return null
        }
    }

}