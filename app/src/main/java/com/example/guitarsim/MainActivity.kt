package com.example.guitarsim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.SensorManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.guitarsim.connectivity.working.AOAManager
import com.example.guitarsim.data.TouchInfo
import com.example.guitarsim.utils.*
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*


class MainActivity : FullscreenActivity() {

    companion object {
        const val TIEMPO_MUESTREO_MILLIS: Long = 10
    }

    val scaleLengthMm: Double
        get() = SharedPrefsUtils(this).getScaleLength().toDouble()

    val fretAmount: Int
        get() = SharedPrefsUtils(this).getFretAmount()

    val nodeAmount: Int
        get() = SharedPrefsUtils(this).getNodeAmount()

    val shouldEnumerateFrets: Boolean
        get() = SharedPrefsUtils(this).shouldEnumerateFrets()

    //    val lastSizes = hashMapOf<Int, Float>()
//    val lastJitters = ConcurrentLinkedQueue<Float>()
//    val lastCejillas = ConcurrentLinkedQueue<Boolean>()
    var cejillaPointers = arrayListOf<Int>()

    //    var nonCejillaCount = 0
    var tieneCejilla = false

    /* var */ val viewPortBeginPx: Int
        get() = ViewUtils.mmToPx(
            SharedPrefsUtils(this).getViewportLocation().toDouble()
        ) // TODO: Make dynamic
    val viewPortEndPx: Int
        get() = viewPortBeginPx + ViewUtils.getScreenHeight(this)
    val guitarScalePx
        get() = ViewUtils.mmToPx(scaleLengthMm)

    val guitarUtils by lazy { GuitarUtils(scaleLengthMm, fretAmount) }

    var shakeDetector: ShakeDetector? = null

    private lateinit var aoaManager: AOAManager
    private var isInForeground = true

    /* ============= MEDICION DE MUESTREO ============= */
    private var didLog: Boolean = false
    var medicionStart: Long = 0L

    var minNanoTimeSinceLastEvent: Long = 0L
    var lastEventNanoTime: Long = 0L

    val MILI_SEC: Long = 1000L
    val MICRO_SEC: Long = 1000L * MILI_SEC
    val NANO_SEC: Long = 1000L * MICRO_SEC
    val PERIODO_MEDICION: Long = 10L * NANO_SEC
    /* ============= MEDICION DE MUESTREO ============= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setShakeRecognizer()
        initUsbConnection()

//        aoaBridge = AOAManager2(this, intent)
//        aoaBridge?.startListening()
    }

    private fun setShakeRecognizer() {
        shakeDetector = ShakeDetector(getSystemService(Context.SENSOR_SERVICE) as SensorManager) {
            //            Toast.makeText(this, "Accel: ${shakeDetector?.mAccel}", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun initUsbConnection() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        var usbAccessory: UsbAccessory? = null

        val intent = intent
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED == intent.action) {
            usbAccessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
        }

        aoaManager = AOAManager(usbManager, usbAccessory)
        aoaManager.connect()
    }

    override fun onStart() {
        super.onStart()
        updateView()
    }

    override fun onResume() {
        super.onResume()
        shakeDetector?.resume()
        isInForeground = true
        setFrets()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.pause()
        isInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        aoaManager.disconnect()
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

//        medirMuestreoPantalla() // Uncomment to sample latency
//        mostrarTouchesViolentamente()
//        medirValoresDePresion()

        touchView.touches.entries.filter { !cejillaPointers.contains(it.key) }.map { it.value }
            .forEach {
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

        handleUsbConnection()

        load {
            Thread.sleep(TIEMPO_MUESTREO_MILLIS)
        }.then {
            updateView()
        }
    }

    private fun medirValoresDePresion() {
        touchView.touches.entries.firstOrNull { true }?.let {
            Log.v("PRESSURE_TEST_RESULTS", it.value.pressure.toString())
        }
    }

    private fun mostrarTouchesViolentamente() {
        if (touchView.touches.isNotEmpty()) {
            touchView.setBackgroundColor(Color.RED)
        } else {
            touchView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    var lastTouch = 0f
    var totalCount: Int = 1
    var sameCount: Int = 0
    var differentCount: Int = -1
    val SAMPLE_SIZE = 1000
    private fun medirMuestreoPantalla() {
        if (sameCount + differentCount < SAMPLE_SIZE) {
            touchView.touches.entries.firstOrNull { true }?.let {
                if (it.value.y == lastTouch) {
                    sameCount++
                } else {
                    differentCount++
                }
                lastTouch = it.value.y
            }
        } else {
            Log.v(
                "LATENCY_SAMPLE_RESULTS",
                "$TIEMPO_MUESTREO_MILLIS ms [$totalCount] Latecy sample results: $sameCount same vs $differentCount different"
            )
            sameCount = 0
            differentCount = 0
            totalCount++
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        /* ============= MEDICION DE MUESTREO ============= */
//        medirMuestreoNano() // Descomentar si se quiere medir
        /* ============= MEDICION DE MUESTREO ============= */

