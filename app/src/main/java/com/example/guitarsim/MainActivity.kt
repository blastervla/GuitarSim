package com.example.guitarsim

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.example.guitarsim.data.TouchInfo
import com.example.guitarsim.utils.load
import com.example.guitarsim.utils.then
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

class MainActivity : FullscreenActivity() {

    val lastSizes = hashMapOf<Int, Float>()
    val lastJitters = ConcurrentLinkedQueue<Float>()
    val lastCejillas = ConcurrentLinkedQueue<Boolean>()
    var cejillaPointers = listOf<Int>()
    var nonCejillaCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        updateView()
    }

    private fun updateView() {
        touchView.invalidate()

        // Check collisions here
        testText.text = if (tieneCejilla()) "Cejilla\n" else "\n"

        var touchingEMString = false
        var touchingAString = false
        var touchingDString = false
        var touchingGString = false
        var touchingBString = false
        var touchingEmString = false

        touchView.touches.entries.filter { !cejillaPointers.contains(it.key) }.map { it.value }.forEach {
            touchingEMString = touchingEMString || isTouchingViewOnXAxis(it, eMString)
            touchingAString = touchingAString || isTouchingViewOnXAxis(it, aString)
            touchingDString = touchingDString || isTouchingViewOnXAxis(it, dString)
            touchingGString = touchingGString || isTouchingViewOnXAxis(it, gString)
            touchingBString = touchingBString || isTouchingViewOnXAxis(it, bString)
            touchingEmString = touchingEmString || isTouchingViewOnXAxis(it, emString)
        }

        if (touchingEMString) testText.text = "${testText.text}  EM\n"
        if (touchingAString) testText.text = "${testText.text}  A\n"
        if (touchingDString) testText.text = "${testText.text}  D\n"
        if (touchingGString) testText.text = "${testText.text}  G\n"
        if (touchingBString) testText.text = "${testText.text}  B\n"
        if (touchingEmString) testText.text = "${testText.text}  Em"

        testText.visibility = if (testText.text.isNotBlank()) View.VISIBLE else View.GONE

        load {
            Thread.sleep(5)
        }.then {
            updateView()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            touchView.touches.remove(event.getPointerId(event.actionIndex))

            if (touchView.touches.isEmpty()) {
                // Then clear cejilla calculations
                resetCejillaVariables()
            }
        }

        // Clean removed touches
        ArrayList(touchView.touches.filter { event.findPointerIndex(it.key) == -1 }.map { it.key }).forEach {
            touchView.touches.remove(it)
        }

        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (touchView.touches.containsKey(pointerId)) {
                touchView.touches[pointerId]?.apply {
                    x = event.getX(i)
                    y = event.getY(i)
                    size = event.getSize(i)
                    // Add pressure too
                }
            } else {
                touchView.touches[pointerId] = TouchInfo(
                    x = event.getX(i),
                    y = event.getY(i),
                    size = event.getSize(i)
                )
            }
            /*testText.text =
                "(${event.getX(i)}, ${event.getY(i)})\n\nSize: ${event.getSize(i)}\nPressure: ${event.getPressure(i)}\nOrientation: ${event.getOrientation(
                    i
                )}\nTouches: ${event.pointerCount}"*/
        }

        if (event.pointerCount >= 2) {
            val pointerIndexes = (0 until event.pointerCount)
            val alignedPointers = pointerIndexes.filter { pointA ->
                val others = pointerIndexes.filter { pointB ->
                    pointB != pointA && abs(event.getY(pointA) - event.getY(pointB)) <= 150
                }
                others.isNotEmpty()
            }

            val cleanAlignedPointers = alignedPointers.filter {
                abs(event.getY(alignedPointers.first()) - event.getY(it)) <= 150
            }

            val hasAlignedTouches = cleanAlignedPointers.isNotEmpty()

            if (hasAlignedTouches) {
                cejillaPointers = cleanAlignedPointers.map { event.getPointerId(it) }

                val sizeSum = cleanAlignedPointers.map { event.getSize(it) }.reduce { acc, i -> abs(acc - i) }
                val pressureSum = cleanAlignedPointers.map { event.getPressure(it) }.reduce { acc, i -> abs(acc - i) }
/*                testText.text =
                    if (hasAlignedTouches && sizeSum < 0.13 && pressureSum < 0.13) "Tiene cejilla" else "Sin cejilla"*/

                val hasPreviousXsForAligned =
                    lastSizes.filter { cleanAlignedPointers.map { event.getPointerId(it) }.contains(it.key) }
                        .isNotEmpty()
                if (hasPreviousXsForAligned) {
                    val entriesToRemove = arrayListOf<Int>()
                    lastJitters.add(lastSizes.map { entry ->
                        val id = entry.key
                        val lastSize = entry.value

                        val pointerIndex = event.findPointerIndex(id)
                        if (pointerIndex == -1) {
                            entriesToRemove.add(entry.key)
                            0f
                        } else {
                            val currentSize = event.getSize(pointerIndex)
                            abs(currentSize - lastSize)
                        }
                    }.sum())

                    if (lastJitters.size > 100) {
                        lastJitters.remove()
                    }

                    val tieneCejilla = pressureSum < 0.5 && (lastJitters.max() ?: 0f) >= 0.03
                    lastCejillas.add(tieneCejilla)
                    if (lastCejillas.size > 50) {
                        lastCejillas.remove()
                    }

                    /*testText.text =
                        "${testText.text}\nJitter = ${lastJitters.max()}\nSize sum: $sizeSum\nPressure sum: $pressureSum"*/

                    // Clean no longer used positions
                    entriesToRemove.forEach {
                        lastSizes.remove(it)
                    }

                    if (tieneCejilla()) {
                        nonCejillaCount = 0
                    } else {
                        cejillaPointers = listOf()
                    }
                }

                cleanAlignedPointers.forEach {
                    lastSizes[event.getPointerId(it)] = event.getSize(it)
                }
            } else {
                resetCejillaVariables()
            }


//            testText.text =
//                "(${event.getX(0) - event.getX(1)}, ${event.getY(0) - event.getY(1)})\n\nSize: ${event.getSize(0) - event.getSize(
//                    1
//                )}\nPressure: ${event.getPressure(0) - event.getPressure(1)}" +
//                        "\nTouches: ${event.pointerCount}"
        } else if (nonCejillaCount >= 10) {
            resetCejillaVariables()
            nonCejillaCount = 0
        } else {
            nonCejillaCount++
        }

        return true
    }

    private fun resetCejillaVariables() {
        lastSizes.clear()
        lastJitters.clear()
        lastCejillas.clear()
        cejillaPointers = listOf()
    }

    private fun tieneCejilla() = lastCejillas.size > 10 && lastCejillas.count { it } > lastCejillas.count { !it }

    private fun isTouchingViewOnXAxis(touch: TouchInfo, view: View): Boolean {
        var location = IntArray(2)
        view.getLocationOnScreen(location)

        val xLocation = location.first()

        return xLocation.toFloat() in (touch.startX..touch.endX) || (xLocation.toFloat() + view.width) in (touch.startX..touch.endX)
    }
}
