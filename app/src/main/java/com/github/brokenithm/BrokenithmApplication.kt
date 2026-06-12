package com.github.brokenithm

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class BrokenithmApplication : Application() {

    abstract class BasePreference<T>(context: Context, fileName: String) {
        protected val config: SharedPreferences = context.getSharedPreferences(fileName, MODE_PRIVATE)
        abstract fun value(): T
        abstract fun update(value: T)
    }

    abstract class Settings<T>(context: Context) : BasePreference<T>(context, settings_preference)

    open class StringPreference(
        context: Context,
        private val key: String,
        private val defValue: String
    ) : Settings<String>(context) {
        override fun value(): String {
            return try {
                config.getString(key, defValue) ?: defValue
            } catch (e: ClassCastException) {
                try {
                    config.getInt(key, defValue.toIntOrNull() ?: 0).toString()
                } catch (e: ClassCastException) {
                    defValue
                }
            }
        }
        override fun update(value: String) = config.edit().putString(key, value).apply()
    }

    open class BooleanPreference(
        context: Context,
        private val key: String,
        private val defValue: Boolean
    ) : Settings<Boolean>(context) {
        override fun value() = config.getBoolean(key, defValue)
        override fun update(value: Boolean) = config.edit().putBoolean(key, value).apply()
    }

    open class IntegerPreference(
        context: Context,
        private val key: String,
        private val defValue: Int
    ) : Settings<Int>(context) {
        override fun value(): Int {
            return try {
                config.getInt(key, defValue)
            } catch (e: ClassCastException) {
                config.getString(key, defValue.toString())?.toInt() ?: defValue
            }
        }
        override fun update(value: Int) = config.edit().putInt(key, value).apply()
    }

    open class FloatPreference(
        context: Context,
        private val key: String,
        private val defValue: Float
    ) : Settings<Float>(context) {
        override fun value() = config.getString(key, defValue.toString())?.toFloat() ?: defValue
        override fun update(value: Float) = config.edit().putString(key, value.toString()).apply()
    }

    lateinit var lastServer : StringPreference
    lateinit var enableAir : BooleanPreference
    lateinit var airSource : StringPreference
    lateinit var simpleAir : BooleanPreference
    lateinit var showDelay : BooleanPreference
    lateinit var enableVibrate : BooleanPreference
    lateinit var tcpMode : BooleanPreference
    lateinit var enableNFC : BooleanPreference
    lateinit var wideTouchRange : BooleanPreference
    lateinit var enableTouchSize : BooleanPreference
    lateinit var fatTouchThreshold : FloatPreference
    lateinit var extraFatTouchThreshold : FloatPreference
    lateinit var gyroAirLowestBound : FloatPreference
    lateinit var gyroAirHighestBound : FloatPreference
    lateinit var accelAirThreshold : FloatPreference
    lateinit var airLineThresholdInt : IntegerPreference
    lateinit var enableGridView : BooleanPreference
    lateinit var cameraAirThreshold : IntegerPreference
    lateinit var proximityAirThreshold : FloatPreference
    lateinit var lightAirThreshold : FloatPreference

    override fun onCreate() {
        super.onCreate()
        val config = getSharedPreferences(settings_preference, MODE_PRIVATE)
        try {
            config.getString("air_source", "3")
        } catch (e: ClassCastException) {
            val value = config.getInt("air_source", 3)
            config.edit().putString("air_source", value.toString()).apply()
        }
        lastServer = StringPreference(this, "server", "")
        enableAir = BooleanPreference(this, "enable_air", true)
        airSource = StringPreference(this, "air_source", "3")
        simpleAir = BooleanPreference(this, "simple_air", false)
        showDelay = BooleanPreference(this, "show_delay", false)
        enableVibrate = BooleanPreference(this, "enable_vibrate", true)
        tcpMode = BooleanPreference(this, "tcp_mode", false)
        enableNFC = BooleanPreference(this, "enable_nfc", true)
        wideTouchRange = BooleanPreference(this, "wide_touch_range", false)
        enableTouchSize = BooleanPreference(this, "enable_touch_size", false)
        fatTouchThreshold = FloatPreference(this, "fat_touch_threshold", 0.027f)
        extraFatTouchThreshold = FloatPreference(this, "extra_fat_touch_threshold", 0.035f)
        gyroAirLowestBound = FloatPreference(this, "gyro_air_lowest", 0.8f)
        gyroAirHighestBound = FloatPreference(this, "gyro_air_highest", 1.35f)
        accelAirThreshold = FloatPreference(this, "accel_air_threshold", 2f)
        airLineThresholdInt = IntegerPreference(this, "air_line_threshold_int", 50)
        enableGridView = BooleanPreference(this, "enable_grid_view", false)
        cameraAirThreshold = IntegerPreference(this, "camera_air_threshold", 50)
        proximityAirThreshold = FloatPreference(this, "proximity_air_threshold", 0.5f)
        lightAirThreshold = FloatPreference(this, "light_air_threshold", 50f)
    }

    companion object {
        private const val settings_preference = "settings"
    }
}