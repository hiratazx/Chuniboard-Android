package com.github.brokenithm.activity

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.hardware.Camera
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.brokenithm.BrokenithmApplication
import com.github.brokenithm.R
import com.github.brokenithm.util.AsyncTaskUtil
import com.github.brokenithm.util.FeliCa
import net.cachapa.expandablelayout.ExpandableLayout
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var senderTask: AsyncTaskUtil.AsyncTask<InetSocketAddress?, Unit, Unit>
    private lateinit var receiverTask: AsyncTaskUtil.AsyncTask<InetSocketAddress?, Unit, Unit>
    private lateinit var pingPongTask: AsyncTaskUtil.AsyncTask<Unit, Unit, Unit>
    private var mExitFlag = true
    private lateinit var app: BrokenithmApplication
    private val serverPort = 52468
    private val mAirIdx = listOf(4, 5, 2, 3, 0, 1)

    // TCP
    private var mTCPMode = false
    private lateinit var mTCPSocket : Socket

    // state
    private val numOfButtons = 16
    private val numOfGaps = 16
    private val buttonWidthToGap = 7.428571f
    private val numOfAirBlock = 6
    private var mEnableTouchSize = false
    private var mFatTouchSizeThreshold = 0.027f
    private var mExtraFatTouchSizeThreshold = 0.035f
    private var mCurrentDelay = 0f

    private var mButtonAreaHeight = 0f
    private var mAirAreaHeightBoundary = 0f
    private var mAirBlockHeight = 0f

    // Buttons
    private var mCurrentAirHeight = 6
    private var mLastButtons = HashSet<Int>()
    private var mTestButton = false
    private var mServiceButton = false
    private data class InputEvent(val keys: MutableSet<Int>? = null, val airHeight : Int = 6, val testButton: Boolean = false, val serviceButton: Boolean = false)

    // LEDs
    private lateinit var mLEDBitmap: Bitmap
    private lateinit var mLEDCanvas: Canvas
    private var buttonWidth = 0f
    private var gapWidth = 0f
    private var buttonBlockWidth = 0f
    private lateinit var mButtonRenderer: View

    // vibrator
    private var mEnableVibrate = true
    private lateinit var vibrator: Vibrator
    private lateinit var vibratorTask: AsyncTaskUtil.AsyncTask<Unit, Unit, Unit>
    private lateinit var vibrateMethod: (Long) -> Unit
    private val vibrateLength = 50L
    private val mVibrationQueue = ArrayDeque<Long>()

    // view
    private var mEnableAir = true
    private var mAirSource = 3
    private var mSimpleAir = false
    private var mDebugInfo = false
    private var mShowDelay = false
    private lateinit var mDelayText: TextView
    private lateinit var mCameraDebugText: TextView
    private var windowWidth = 0f
    private var windowHeight = 0f
    private var mTouchAreaRect: Rect? = null

    // sensor
    private var mSensorManager: SensorManager? = null
    private var mSensorCallback: ((Float) -> Unit)? = null
    private var mGyroLowestBound = 0.8f
    private var mGyroHighestBound = 1.35f
    private var mAccelThreshold = 2f
    private var mProximityThreshold = 0.5f
    private var mLightThreshold = 50f

    // camera
    private var mCamera: Camera? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mCameraBrightnessThreshold = 50f
    private val mCameraRequestCode = 1001

    private val listener = object : SensorEventListener {
        var current = 0
        var lastAcceleration = 0f

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            return
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            val threshold = mAccelThreshold
            val sensorName = event.sensor.name.lowercase()
            val isProximityType = event.sensor.type == Sensor.TYPE_PROXIMITY || sensorName.contains("proximity")
            val isLightType = event.sensor.type == Sensor.TYPE_LIGHT || sensorName.contains("light")

            if (isProximityType) {
                if (mAirSource != 5) return
                val distance = event.values[0]
                val isAir = distance > mProximityThreshold
                mCurrentAirHeight = if (isAir) 0 else 6
                if (mEnableGridView) {
                    runOnUiThread { drawGrid(mLastButtons, mCurrentAirHeight) }
                }
                if (mDebugInfo && this@MainActivity::mCameraDebugText.isInitialized) {
                    val status = getString(if (isAir) R.string.note_air else R.string.note_normal)
                    runOnUiThread { mCameraDebugText.text = getString(R.string.proximity_debug, distance, status) }
                }
                return
            }
            
            if (isLightType) {
                if (mAirSource != 6) return
                val lux = event.values[0]
                val isAir = lux > mLightThreshold
                mCurrentAirHeight = if (isAir) 0 else 6
                if (mEnableGridView) {
                    runOnUiThread { drawGrid(mLastButtons, mCurrentAirHeight) }
                }
                if (mDebugInfo && this@MainActivity::mCameraDebugText.isInitialized) {
                    val status = getString(if (isAir) R.string.note_air else R.string.note_normal)
                    runOnUiThread { mCameraDebugText.text = getString(R.string.light_debug, lux, status) }
                }
                return
            }

            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (mAirSource != 2)
                        return
                    if (lastAcceleration != 0f) {
                        val accelX = (lastAcceleration - event.values[0])
                        if (accelX >= threshold && current >= 0)
                            current --
                        else if (accelX <= -threshold && current <= 0)
                            current ++
                        if (current > 0)
                            mCurrentAirHeight = 0
                        else if (current < 0)
                            mCurrentAirHeight = 6
                        
                        if (mEnableGridView) {
                            drawGrid(mLastButtons, mCurrentAirHeight)
                        }
                    }
                    lastAcceleration = event.values[0]
                }
                Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    if (mAirSource != 1)
                        return
                    val rotationVector = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationVector, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationVector, orientation)
                    mSensorCallback?.invoke(orientation[2])
                    
                    if (mEnableGridView) {
                        drawGrid(mLastButtons, mCurrentAirHeight)
                    }
                    return
                }
            }
        }
    }

    private fun Byte.getBit(bit: Int) = (toInt() ushr bit) and 0x1
    private fun MifareClassic.authenticateBlock(blockIndex: Int, keyA: ByteArray, keyB: ByteArray, write: Boolean = false): Boolean {
        val sectorIndex = blockToSector(blockIndex)
        val accessBitsBlock = sectorToBlock(sectorIndex) + 3
        if (!authenticateSectorWithKeyA(sectorIndex, keyA)) return false
        val accessBits = readBlock(accessBitsBlock)
        val targetBit = blockIndex % 4
        val bitC1 = accessBits[7].getBit(targetBit + 4)
        val bitC2 = accessBits[8].getBit(targetBit)
        val bitC3 = accessBits[8].getBit(targetBit + 4)
        val allBits = (bitC1 shl 2) or (bitC2 shl 1) or bitC3
        return if (write) {
            when (allBits) {
                0 -> true
                3, 4, 6 -> authenticateSectorWithKeyB(sectorIndex, keyB)
                else -> false
            }
        } else {
            when (allBits) {
                7 -> false
                3, 5 -> authenticateSectorWithKeyB(sectorIndex, keyB)
                else -> true
            }
        }
    }
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    enum class CardType {
        CARD_AIME, CARD_FELICA
    }
    private var adapter: NfcAdapter? = null
    private val mAimeKey = byteArrayOf(0x57, 0x43, 0x43, 0x46, 0x76, 0x32)
    private val mBanaKey = byteArrayOf(0x60, -0x70, -0x30, 0x06, 0x32, -0x0b)
    private var mEnableNFC = true
    private var hasCard = false
    private var cardType = CardType.CARD_AIME
    private val cardId = ByteArray(10)
    private var mEnableGridView = false
    private lateinit var mGridOverlay: ImageView
    private lateinit var mGridBitmap: Bitmap
    private lateinit var mGridCanvas: Canvas
    private val mGreenPaint = Paint().apply { color = Color.GREEN; strokeWidth = 2f; style = Paint.Style.STROKE }
    private val mPinkPaint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL }
    private val mCyanPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.FILL }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val felica = FeliCa.get(tag)
        if (felica != null) {
            thread {
                try {
                    felica.connect()
                    felica.poll()
                    felica.IDm?.copyInto(cardId) ?: throw IllegalStateException("Failed to fetch IDm from FeliCa")
                    cardId[8] = 0
                    cardId[9] = 0
                    cardType = CardType.CARD_FELICA
                    hasCard = true
                    Log.d(TAG, "Found FeliCa card: ${cardId.toHexString().removeRange(16..19)}")
                    while (felica.isConnected) Thread.sleep(50)
                    hasCard = false
                    felica.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }
        val mifare = MifareClassic.get(tag) ?: return
        thread {
            try {
                mifare.connect()
                if (mifare.authenticateBlock(2, keyA = mAimeKey, keyB = mAimeKey) ||
                    mifare.authenticateBlock(2, keyA = mBanaKey, keyB = mAimeKey)) {
                    Thread.sleep(100)
                    val block = mifare.readBlock(2)
                    block.copyInto(cardId, 0, 6, 16)
                    cardType = CardType.CARD_AIME
                    hasCard = true
                    Log.d(TAG, "Found Aime card: ${cardId.toHexString()}")
                    while (mifare.isConnected) Thread.sleep(50)
                    hasCard = false
                } else {
                    Log.d(TAG, "NFC auth failed")
                }
                mifare.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setImmersive()
        app = application as BrokenithmApplication
        vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val nfcManager = getSystemService(Context.NFC_SERVICE) as NfcManager
        adapter = nfcManager.defaultAdapter

        vibrateMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            {
                vibrator.vibrate(VibrationEffect.createOneShot(it, 255))
            }
        } else {
            {
                vibrator.vibrate(it)
            }
        }

        val settings = findViewById<Button>(R.id.button_settings)
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        mDelayText = findViewById(R.id.text_delay)
        mCameraDebugText = findViewById(R.id.text_camera_debug)
        mGridOverlay = findViewById(R.id.grid_overlay)

        val editServer = findViewById<EditText>(R.id.edit_server).apply {
            setText(app.lastServer.value())
        }
        findViewById<Button>(R.id.button_start).setOnClickListener {
            val server = editServer.text.toString()
            if (server.isBlank())
                return@setOnClickListener
            if (mExitFlag) {
                if (senderTask.isActive || receiverTask.isActive)
                    return@setOnClickListener
                mExitFlag = false
                (it as Button).setText(R.string.stop)
                editServer.isEnabled = false
                settings.isEnabled = false

                app.lastServer.update(server)
                val address = parseAddress(server)
                if (!mTCPMode)
                    sendConnect(address)
                currentPacketId = 1
                senderTask.execute(lifecycleScope, address)
                receiverTask.execute(lifecycleScope, address)
                pingPongTask.execute(lifecycleScope)
            } else {
                sendDisconnect(parseAddress(server))
                mExitFlag = true
                (it as Button).setText(R.string.start)
                editServer.isEnabled = true
                settings.isEnabled = true
                senderTask.cancel()
                receiverTask.cancel()
                pingPongTask.cancel()
            }
        }

        findViewById<Button>(R.id.button_coin).setOnClickListener {
            if(!mExitFlag)
                sendFunctionKey(parseAddress(editServer.text.toString()), FunctionButton.FUNCTION_COIN)
        }
        findViewById<Button>(R.id.button_card).setOnClickListener {
            if(!mExitFlag)
                sendFunctionKey(parseAddress(editServer.text.toString()), FunctionButton.FUNCTION_CARD)
        }

        val checkSimpleAir = findViewById<CheckBox>(R.id.check_simple_air)
        val textSwitchAir = findViewById<TextView>(R.id.text_switch_air)
        textSwitchAir.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        
        checkSimpleAir.setOnCheckedChangeListener { _, isChecked ->
            mSimpleAir = isChecked
            app.simpleAir.update(isChecked)
        }
        
        findViewById<View>(R.id.button_test).setOnTouchListener { view, event ->
            mTestButton = when(event.actionMasked) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> true
                else -> false
            }
            view.performClick()
        }
        findViewById<View>(R.id.button_service).setOnTouchListener { view, event ->
            mServiceButton = when(event.actionMasked) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> true
                else -> false
            }
            view.performClick()
        }

        findViewById<CheckBox>(R.id.check_show_delay).apply {
            setOnCheckedChangeListener { _, isChecked ->
                mShowDelay = isChecked
                mDelayText.visibility = if (isChecked) View.VISIBLE else View.GONE
                app.showDelay.update(isChecked)
            }
            isChecked = app.showDelay.value()
        }

        mTCPMode = app.tcpMode.value()
        findViewById<TextView>(R.id.text_mode).apply {
            text = getString(if (mTCPMode) R.string.tcp else R.string.udp)
            setOnClickListener {
                if (!mExitFlag)
                    return@setOnClickListener
                val modes = arrayOf(getString(R.string.udp), getString(R.string.tcp))
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Connection Mode")
                    .setItems(modes) { _, which ->
                        mTCPMode = which == 1
                        text = getString(if (mTCPMode) R.string.tcp else R.string.udp)
                        app.tcpMode.update(mTCPMode)
                    }
                    .show()
            }
        }
        initTasks()

        vibratorTask.execute(lifecycleScope)

        mSensorCallback = {
            val lowest = mGyroLowestBound
            val highest = mGyroHighestBound
            val steps = (highest - lowest) / numOfAirBlock
            val current = abs(it)
            mCurrentAirHeight = if (mSimpleAir) {
                if (current > lowest) 0 else 6
            } else {
                when (current) {
                    in 0f..lowest -> 6
                    in lowest..highest -> ((highest - current) / steps).toInt()
                    else -> 0
                }
            }
        }

        val contentView = findViewById<ViewGroup>(android.R.id.content)
        contentView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dm = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(dm)
                windowWidth = dm.widthPixels.toFloat()
                windowHeight = dm.heightPixels.toFloat()
                initTouchArea(0, 0)
            }
        })
    }

    private fun initTouchArea(windowLeft: Int, windowTop: Int) {
        val expandControl = findViewById<ExpandableLayout>(R.id.expand_control)
        val textExpand = findViewById<TextView>(R.id.text_expand)
        textExpand.setOnClickListener {
            if (expandControl.isExpanded) {
                (it as TextView).setText(R.string.expand)
                expandControl.collapse()
            } else {
                (it as TextView).setText(R.string.collapse)
                expandControl.expand()
            }
        }

        val textInfo = findViewById<TextView>(R.id.text_info)
        findViewById<CheckBox>(R.id.check_debug).setOnCheckedChangeListener { _, isChecked ->
            mDebugInfo = isChecked
            textInfo.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (this::mCameraDebugText.isInitialized) {
                mCameraDebugText.visibility = if (isChecked && (mAirSource == 4 || mAirSource == 5 || mAirSource == 6)) View.VISIBLE else View.GONE
            }
        }

        findViewById<TextView>(R.id.text_switch_air).setOnClickListener {
            val entries = resources.getStringArray(R.array.air_source_entries)
            val values = resources.getStringArray(R.array.air_source_values)
            val descriptions = arrayOf(
                getString(R.string.air_source_help_none),
                getString(R.string.air_source_help_gyro),
                getString(R.string.air_source_help_accel),
                getString(R.string.air_source_help_touch),
                getString(R.string.air_source_help_camera),
                getString(R.string.air_source_help_proximity),
                getString(R.string.air_source_help_light),
                getString(R.string.air_source_help_auto)
            )
            val items = entries.mapIndexed { i, s -> "$s\n(${descriptions[i]})" }.toTypedArray()
            
            val currentIndex = values.indexOf(mAirSource.toString()).coerceAtLeast(0)
            
            AlertDialog.Builder(this)
                .setTitle(R.string.air_source)
                .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                    app.airSource.update(values[which])
                    loadPreference()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        gapWidth = windowWidth / (numOfButtons * buttonWidthToGap + numOfGaps)
        buttonWidth = gapWidth * buttonWidthToGap
        buttonBlockWidth = buttonWidth + gapWidth
        mButtonRenderer = findViewById(R.id.button_render_area)
        updateThresholds()

        findViewById<View>(R.id.touch_area).setOnTouchListener { view, event ->
            if (expandControl.isExpanded) textExpand.callOnClick()
            view ?: return@setOnTouchListener true
            event ?: return@setOnTouchListener true
            if (mTouchAreaRect == null) {
                val arr = IntArray(2)
                view.getLocationOnScreen(arr)
                mTouchAreaRect = Rect(arr[0], arr[1], arr[0] + view.width, arr[1] + view.height)
            }
            val currentAirAreaHeight = if (mAirSource != 3) 0f else mAirAreaHeightBoundary
            val currentButtonAreaHeight = if (mAirSource != 3) 0f else (windowHeight - mButtonAreaHeight)
            // Disable touch event batching so simultaneous touches are never coalesced/dropped.
            // Critical for multi-touch rhythm game input on devices with aggressive batching (e.g. HyperOS).
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                view.requestUnbufferedDispatch(event)
            }
            val totalTouches = event.pointerCount

            val touchedButtons = HashSet<Int>()
            var thisAirHeight = 6
            var maxTouchedSize = 0f
            if (event.action != MotionEvent.ACTION_UP && event.action != MotionEvent.ACTION_CANCEL) {
                var ignoredIndex = -1
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP)
                    ignoredIndex = event.actionIndex
                for (i in 0 until totalTouches) {
                    if (i == ignoredIndex) continue
                    val x = event.getX(i) + mTouchAreaRect!!.left - windowLeft
                    val y = event.getY(i) + mTouchAreaRect!!.top - windowTop
                    when(y) {
                        in 0f..currentAirAreaHeight -> thisAirHeight = 0
                        in currentAirAreaHeight..currentButtonAreaHeight -> {
                            val curAir = ((y - mAirAreaHeightBoundary) / mAirBlockHeight).toInt()
                            thisAirHeight = if(mSimpleAir) 0 else thisAirHeight.coerceAtMost(curAir)
                        }
                        in currentButtonAreaHeight..windowHeight -> {
                            val blockWidth = windowWidth / numOfButtons
                            val pointPos = x / blockWidth
                            var index = pointPos.toInt().coerceIn(0, 15)
                            if (mEnableTouchSize) {
                                var centerButton = index * 2
                                if (touchedButtons.contains(centerButton)) centerButton++
                                var leftButton = ((index - 1) * 2).coerceAtLeast(0)
                                if (touchedButtons.contains(leftButton)) leftButton++
                                var rightButton = ((index + 1) * 2).coerceAtMost(31)
                                if (touchedButtons.contains(rightButton)) rightButton++
                                val currentSize = event.getSize(i)
                                maxTouchedSize = maxTouchedSize.coerceAtLeast(currentSize)
                                touchedButtons.add(centerButton)
                                when ((pointPos - index) * 4) {
                                    in 0f..1f -> {
                                        touchedButtons.add(leftButton)
                                        if (currentSize >= mExtraFatTouchSizeThreshold) {
                                            touchedButtons.add(((index - 2) * 2).coerceAtLeast(0))
                                            touchedButtons.add(rightButton)
                                        }
                                    }
                                    in 1f..3f -> {
                                        if (currentSize >= mFatTouchSizeThreshold) {
                                            touchedButtons.add(leftButton)
                                            touchedButtons.add(rightButton)
                                        }
                                        if (currentSize >= mExtraFatTouchSizeThreshold) {
                                            touchedButtons.add(((index - 2) * 2).coerceAtLeast(0))
                                            touchedButtons.add(((index + 2) * 2).coerceAtMost(31))
                                        }
                                    }
                                    else -> {
                                        touchedButtons.add(rightButton)
                                        if (currentSize >= mExtraFatTouchSizeThreshold) {
                                            touchedButtons.add(leftButton)
                                            touchedButtons.add(((index + 2) * 2).coerceAtMost(31))
                                        }
                                    }
                                }
                            } else {
                                var targetIndex = index * 2
                                if (touchedButtons.contains(targetIndex)) targetIndex++
                                touchedButtons.add(targetIndex)
                                if ((pointPos - index) * 4 < 1 && index > 0) {
                                    var left = (index - 1) * 2
                                    if (touchedButtons.contains(left)) left++
                                    touchedButtons.add(left)
                                } else if ((pointPos - index) * 4 > 3 && index < 15) {
                                    var right = (index + 1) * 2
                                    if (touchedButtons.contains(right)) right++
                                    touchedButtons.add(right)
                                }
                            }
                        }
                    }
                }
            } else thisAirHeight = 6
            
            if (mEnableVibrate) {
                if (hasNewKeys(mLastButtons, touchedButtons)) mVibrationQueue.add(vibrateLength)
                else if (touchedButtons.isEmpty()) mVibrationQueue.clear()
            }
            mLastButtons = touchedButtons
            if (mAirSource == 3) mCurrentAirHeight = thisAirHeight
            else if (mAirSource == 7) mCurrentAirHeight = if (touchedButtons.isEmpty()) 0 else 6
            
            if (mEnableGridView) drawGrid(touchedButtons, mCurrentAirHeight)
            if (mDebugInfo) textInfo.text = getString(R.string.debug_info, mCurrentAirHeight, touchedButtons.toString(), maxTouchedSize, event.toString())
            view.performClick()
            return@setOnTouchListener true
        }
    }

    override fun onResume() {
        super.onResume()
        if (mSensorManager == null) mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        mSensorManager?.let { sm ->
            val gyro = sm.getDefaultSensor(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) Sensor.TYPE_GAME_ROTATION_VECTOR else Sensor.TYPE_ROTATION_VECTOR)
            sm.registerListener(listener, gyro, 10000)
            
            val accel = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            sm.registerListener(listener, accel, 10000)
            
            val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (proximity != null) {
                sm.registerListener(listener, proximity, 10000)
            } else {
                sm.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
                    if (sensor.name.lowercase().contains("proximity")) {
                        sm.registerListener(listener, sensor, 10000)
                    }
                }
            }

            val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
            if (light != null) {
                sm.registerListener(listener, light, 10000)
            } else {
                sm.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
                    if (sensor.name.lowercase().contains("light")) {
                        sm.registerListener(listener, sensor, 10000)
                    }
                }
            }
        }

        enableNfcForegroundDispatch()
        loadPreference()
    }

    private fun initCamera() {
        if (mAirSource != 4 || mCamera != null) return
        thread {
            try {
                var camId = -1
                val n = Camera.getNumberOfCameras()
                for (i in 0 until n) {
                    val info = Camera.CameraInfo()
                    Camera.getCameraInfo(i, info)
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { camId = i; break }
                }
                if (camId == -1) camId = 0
                val cam = Camera.open(camId)
                mCamera = cam
                mSurfaceTexture = SurfaceTexture(10)
                cam.setPreviewTexture(mSurfaceTexture)
                cam.setPreviewCallback { data, _ ->
                    if (mAirSource != 4 || data == null) return@setPreviewCallback
                    var total = 0L
                    val samples = 1000
                    val skip = (data.size / samples).coerceAtLeast(1)
                    for (i in 0 until samples) {
                        val idx = i * skip
                        if (idx < data.size) total += data[idx].toInt() and 0xFF
                    }
                    val avg = total / samples
                    val isAir = avg > mCameraBrightnessThreshold
                    val newHeight = if (isAir) 0 else 6
                    if (mDebugInfo) {
                        val status = getString(if (isAir) R.string.note_air else R.string.note_normal)
                        runOnUiThread { mCameraDebugText.text = getString(R.string.camera_debug, avg.toInt(), status) }
                    }
                    if (newHeight != mCurrentAirHeight) {
                        mCurrentAirHeight = newHeight
                        if (mEnableGridView) runOnUiThread { drawGrid(mLastButtons, mCurrentAirHeight) }
                    }
                }
                cam.startPreview()
            } catch (e: Exception) { e.printStackTrace(); mCamera = null }
        }
    }

    private fun stopCamera() {
        mCamera?.setPreviewCallback(null)
        try { mCamera?.stopPreview() } catch (e: Exception) {}
        mCamera?.release()
        mCamera = null
        mSurfaceTexture?.release()
        mSurfaceTexture = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == mCameraRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) initCamera()
    }

    private fun loadPreference() {
        mEnableTouchSize = app.enableTouchSize.value()
        mFatTouchSizeThreshold = app.fatTouchThreshold.value()
        mExtraFatTouchSizeThreshold = app.extraFatTouchThreshold.value()
        mEnableNFC = app.enableNFC.value()
        mEnableVibrate = app.enableVibrate.value()
        mGyroLowestBound = app.gyroAirLowestBound.value()
        mGyroHighestBound = app.gyroAirHighestBound.value()
        mAccelThreshold = app.accelAirThreshold.value()
        mProximityThreshold = app.proximityAirThreshold.value()
        mLightThreshold = app.lightAirThreshold.value()
        mCameraBrightnessThreshold = app.cameraAirThreshold.value().toFloat()
        mEnableGridView = app.enableGridView.value()
        findViewById<ImageView>(R.id.grid_overlay)?.visibility = if (mEnableGridView) View.VISIBLE else View.GONE

        val oldAirSource = mAirSource
        mAirSource = app.airSource.value().toIntOrNull() ?: 0
        mEnableAir = mAirSource != 0
        
        if (oldAirSource != mAirSource) {
            stopCamera()
            if (mAirSource == 4) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) initCamera()
                else ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), mCameraRequestCode)
            }
        }

        findViewById<CheckBox>(R.id.check_simple_air)?.isEnabled = (mAirSource == 1 || mAirSource == 3)
        if (this::mCameraDebugText.isInitialized) {
            mCameraDebugText.visibility = if (mDebugInfo && (mAirSource == 4 || mAirSource == 5 || mAirSource == 6)) View.VISIBLE else View.GONE
        }
        
        findViewById<TextView>(R.id.text_switch_air)?.text = getString(when (mAirSource) {
            0 -> R.string.disable_air
            1 -> R.string.gyro_air
            2 -> R.string.accel_air
            3 -> R.string.touch_air
            4 -> R.string.camera_air
            5 -> R.string.proximity_air
            6 -> R.string.light_air
            else -> R.string.auto_air
        })

        if (windowHeight > 0) updateThresholds()
    }

    private fun updateThresholds() {
        if (windowWidth <= 0f || windowHeight <= 0f) return
        val threshold = app.airLineThresholdInt.value() / 100f
        mButtonAreaHeight = windowHeight * (1.0f - threshold)
        mAirAreaHeightBoundary = 0f
        val buttonAreaTop = windowHeight * threshold
        mAirBlockHeight = (buttonAreaTop / numOfAirBlock).coerceAtLeast(1f)
        val buttonGuideline = findViewById<androidx.constraintlayout.widget.Guideline>(R.id.button_area_upper)
        val buttonParams = buttonGuideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        buttonParams.guidePercent = threshold
        buttonGuideline.layoutParams = buttonParams
        mTouchAreaRect = null
        val newHeight = mButtonAreaHeight.toInt().coerceAtLeast(1)
        if (!::mLEDBitmap.isInitialized || mLEDBitmap.height != newHeight) {
            mLEDBitmap = Bitmap.createBitmap(windowWidth.toInt(), newHeight, Bitmap.Config.RGB_565)
            mLEDCanvas = Canvas(mLEDBitmap)
            mButtonRenderer.background = BitmapDrawable(resources, mLEDBitmap)
        }
        if (mEnableGridView) {
            mGridBitmap = Bitmap.createBitmap(windowWidth.toInt(), windowHeight.toInt(), Bitmap.Config.ARGB_8888)
            mGridCanvas = Canvas(mGridBitmap)
            mGridOverlay.setImageBitmap(mGridBitmap)
            drawGrid(HashSet(), 6)
        }
    }

    private fun drawGrid(touchedButtons: HashSet<Int>, airHeight: Int) {
        if (!mEnableGridView || !::mGridCanvas.isInitialized) return
        mGridBitmap.eraseColor(Color.TRANSPARENT)
        if (airHeight < 6) {
            val top = mAirAreaHeightBoundary + (airHeight * mAirBlockHeight)
            mGridCanvas.drawRect(0f, top, windowWidth, top + mAirBlockHeight, mCyanPaint)
        }
        val buttonAreaTop = windowHeight - mButtonAreaHeight
        val blockWidth = windowWidth / numOfButtons
        for (btn in touchedButtons) {
            val left = (btn / 2) * blockWidth
            mGridCanvas.drawRect(left, buttonAreaTop, left + blockWidth, windowHeight, mPinkPaint)
        }
        for (i in 0..numOfAirBlock) {
            val y = mAirAreaHeightBoundary + (i * mAirBlockHeight)
            mGridCanvas.drawLine(0f, y, windowWidth, y, mGreenPaint)
        }
        mGridCanvas.drawLine(0f, buttonAreaTop, windowWidth, buttonAreaTop, mGreenPaint)
        for (i in 0..numOfButtons) {
            val x = i * blockWidth
            mGridCanvas.drawLine(x, buttonAreaTop, x, windowHeight, mGreenPaint)
        }
        mGridOverlay.postInvalidate()
    }

    private fun enableNfcForegroundDispatch() {
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
            adapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
        } catch (ex: IllegalStateException) { Log.e(TAG, "Error enabling NFC foreground dispatch", ex) }
    }

    private fun disableNfcForegroundDispatch() {
        try { adapter?.disableForegroundDispatch(this) }
        catch (ex: IllegalStateException) { Log.e(TAG, "Error disabling NFC foreground dispatch", ex) }
    }

    override fun onPause() {
        disableNfcForegroundDispatch()
        mSensorManager?.unregisterListener(listener)
        stopCamera()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setImmersive()
    }

    private var exitTime: Long = 0
    override fun onBackPressed() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - exitTime > 1500) {
            Toast.makeText(this, R.string.press_again_to_exit, Toast.LENGTH_SHORT).show()
            exitTime = currentTime
        } else finish()
    }

    private fun setImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun hasNewKeys(oldKeys: MutableSet<Int>, newKeys: MutableSet<Int>): Boolean {
        for (i in newKeys) if (!oldKeys.contains(i)) return true
        return false
    }

    private fun parseAddress(address: String): InetSocketAddress? {
        val parts = address.split(":")
        return when(parts.size) {
            1 -> InetSocketAddress(parts[0], serverPort)
            2 -> InetSocketAddress(parts[0], parts[1].toInt())
            else -> null
        }
    }

    private fun Char.byte() = code.toByte()
    private fun initTasks() {
        receiverTask = AsyncTaskUtil.AsyncTask.make(doInBackground = {
            val address = it[0] ?: return@make
            val buffer = ByteArray(256)
            if (mTCPMode) {
                while (!mExitFlag) {
                    if (!this::mTCPSocket.isInitialized || !mTCPSocket.isConnected || mTCPSocket.isClosed) { Thread.sleep(50); continue }
                    try {
                        val dataSize = mTCPSocket.getInputStream().read(buffer, 0, 256)
                        if (dataSize >= 3) {
                            if (dataSize >= 100 && buffer[1] == 'L'.byte() && buffer[2] == 'E'.byte() && buffer[3] == 'D'.byte()) setLED(buffer)
                            if (dataSize >= 4 && buffer[1] == 'P'.byte() && buffer[2] == 'O'.byte() && buffer[3] == 'N'.byte()) {
                                val delay = calculateDelay(buffer)
                                if (delay > 0f) mCurrentDelay = delay
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else {
                val socket = try { DatagramSocket(serverPort).apply { reuseAddress = true; soTimeout = 1000 } } catch (e: BindException) { e.printStackTrace(); return@make }
                val packet = DatagramPacket(buffer, buffer.size)
                while (!mExitFlag) {
                    try {
                        socket.receive(packet)
                        val host = address.hostName ?: address.address?.hostAddress
                        if (packet.address.hostAddress == host && packet.port == address.port) {
                            val data = packet.data
                            if (data.size >= 3) {
                                if (data.size >= 100 && data[1] == 'L'.byte() && data[2] == 'E'.byte() && data[3] == 'D'.byte()) setLED(data)
                                if (data.size >= 4 && data[1] == 'P'.byte() && data[2] == 'O'.byte() && data[3] == 'N'.byte()) {
                                    val delay = calculateDelay(data)
                                    if (delay > 0f) mCurrentDelay = delay
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {}
                }
                socket.close()
            }
        })
        senderTask = AsyncTaskUtil.AsyncTask.make(doInBackground = {
            val address = it[0] ?: return@make
            if (mTCPMode) {
                try { mTCPSocket = Socket().apply { tcpNoDelay = true }; mTCPSocket.connect(address) } catch (e: Exception) { e.printStackTrace(); return@make }
                while (!mExitFlag) {
                    if (mShowDelay) sendTCPPing()
                    val buttons = InputEvent(mLastButtons, mCurrentAirHeight, mTestButton, mServiceButton)
                    val buffer = applyKeys(buttons, IoBuffer())
                    try {
                        mTCPSocket.getOutputStream().write(constructBuffer(buffer))
                        if (mEnableNFC) mTCPSocket.getOutputStream().write(constructCardData())
                    } catch (e: Exception) { e.printStackTrace(); continue }
                    Thread.sleep(1)
                }
            } else {
                val socket = try { DatagramSocket().apply { reuseAddress = true; soTimeout = 1000 } } catch (e: BindException) { e.printStackTrace(); return@make }
                try { socket.connect(address) } catch (e: Exception) { e.printStackTrace(); return@make }
                while (!mExitFlag) {
                    if (mShowDelay) sendPing(address)
                    val buttons = InputEvent(mLastButtons, mCurrentAirHeight, mTestButton, mServiceButton)
                    val buffer = applyKeys(buttons, IoBuffer())
                    val packet = constructPacket(buffer)
                    try {
                        socket.send(packet)
                        if (mEnableNFC) socket.send(constructCardPacket())
                    } catch (e: Exception) { e.printStackTrace(); Thread.sleep(100); continue }
                    Thread.sleep(1)
                }
                socket.close()
            }
        })
        pingPongTask = AsyncTaskUtil.AsyncTask.make(doInBackground = {
            while (!mExitFlag) {
                if (!mShowDelay) { Thread.sleep(250); continue }
                if (mCurrentDelay >= 0f) runOnUiThread { mDelayText.text = getString(R.string.current_latency, mCurrentDelay) }
                Thread.sleep(200)
            }
        })
        vibratorTask = AsyncTaskUtil.AsyncTask.make(doInBackground = {
            while (true) {
                if (!mEnableVibrate) { Thread.sleep(250); continue }
                val next = mVibrationQueue.poll()
                if (next != null) vibrateMethod(next)
                Thread.sleep(10)
            }
        })
    }

    enum class FunctionButton { UNDEFINED, FUNCTION_COIN, FUNCTION_CARD }
    class IoBuffer { var length: Int = 0; var header = ByteArray(3); var air = ByteArray(6); var slider = ByteArray(32); var testBtn = false; var serviceBtn = false }

    private fun getLocalIPAddress(): ByteArray {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.address
                }
            }
        } catch (e: Exception) {}
        return byteArrayOf()
    }

    private fun sendConnect(address: InetSocketAddress?) {
        address ?: return
        thread {
            val selfAddress = getLocalIPAddress()
            if (selfAddress.isEmpty()) return@thread
            val buffer = ByteArray(21)
            byteArrayOf('C'.byte(), 'O'.byte(), 'N'.byte()).copyInto(buffer, 1)
            ByteBuffer.wrap(buffer).put(4, 1).putShort(5, serverPort.toShort())
            selfAddress.copyInto(buffer, 7)
            buffer[0] = (3 + 1 + 2 + selfAddress.size).toByte()
            try { DatagramSocket().apply { connect(address); send(DatagramPacket(buffer, buffer.size)); close() } } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun sendDisconnect(address: InetSocketAddress?) {
        address ?: return
        thread {
            val buffer = byteArrayOf(3, 'D'.byte(), 'I'.byte(), 'S'.byte())
            if (mTCPMode) { try { mTCPSocket.close() } catch (e: Exception) {} }
            else { try { DatagramSocket().apply { connect(address); send(DatagramPacket(buffer, buffer.size)); close() } } catch (e: Exception) {} }
        }
    }

    private fun sendFunctionKey(address: InetSocketAddress?, function: FunctionButton) {
        address ?: return
        thread {
            val buffer = byteArrayOf(4, 'F'.byte(), 'N'.byte(), 'C'.byte(), function.ordinal.toByte())
            if (mTCPMode) { try { mTCPSocket.getOutputStream().write(buffer) } catch (e: Exception) {} }
            else { try { DatagramSocket().apply { connect(address); send(DatagramPacket(buffer, buffer.size)); close() } } catch (e: Exception) {} }
        }
    }

    private var lastPingTime = 0L
    private fun sendPing(address: InetSocketAddress?) {
        address ?: return
        if (System.currentTimeMillis() - lastPingTime < 100L) return
        lastPingTime = System.currentTimeMillis()
        val buffer = ByteArray(12)
        byteArrayOf(11, 'P'.byte(), 'I'.byte(), 'N'.byte()).copyInto(buffer)
        ByteBuffer.wrap(buffer, 4, 8).putLong(SystemClock.elapsedRealtimeNanos())
        try { DatagramSocket().apply { connect(address); send(DatagramPacket(buffer, buffer.size)); close() } } catch (e: Exception) {}
    }

    private fun sendTCPPing() {
        if (System.currentTimeMillis() - lastPingTime < 100L) return
        lastPingTime = System.currentTimeMillis()
        val buffer = ByteArray(12)
        byteArrayOf(11, 'P'.byte(), 'I'.byte(), 'N'.byte()).copyInto(buffer)
        ByteBuffer.wrap(buffer, 4, 8).putLong(SystemClock.elapsedRealtimeNanos())
        try { mTCPSocket.getOutputStream().write(buffer) } catch (e: Exception) {}
    }

    private fun calculateDelay(data: ByteArray): Float = (SystemClock.elapsedRealtimeNanos() - ByteBuffer.wrap(data).getLong(4)) / 2000000.0f
    private var currentPacketId = 1
    private fun constructBuffer(buffer: IoBuffer): ByteArray {
        val realBuf = ByteArray(48)
        realBuf[0] = buffer.length.toByte()
        buffer.header.copyInto(realBuf, 1)
        ByteBuffer.wrap(realBuf).putInt(4, currentPacketId++)
        if (mEnableAir) {
            buffer.air.copyInto(realBuf, 8); buffer.slider.copyInto(realBuf, 14)
            realBuf[46] = if (buffer.testBtn) 0x01 else 0x00; realBuf[47] = if (buffer.serviceBtn) 0x01 else 0x00
        } else {
            buffer.slider.copyInto(realBuf, 8)
            realBuf[40] = if (buffer.testBtn) 0x01 else 0x00; realBuf[41] = if (buffer.serviceBtn) 0x01 else 0x00
        }
        return realBuf
    }
    private fun constructPacket(buffer: IoBuffer): DatagramPacket { val b = constructBuffer(buffer); return DatagramPacket(b, buffer.length + 1) }
    private fun constructCardData(): ByteArray {
        val buf = ByteArray(24); byteArrayOf(15, 'C'.byte(), 'R'.byte(), 'D'.byte()).copyInto(buf)
        buf[4] = if (hasCard) 1 else 0; buf[5] = cardType.ordinal.toByte()
        if (hasCard) cardId.copyInto(buf, 6)
        return buf
    }
    private fun constructCardPacket(): DatagramPacket { val b = constructCardData(); return DatagramPacket(b, b[0] + 1) }
    private var mLastAirHeight = 6
    private var mLastAirUpdateTime = 0L
    private fun applyKeys(event: InputEvent, buffer: IoBuffer): IoBuffer {
        return buffer.apply {
            if (mEnableAir) { buffer.length = 47; header = byteArrayOf('I'.byte(), 'N'.byte(), 'P'.byte()) }
            else { buffer.length = 41; header = byteArrayOf('I'.byte(), 'P'.byte(), 'T'.byte()) }
            if (event.keys != null) for (i in 0 until 32) slider[31 - i] = if (event.keys.contains(i)) 0x80.toByte() else 0x0
            if (mEnableAir) {
                val now = System.currentTimeMillis()
                if (now - mLastAirUpdateTime > 10L) {
                    mLastAirHeight += if (mLastAirHeight < mCurrentAirHeight) 1 else if (mLastAirHeight > mCurrentAirHeight) -1 else 0
                    mLastAirUpdateTime = now
                }
                if (mLastAirHeight != 6) for (i in mLastAirHeight..5) buffer.air[mAirIdx[i]] = 1
            }
            serviceBtn = event.serviceButton; testBtn = event.testButton
        }
    }

    private fun setLED(status: ByteArray) {
        val blockCount = numOfButtons + numOfGaps; val steps = 32 / blockCount
        var drawXOffset = 0f; val h = mLEDBitmap.height
        for (i in (blockCount - 1).downTo(0)) {
            val idx = 4 + (i * steps * 3)
            val b = status[idx].toInt() and 0xff; val r = status[idx + 1].toInt() and 0xff; val g = status[idx + 2].toInt() and 0xff
            val color = 0xff000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            val w = if (i % 2 == 0) buttonWidth else gapWidth
            mLEDCanvas.drawRect(drawXOffset, 0f, drawXOffset + w, h.toFloat(), Paint().apply { this.color = color.toInt() })
            drawXOffset += w
        }
        mButtonRenderer.postInvalidate()
    }

    companion object { private const val TAG = "Brokenithm" }
}