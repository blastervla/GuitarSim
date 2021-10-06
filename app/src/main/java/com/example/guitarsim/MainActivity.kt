package com.example.guitarsim

import android.annotation.SuppressLint
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

    private val scaleLengthMm: Double
        get() = SharedPrefsUtils(this).getScaleLength().toDouble()

    private val fretAmount: Int
        get() = SharedPrefsUtils(this).getFretAmount()

    private val nodeAmount: Int
        get() = SharedPrefsUtils(this).getNodeAmount()

    private val shouldEnumerateFrets: Boolean
        get() = SharedPrefsUtils(this).shouldEnumerateFrets()

    private var cejillaPointers = arrayListOf<Int>()

    private var tieneCejilla = false

    private val viewPortBeginPx: Int
        get() = ViewUtils.mmToPx(
            SharedPrefsUtils(this).getViewportLocation().toDouble()
        )
    private val viewPortEndPx: Int
        get() = viewPortBeginPx + ViewUtils.getScreenHeight(this)

    private val guitarUtils by lazy { GuitarUtils(scaleLengthMm, fretAmount) }

    var shakeDetector: ShakeDetector? = null

    private lateinit var aoaManager: AOAManager
    private var isInForeground = true

    /* ============= MEDICION DE MUESTREO ============= */
    private var didLog: Boolean = false
    var medicionStart: Long = 0L

    var minNanoTimeSinceLastEvent: Long = 0L
    var lastEventNanoTime: Long = 0L

    private val MILI_SEC: Long = 1000L
    private val MICRO_SEC: Long = 1000L * MILI_SEC
    private val NANO_SEC: Long = 1000L * MICRO_SEC
    private val PERIODO_MEDICION: Long = 10L * NANO_SEC
    /* ============= MEDICION DE MUESTREO ============= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setShakeRecognizer()
        initUsbConnection()
    }

    private fun setShakeRecognizer() {
        shakeDetector = ShakeDetector(getSystemService(Context.SENSOR_SERVICE) as SensorManager) {
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

    @SuppressLint("SetTextI18n")
    private fun updateView() {
        touchView.invalidate()

        testText.text = if (tieneCejilla) "Cejilla\n" else "\n"

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
        var pointerIndex = event.actionIndex
        when (action) {
            MotionEvent.ACTION_POINTER_UP -> {
                touchView.removeTouch(pointerId)
                cejillaPointers.remove(pointerId)
            }
            MotionEvent.ACTION_UP -> {
                touchView.removeAllTouches()
                cejillaPointers.clear()
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
                touchView.touches.forEach { (pointerId, touchInfo) ->
                    pointerIndex = event.findPointerIndex(pointerId)
                    touchInfo.apply {
                        y = event.getY(pointerIndex)

                        // Es vertical, pero recordemos que vertical para nosotros es horizontal para el celu
                        verticalStretching = event.getX(pointerIndex) - x

                        pressure = event.getPressure(pointerIndex)
                        size = event.getSize(pointerIndex)
                    }
                }
            }
        }

        if (event.pointerCount >= 2) {
            processCejilla()
        }

        return true
    }

    private fun processCejilla() {
        val emPresses = touchView.touches.filter { isTouchingViewOnXAxis(it.value, emString) }
        val eMPresses = touchView.touches.filter { isTouchingViewOnXAxis(it.value, eMString) }
        val alignedPresses = twoAligned(emPresses, eMPresses)
        tieneCejilla = alignedPresses != null
        alignedPresses?.let {
            cejillaPointers.add(alignedPresses.first)
            cejillaPointers.add(alignedPresses.second)
        }
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

    private fun isTouchingViewOnXAxis(touch: TouchInfo, view: View): Boolean {
        val location = IntArray(2)
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
    private fun handleUsbConnection() {
        val touchesToAdd =
            touchView.touches.keys.filter { !cejillaPointers.contains(it) && getChords(touchView.touches[it]).isNotEmpty() }
        val cejillaTouch = cejillaPointers.firstOrNull()

        val chordAmount = touchesToAdd.sumBy { getChords(touchView.touches[it]).size }
        val bufferSize =
            28 * chordAmount + if (cejillaTouch != null) 28 else 0

        val buff =
            ByteBuffer.allocate(
                if (bufferSize > 0) bufferSize else 8
            )
        buff.order(ByteOrder.LITTLE_ENDIAN)

        // Remove old touches
        touchView.removedTouches.clear()

        // Add new touches
        for (touchId in touchesToAdd) {
            touchView.touches[touchId]?.let { touch ->
                getChords(touch).forEach { chord ->
                    buff.putInt(0x2) // Command: Finger update
                    buff.putInt(0)   // isCejilla = false
                    buff.putInt(getNode(touch))
                    buff.putInt(chord)
                    buff.putInt(touchId)
                    buff.putInt(floor(touch.verticalStretching).toInt())
                    buff.putInt(floor(touch.pressure * 1000).toInt())
                }
            }
        }
        // Add cejilla touch
        cejillaTouch?.let { cejillaPointerId ->
            touchView.touches[cejillaPointerId]?.let { touch ->
                buff.putInt(0x2) // Command: Finger update
                buff.putInt(1)   // isCejilla = false
                buff.putInt(getNode(touch))
                buff.putInt(0)
                buff.putInt(cejillaPointerId)
                buff.putInt(floor(touch.verticalStretching).toInt())
                buff.putInt(floor(touch.pressure * 1000).toInt())
            }
        }

        // Send a "clear presses" signal if no touch is active
        if (bufferSize == 0) {
            buff.putInt(0x3)
            buff.putInt(0x0)
        }

        aoaManager.write(buff.array())
    }
    /* =============== USB Connection Handler =============== */

    fun getNode(touch: TouchInfo): Int {
        val touchYInViewport = touch.y
        val touchYInScale = viewPortBeginPx + touchYInViewport

        val scaleLengthPx = ViewUtils.mmToPx(scaleLengthMm)

        return round(touchYInScale * nodeAmount / scaleLengthPx).toInt()
    }

    fun getChords(touch: TouchInfo?): List<Int> {
        val chords = ArrayList<Int>()
        if (touch == null) return chords


        if (isTouchingViewOnXAxis(touch, eMString)) chords.add(5)
        if (isTouchingViewOnXAxis(touch, aString)) chords.add(4)
        if (isTouchingViewOnXAxis(touch, dString)) chords.add(3)
        if (isTouchingViewOnXAxis(touch, gString)) chords.add(2)
        if (isTouchingViewOnXAxis(touch, bString)) chords.add(1)
        if (isTouchingViewOnXAxis(touch, emString)) chords.add(0)
        return chords
    }
}