        val action = event.actionMasked
        val pointerId = event.getPointerId(event.actionIndex)
        val pointerIndex = event.actionIndex
        when (action) {
            MotionEvent.ACTION_POINTER_UP -> {
                touchView.removeTouch(pointerId)
                cejillaPointers.remove(pointerId)
            }
            MotionEvent.ACTION_UP -> {
                touchView.removeAllTouches()
                resetCejillaVariables()
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                touchView.touches[pointerId] = TouchInfo(
                    x = event.getX(pointerIndex),
                    y = event.getY(pointerIndex),
                    verticalStretching = 0f,
                    pressure = event.getPressure(pointerIndex),
                    size = event.getSize(pointerIndex)
                )
            }
            MotionEvent.ACTION_MOVE -> {
                touchView.touches[pointerId]?.apply {
                    y = event.getY(pointerIndex)

                    // Es vertical, pero recordemos que vertical para nosotros es horizontal para el celu
                    verticalStretching = event.getX(pointerIndex) - x

                    pressure = event.getPressure(pointerIndex)
                    size = event.getSize(pointerIndex)
                }
            }
        }

        when {
            event.pointerCount >= 2 -> processCejilla(/*event*/)
//            nonCejillaCount >= 10 -> {
//                resetCejillaVariables()
//                nonCejillaCount = 0
//            }
//            else -> nonCejillaCount++
        }

