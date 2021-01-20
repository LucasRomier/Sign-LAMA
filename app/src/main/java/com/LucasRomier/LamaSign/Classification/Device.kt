@file:Suppress("unused", "PackageName", "FunctionName")

package com.LucasRomier.LamaSign.Classification

/** The runtime device type used for executing classification. */
enum class Device {

    CPU,
    NN_API,
    GPU;

    companion object
    {

        fun FromString(str: String) : Device? {
            when(str) {
                "CPU" -> return CPU
                "GPU" -> return GPU
                "NN API" -> return NN_API
            }

            return null
        }

    }

}