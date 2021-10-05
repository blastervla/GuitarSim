package com.example.guitarsim.utils

class GuitarUtils(scaleMillis: Double, fretAmount: Int) {
    private var fretsPx: ArrayList<Int> = arrayListOf()

    init {
        var scalingFactor = 0.0
        var distance = 0.0

        for (fret in 0..fretAmount) {
            val location = scaleMillis - distance
            scalingFactor = location / 17.817
            distance += scalingFactor
            fretsPx.add(ViewUtils.mmToPx(650 - location))
        }

        /*val minFret = fretsPx.min()
        minFret?.let { min ->
            fretsPx = fretsPx.map { it - minFret } as ArrayList<Int>
        }*/
    }

    fun fretsInViewport(viewPortBeginPx: Int, viewPortEndPx: Int) =
        fretsPx.mapIndexed { i, it -> Pair(i, it) }.filter { it.second in viewPortBeginPx..viewPortEndPx }
}