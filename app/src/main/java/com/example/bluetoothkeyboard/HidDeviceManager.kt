package com.example.bluetoothkeyboard

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bluetoothkeyboard.KeyboardReport.KeyboardDataSender
import java.util.concurrent.Executor

/**
 * HID 设备管理器
 * 负责注册 HID 服务、管理连接状态和发送数据
 */
class HidDeviceManager private constructor() : KeyboardDataSender {

    companion object {
        private const val TAG = "HidDeviceManager"

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

    private var inputHost: BluetoothHidDevice? = null
    private var device: BluetoothDevice? = null
    private var isRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<HidDeviceCallback>()
    private val keyboardReport = KeyboardReport()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App status changed: registered=$registered")
            isRegistered = registered
            mainHandler.post {
                callbacks.forEach { it.onAppRegistered(registered) }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "Connection state changed: device=${device.name}, state=$state")
            mainHandler.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        this@HidDeviceManager.device = device
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (this@HidDeviceManager.device?.address == device.address) {
                            this@HidDeviceManager.device = null
                        }
                    }
                }
                callbacks.forEach { it.onConnectionStateChanged(device, state) }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "Get report: type=$type, id=$id")
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                if (id == Constants.ID_KEYBOARD) {
                    val report = keyboardReport.getReport()
                    inputHost?.replyReport(device, type, id, report)
                } else {
                    inputHost?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                }
            } else {
                inputHost?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            Log.d(TAG, "Set report: type=$type, id=$id")
            inputHost?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "HID service connected, profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                inputHost = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "HID service disconnected")
            if (profile == BluetoothProfile.HID_DEVICE) {
                isRegistered = false
                inputHost = null
                device = null
            }
        }
    }

    fun initialize(context: Context, callback: HidDeviceCallback) {
        callbacks.add(callback)
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter
        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun release(context: Context, callback: HidDeviceCallback) {
        callbacks.remove(callback)
        if (callbacks.isEmpty()) {
            unregisterApp()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as android.bluetooth.BluetoothManager
            val adapter = bluetoothManager.adapter
            inputHost?.let { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
            inputHost = null
            device = null
        }
    }

    /**
     * 注册 HID 应用
     * 使用 API 34 的正确签名
     */
    private fun registerApp() {
        // 创建 SDP 设置
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Bluetooth Keyboard",           // 名称
            "Android HID Keyboard",         // 描述
            "Android",                       // 提供商
            BluetoothHidDevice.SUBCLASS1_KEYBOARD, // 子类
            Constants.SDP_RECORD             // HID 描述符
        )

        // 创建 QoS 设置
        val qosSettings = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_TYPE_BEST_EFFORT,
            0, 0, 0, 0, 0
        )

        inputHost?.registerApp(
            sdpSettings,
            qosSettings,
            qosSettings,
            Executor { it.run() },
            callback
        )
        Log.d(TAG, "registerApp called")
    }

    private fun unregisterApp() {
        if (isRegistered && inputHost != null) {
            inputHost?.unregisterApp()
        }
    }

    fun connect(device: BluetoothDevice): Boolean {
        Log.d(TAG, "Connecting to device: ${device.name}")
        return inputHost?.connect(device) ?: false
    }

    fun disconnect(device: BluetoothDevice) {
        inputHost?.disconnect(device)
    }

    fun getConnectedDevices(): List<BluetoothDevice> {
        return inputHost?.connectedDevices ?: emptyList()
    }

    fun isConnected(): Boolean = device != null

    fun getCurrentDevice(): BluetoothDevice? = device

    /**
     * 发送键盘报告
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
        val hid = inputHost
        val dev = device

        if (hid != null && dev != null) {
            val report = keyboardReport.setValue(modifier, key1, key2, key3, key4, key5, key6)
            try {
                hid.sendReport(dev, Constants.ID_KEYBOARD.toInt(), report)
                Log.d(TAG, "sendReport: modifier=$modifier, key1=$key1")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send report", e)
            }
        } else {
            Log.w(TAG, "sendKeyboard: not connected (hid=$hid, device=$dev)")
        }
    }
}