        return true
    }

    private fun processCejilla(/*event: MotionEvent*/) {
        val emPresses = touchView.touches.filter { isTouchingViewOnXAxis(it.value, emString) }
        val eMPresses = touchView.touches.filter { isTouchingViewOnXAxis(it.value, eMString) }
        val alignedPresses = twoAligned(emPresses, eMPresses)
        tieneCejilla = alignedPresses != null
        alignedPresses?.let {
            cejillaPointers.add(alignedPresses.first)
            cejillaPointers.add(alignedPresses.second)
        }
        return

//        val pointerIndexes = (0 until event.pointerCount)
//        val alignedPointers = pointerIndexes.filter { pointA ->
//            val others = pointerIndexes.filter { pointB ->
//                pointB != pointA && abs(event.getY(pointA) - event.getY(pointB)) <= 150
//            }
//            others.isNotEmpty()
//        }
//
//        val cleanAlignedPointers = alignedPointers.filter {
//            abs(event.getY(alignedPointers.first()) - event.getY(it)) <= 150
//        }
//
//        val hasAlignedTouches = cleanAlignedPointers.isNotEmpty()
//
//        if (hasAlignedTouches) {
//            cejillaPointers = cleanAlignedPointers.map { event.getPointerId(it) }
//
//            val sizeSum = cleanAlignedPointers.map { event.getSize(it) }.reduce { acc, i ->
//                abs(
//                    acc - i
//                )
//            }
//            val pressureSum = cleanAlignedPointers.map { event.getPressure(it) }.reduce { acc, i ->
//                abs(
//                    acc - i
//                )
//            }
//    /*                testText.text =
//                        if (hasAlignedTouches && sizeSum < 0.13 && pressureSum < 0.13) "Tiene cejilla" else "Sin cejilla"*/
//
//            val hasPreviousXsForAligned =
//                lastSizes.filter {
//                    cleanAlignedPointers.map { event.getPointerId(it) }.contains(
//                        it.key
//                    )
//                }
//                    .isNotEmpty()
//            if (hasPreviousXsForAligned) {
//                val entriesToRemove = arrayListOf<Int>()
//                lastJitters.add(lastSizes.map { entry ->
//                    val id = entry.key
//                    val lastSize = entry.value
//
//                    val pointerIndex = event.findPointerIndex(id)
//                    if (pointerIndex == -1) {
//                        entriesToRemove.add(entry.key)
//                        0f
//                    } else {
//                        val currentSize = event.getSize(pointerIndex)
//                        abs(currentSize - lastSize)
//                    }
//                }.sum())
//
//                if (lastJitters.size > 100) {
//                    lastJitters.remove()
//                }
//
//                val tieneCejilla = pressureSum < 0.5 && (lastJitters.max() ?: 0f) >= 0.03
//                lastCejillas.add(tieneCejilla)
//                if (lastCejillas.size > 50) {
//                    lastCejillas.remove()
//                }
//
//                /*testText.text =
//                        "${testText.text}\nJitter = ${lastJitters.max()}\nSize sum: $sizeSum\nPressure sum: $pressureSum"*/
//
//                // Clean no longer used positions
//                entriesToRemove.forEach {
//                    lastSizes.remove(it)
//                }
//
//                if (tieneCejilla()) {
//                    nonCejillaCount = 0
//                } else {
//                    cejillaPointers = listOf()
//                }
//            }
//
//            cleanAlignedPointers.forEach {
//                lastSizes[event.getPointerId(it)] = event.getSize(it)
//            }
//        } else {
//            resetCejillaVariables()
//        }
    }

    private fun twoAligned(
        touches1: Map<Int, TouchInfo>,
        touches2: Map<Int, TouchInfo>
    ): Pair<Int, Int>? {
        for (t1 in touches1) {
            for (t2 in touches2) {
                if (abs(t1.value.y - t2.value.y) <= 150) {
                    return Pair(t1.key, t2.key)
                }
            }
        }
        return null
    }

    private fun medirMuestreoNano() {
        if (didLog) return

        val nowNano = System.nanoTime()
        if (medicionStart == 0L) {
            medicionStart = nowNano
            lastEventNanoTime = medicionStart
        } else {
            minNanoTimeSinceLastEvent = if (minNanoTimeSinceLastEvent != 0L) {
                min(minNanoTimeSinceLastEvent, nowNano - lastEventNanoTime)
            } else {
                nowNano - lastEventNanoTime
            }
            if (nowNano - medicionStart >= PERIODO_MEDICION) {
                Log.v(
                    "TOUCH_EVENT",
                    "Minimum Time since last event: $minNanoTimeSinceLastEvent nano seconds"
                )
                Log.v(
                    "TOUCH_EVENT",
                    "Minimum Mili Time since last event: ${minNanoTimeSinceLastEvent.toDouble() / NANO_SEC * MILI_SEC} milli seconds"
                )
                didLog = true
            }
        }
    }

    private fun resetCejillaVariables() {
//        lastSizes.clear()
//        lastJitters.clear()
//        lastCejillas.clear()
        cejillaPointers.clear()
    }

    private fun tieneCejilla() =
        tieneCejilla //lastCejillas.size > 10 && lastCejillas.count { it } > lastCejillas.count { !it }

    private fun isTouchingViewOnXAxis(touch: TouchInfo, view: View): Boolean {
        var location = IntArray(2)
        view.getLocationOnScreen(location)

        val xLocation = location.first()

        return xLocation.toFloat() in (touch.startX..touch.endX) || (xLocation.toFloat() + view.width) in (touch.startX..touch.endX)
    }

    private fun setFrets() {
        GuitarUtils(scaleLengthMm, fretAmount)
        val fretsToShow = guitarUtils.fretsInViewport(viewPortBeginPx, viewPortEndPx)

        fretContainer.removeAllViews()

        var lastFret: Pair<Int, Int>? = null
        fretsToShow.forEach { fret ->
            val fretView = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.fret_width)
                ).apply {
                    val fretLocation = fret.second
                    topMargin = fretLocation - viewPortBeginPx
                }
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.fretColor))
            }

            if (shouldEnumerateFrets) {
                lastFret?.let { lastFret ->
                    val fretNumber = fret.first
                    val size =
                        resources.getDimension(if (fretNumber < 10) R.dimen.fret_one_digit_size else R.dimen.fret_two_digit_size)
                    val numberTextView = TextView(this).apply {
                        rotation = -90f
                        text = fretNumber.toString()
                        textSize = size

                        val displayMetrics = DisplayMetrics()
                        windowManager.defaultDisplay.getMetrics(displayMetrics)
                        val deviceWidth = displayMetrics.widthPixels

                        val widthMeasureSpec =
                            MeasureSpec.makeMeasureSpec(deviceWidth, MeasureSpec.AT_MOST)
                        val heightMeasureSpec =
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                        measure(widthMeasureSpec, heightMeasureSpec)
                        layoutParams = FrameLayout.LayoutParams(
                            measuredWidth,
                            measuredHeight
                        ).apply {
                            val fretLocation = fret.second
                            val lastFretLocation = lastFret.second
                            topMargin =
                                (lastFretLocation + fretLocation - 2 * viewPortBeginPx) / 2 - measuredHeight / 2
                            leftMargin = deviceWidth / 2 - measuredWidth / 2
                        }
                        setTextColor(
                            ContextCompat.getColor(
                                this@MainActivity,
                                R.color.fretNumberColor
                            )
                        )
                    }
                    fretContainer.addView(numberTextView)
                }
                lastFret = fret
            }

            fretContainer.addView(fretView)
        }
    }

    /* =============== USB Connection Handler =============== */
