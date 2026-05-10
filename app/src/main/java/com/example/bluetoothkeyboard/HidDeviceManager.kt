package com.example.bluetoothkeyboard

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * HID 设备管理器
 * 使用与成功项目完全一致的实现方式
 */
class HidDeviceManager private constructor() : KeyboardDataSender {

    companion object {
        private const val TAG = "HidDeviceManager"
        const val KEYBOARD_NAME = "Bluetooth Keyboard"
        const val DESCRIPTION = "Android HID Keyboard"
        const val PROVIDER = "Android"

        @Volatile
        private var instance: HidDeviceManager? = null

        fun getInstance(): HidDeviceManager {
            return instance ?: synchronized(this) {
                instance ?: HidDeviceManager().also { instance = it }
            }
        }
    }

    interface HidDeviceCallback {
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onAppRegistered(success: Boolean)
    }

    private var mHidDevice: BluetoothHidDevice? = null
    private var mHostDevice: BluetoothDevice? = null
    private var isRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<HidDeviceCallback>()

    private val mCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
            isRegistered = registered
            mainHandler.post {
                callbacks.forEach { it.onAppRegistered(registered) }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device.name}, state=$state")
            mainHandler.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        mHostDevice = device
                        Log.d(TAG, "HID connected to: ${device.name}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (mHostDevice?.address == device.address) {
                            mHostDevice = null
                        }
                        Log.d(TAG, "HID disconnected")
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Log.d(TAG, "HID connecting...")
                    }
                }
                callbacks.forEach { it.onConnectionStateChanged(device, state) }
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                if (proxy is BluetoothHidDevice) {
                    mHidDevice = proxy
                    Log.d(TAG, "HID proxy received")
                    registerApp()
                } else {
                    Log.e(TAG, "Proxy is not BluetoothHidDevice")
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                isRegistered = false
                mHidDevice = null
                mHostDevice = null
            }
        }
    }

    fun initialize(context: Context, callback: HidDeviceCallback) {
        callbacks.add(callback)
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }
        
        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun release(context: Context, callback: HidDeviceCallback) {
        callbacks.remove(callback)
        if (callbacks.isEmpty()) {
            unregisterApp()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as android.bluetooth.BluetoothManager
            val adapter = bluetoothManager.adapter
            mHidDevice?.let { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
            mHidDevice = null
            mHostDevice = null
        }
    }

    /**
     * 注册 HID 应用
     * 使用与成功项目完全一致的方式：5 参数版本，QoS 传 null
     */
    private fun registerApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            KEYBOARD_NAME,
            DESCRIPTION,
            PROVIDER,
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            Constants.KEYBOARD_DESCRIPTOR
        )

        // 使用成功项目的方式：QoS 参数传 null
        mHidDevice?.registerApp(
            sdpSettings,
            null,  // inQos = null
            null,  // outQos = null
            Executors.newCachedThreadPool(),
            mCallback
        )
        Log.d(TAG, "registerApp called with 5-arg API, QoS=null")
    }

    private fun unregisterApp() {
        if (isRegistered && mHidDevice != null) {
            mHidDevice?.unregisterApp()
        }
    }

    fun connect(device: BluetoothDevice): Boolean {
        Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")
        return mHidDevice?.connect(device) ?: false
    }

    fun disconnect(device: BluetoothDevice) {
        mHidDevice?.disconnect(device)
    }

    fun isConnected(): Boolean = mHostDevice != null

    fun getCurrentDevice(): BluetoothDevice? = mHostDevice

    /**
     * 发送键盘报告
     * 使用与成功项目完全一致的方式
     */
    override fun sendKeyboard(
        modifier: Int,
        key1: Int,
        key2: Int,
        key3: Int,
        key4: Int,
        key5: Int,
        key6: Int
    ) {
        val hid = mHidDevice
        val device = mHostDevice

        if (hid != null && device != null) {
            // 构造 8 字节报告
            val report = byteArrayOf(
                modifier.toByte(),  // 修饰键
                0,                  // 保留字节
                key1.toByte(),      // 按键 1
                key2.toByte(),      // 按键 2
                key3.toByte(),      // 按键 3
                key4.toByte(),      // 按键 4
                key5.toByte(),      // 按键 5
                key6.toByte()       // 按键 6
            )
            
            try {
                // 使用 Report ID = 8
                hid.sendReport(device, Constants.ID_KEYBOARD, report)
                Log.d(TAG, "sendReport: id=${Constants.ID_KEYBOARD}, modifier=$modifier, key1=$key1")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send report", e)
            }
        } else {
            Log.w(TAG, "sendKeyboard: not connected (hid=$hid, device=$device)")
        }
    }
}
