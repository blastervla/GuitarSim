package com.example.guitarsim

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.guitarsim.connectivity.working.AOAManager
import com.example.guitarsim.data.TouchInfo
import com.example.guitarsim.utils.*
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

class MainActivity : FullscreenActivity() {

    companion object {
        const val TIEMPO_MUESTREO_MILLIS: Long = 5
    }

    val scaleLengthMm: Double
        get() = SharedPrefsUtils(this).getScaleLength().toDouble()

    val fretAmount: Int
        get() = SharedPrefsUtils(this).getFretAmount()

    val nodeAmount: Int
        get() = SharedPrefsUtils(this).getNodeAmount()

    val lastSizes = hashMapOf<Int, Float>()
    val lastJitters = ConcurrentLinkedQueue<Float>()
    val lastCejillas = ConcurrentLinkedQueue<Boolean>()
    var cejillaPointers = listOf<Int>()
    var nonCejillaCount = 0

    /* var */ val viewPortBeginPx: Int
        get() = ViewUtils.mmToPx(SharedPrefsUtils(this).getViewportLocation().toDouble()) // TODO: Make dynamic
    val viewPortEndPx: Int
        get() = viewPortBeginPx + ViewUtils.getScreenHeight(this)
    val guitarScalePx
        get() = ViewUtils.mmToPx(scaleLengthMm)

    val guitarUtils by lazy { GuitarUtils(scaleLengthMm, fretAmount) }

    var shakeDetector: ShakeDetector? = null

    private lateinit var aoaManager: AOAManager
    private var isInForeground = true

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

        handleUsbConnection()

        load {
            Thread.sleep(TIEMPO_MUESTREO_MILLIS)
        }.then {
            updateView()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            touchView.removeTouch(event.getPointerId(event.actionIndex))

            if (touchView.touches.isEmpty()) {
                // Then clear cejilla calculations
                resetCejillaVariables()
            }
        } else if (action == MotionEvent.ACTION_UP && touchView.touches.size == 1) {
            touchView.removeAllTouches()
            resetCejillaVariables()
            return true
        }

        // Clean removed touches
        ArrayList(touchView.touches.filter { event.findPointerIndex(it.key) == -1 }.map { it.key }).forEach {
            touchView.removeTouch(it)
        }

        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (touchView.touches.containsKey(pointerId)) {
                touchView.touches[pointerId]?.apply {
                    y = event.getY(i)

                    // Es vertical, pero recordemos que vertical para nosotros es horizontal para el celu
                    verticalStretching =
                        event.getX(i) - x // TODO: Definir si + es para abajo y - es para arriba (quizás hay que cambiar el signo)

                    pressure = event.getPressure(i)
                    size = event.getSize(i)
                }
            } else {
                touchView.touches[pointerId] = TouchInfo(
                    x = event.getX(i),
                    y = event.getY(i),
                    verticalStretching = 0f,
                    pressure = event.getPressure(i),
                    size = event.getSize(i)
                )
            }
            /*touchView.touches[pointerId]?.let {
                Log.v(
                    "GuitarSim", "verticalStretching = ${it.verticalStretching}\npressure = ${event.pressure}\n" +
                            "size = ${event.size}"
                )
            }*/
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

    private fun setFrets() {
        GuitarUtils(scaleLengthMm, fretAmount)
        val fretsToShow = guitarUtils.fretsInViewport(viewPortBeginPx, viewPortEndPx)

        fretContainer.removeAllViews()

        fretsToShow.forEach { fretLocation ->
            val fretView = View(this).apply {
                // TODO: Pasar 12 a dimens
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.fret_width)
                ).apply {
                    topMargin = fretLocation - viewPortBeginPx
                }
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.fretColor))
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

        val bufferSize = 28 * touchesToAdd.size + 8 * touchesToRemove.size + if (cejillaTouch != null) 28 else 0

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

        if (bufferSize == 0) {
            buff.putInt(0x3)
            buff.putInt(0x0) // Remove all pressess
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