//    override fun run() {
//        while (isInForeground) {
//
//        }
//    }
    private fun handleUsbConnection() {
        val touchesToAdd =
            touchView.touches.keys.filter { !cejillaPointers.contains(it) && getChord(touchView.touches[it]) != -1 }
        val cejillaTouch = cejillaPointers.firstOrNull()
        val touchesToRemove = arrayListOf<Int>() // touchView.removedTouches

        val bufferSize =
            28 * touchesToAdd.size + 8 * touchesToRemove.size + if (cejillaTouch != null) 28 else 0

        val buff =
            ByteBuffer.allocate(
                if (bufferSize > 0) bufferSize else 8
            )
        buff.order(ByteOrder.LITTLE_ENDIAN)

        // Remove old touches
        /*for (touchId in touchesToRemove) {
            buff.putInt(0x3) // Command: Finger remove
            buff.putInt(touchId)
        }*/
        touchView.removedTouches.clear()

        // Add new touches
        for (touchId in touchesToAdd) {
            touchView.touches[touchId]?.let { touch ->
                buff.putInt(0x2) // Command: Finger update
                buff.putInt(0)   // isCejilla = false
                buff.putInt(getNode(touch))
                buff.putInt(getChord(touch))
                buff.putInt(touchId)
                buff.putInt(floor(touch.verticalStretching).toInt())
                buff.putInt(floor(touch.pressure * 1000).toInt())
                Log.v("asdf", "Chord: ${getChord(touch)}")
            }
        }
        // Add cejilla touch
        cejillaTouch?.let { cejillaPointerId ->
            touchView.touches[cejillaPointerId]?.let { touch ->
                buff.putInt(0x2) // Command: Finger update
                buff.putInt(1)   // isCejilla = false
                buff.putInt(getNode(touch))
                buff.putInt(getChord(touch))
                buff.putInt(cejillaPointerId)
                buff.putInt(floor(touch.verticalStretching).toInt())
                buff.putInt(floor(touch.pressure * 1000).toInt())
                Log.v("asdf", "Vert stretch: ${touch.verticalStretching}")
            }
        }

        // Send a "clear presses" signal if no touch is active
        if (bufferSize == 0) {
            buff.putInt(0x3)
            buff.putInt(0x0)
        }

        aoaManager.write(buff.array())
        /*try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }*/
    }
    /* =============== USB Connection Handler =============== */

    fun getNode(touch: TouchInfo): Int {
        val touchYInViewport = touch.y
        val touchYInScale = viewPortBeginPx + touchYInViewport

        val scaleLengthPx = ViewUtils.mmToPx(scaleLengthMm)

        return round(touchYInScale * nodeAmount / scaleLengthPx).toInt()
    }

    fun getChord(touch: TouchInfo?): Int {
        if (touch == null) return -1

        if (isTouchingViewOnXAxis(touch, eMString)) return 5
        if (isTouchingViewOnXAxis(touch, aString)) return 4
        if (isTouchingViewOnXAxis(touch, dString)) return 3
        if (isTouchingViewOnXAxis(touch, gString)) return 2
        if (isTouchingViewOnXAxis(touch, bString)) return 1
        if (isTouchingViewOnXAxis(touch, emString)) return 0
        return -1
    }
}
