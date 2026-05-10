package com.example.bluetoothkeyboard

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bluetoothkeyboard.KeyboardReport.KeyboardDataSender

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

    // HID 设备回调接口
    interface HidDeviceCallback {
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onAppRegistered(success: Boolean)
    }

    private var hidDevice: BluetoothHidDevice? = null
    private var currentDevice: BluetoothDevice? = null
    private var isRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<HidDeviceCallback>()
    private val keyboardReport = KeyboardReport()

    /**
     * HID 设备回调
     */
    private val hidCallback = object : BluetoothHidDevice.Callback() {
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
                        currentDevice = device
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (currentDevice?.address == device.address) {
                            currentDevice = null
                        }
                    }
                }
                callbacks.forEach { it.onConnectionStateChanged(device, state) }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "Get report: type=$type, id=$id")
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                val report = keyboardReport.getReport()
                hidDevice?.replyReport(device, type, id, report)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            Log.d(TAG, "Set report: type=$type, id=$id")
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    /**
     * 服务状态监听器
     */
    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "HID service connected")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "HID service disconnected")
            if (profile == BluetoothProfile.HID_DEVICE) {
                isRegistered = false
                hidDevice = null
            }
        }
    }

    /**
     * 初始化 HID 设备管理器
     */
    fun initialize(context: Context, callback: HidDeviceCallback) {
        callbacks.add(callback)
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) 
            as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter
        
        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    /**
     * 释放资源
     */
    fun release(context: Context, callback: HidDeviceCallback) {
        callbacks.remove(callback)
        
        if (callbacks.isEmpty()) {
            unregisterApp()
            
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) 
                as android.bluetooth.BluetoothManager
            val adapter = bluetoothManager.adapter
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
    }

    /**
     * 注册 HID 应用
     */
    private fun registerApp() {
        hidDevice?.registerApp(
            Constants.SDP_RECORD,
            null,
            Constants.QOS_OUT,
            Runnable::run,
            hidCallback
        )
    }

    /**
     * 注销 HID 应用
     */
    private fun unregisterApp() {
        if (isRegistered) {
            hidDevice?.unregisterApp()
        }
    }

    /**
     * 连接到指定设备
     */
    fun connect(device: BluetoothDevice): Boolean {
        return hidDevice?.connect(device) ?: false
    }

    /**
     * 断开连接
     */
    fun disconnect(device: BluetoothDevice) {
        hidDevice?.disconnect(device)
    }

    /**
     * 获取已连接设备列表
     */
    fun getConnectedDevices(): List<BluetoothDevice> {
        return hidDevice?.connectedDevices ?: emptyList()
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = currentDevice != null

    /**
     * 获取当前连接的设备
     */
    fun getCurrentDevice(): BluetoothDevice? = currentDevice

    /**
     * 发送键盘报告（实现 KeyboardDataSender 接口）
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
        val device = currentDevice
        val hid = hidDevice
        
        if (device != null && hid != null) {
            val report = keyboardReport.setValue(modifier, key1, key2, key3, key4, key5, key6)
            try {
                hid.sendReport(device, Constants.ID_KEYBOARD, report)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send report", e)
            }
        }
    }
}
