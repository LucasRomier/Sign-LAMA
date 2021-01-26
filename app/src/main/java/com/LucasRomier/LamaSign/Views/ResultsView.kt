@file:Suppress("unused", "PackageName", "Deprecation")

package com.LucasRomier.LamaSign.Views

import com.LucasRomier.LamaSign.Classification.Recognition

interface ResultsView {

    fun setResults(results: List<Recognition?>?)

}