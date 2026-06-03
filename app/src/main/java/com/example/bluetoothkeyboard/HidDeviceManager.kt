package com.example.bluetoothkeyboard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HID 设备管理器
 * 
 * 基于 HidPeripheral 项目的成功实现方式：
 * 1. 注册前设置蓝牙适配器名称
 * 2. 使用 SUBCLASS1_COMBO（鼠标+键盘组合设备）
 * 3. 使用队列 + Timer 定时器发送报告（每5ms轮询）
 * 4. 使用 synchronized 保证线程安全
 * 5. QoS 参数传 null
 */
class HidDeviceManager private constructor() {

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

    // HID 设备和连接状态
    private var mHidDevice: BluetoothHidDevice? = null
    private var mHostDevice: BluetoothDevice? = null
    @Volatile
    private var isRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<HidDeviceManager.HidDeviceCallback>()

    // ★ 关键：队列 + 定时器发送机制 ★
    // 使用 ConcurrentLinkedQueue 存待发送的报告
    private val reportQueue = ConcurrentLinkedQueue<ByteArray>()
    // 定时器线程，每5ms轮询队列并发送
    private var sendThread: Thread? = null
    private val isSending = AtomicBoolean(false)

    // ★ 关键：键盘状态（线程安全）★
    @Volatile
    private var modifierByte: Byte = 0
    @Volatile
    private var keyByte: Byte = 0

    // HID 回调
    private val mCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered, pluggedDevice=${pluggedDevice?.name}")
            isRegistered = registered
            mainHandler.post {
                callbacks.forEach { it.onAppRegistered(registered) }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device.name} (${device.address}), state=$state")
            mainHandler.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        mHostDevice = device
                        Log.d(TAG, "★ HID 已连接到: ${device.name} ★")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (mHostDevice?.address == device.address) {
                            mHostDevice = null
                        }
                        Log.d(TAG, "HID 已断开")
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Log.d(TAG, "HID 正在连接...")
                    }
                }
                callbacks.forEach { it.onConnectionStateChanged(device, state) }
            }
        }
    }

    // 服务监听器
    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                if (proxy is BluetoothHidDevice) {
                    mHidDevice = proxy
                    Log.d(TAG, "HID proxy 已获取")
                    registerApp()
                } else {
                    Log.e(TAG, "Proxy 不是 BluetoothHidDevice 类型")
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                isRegistered = false
                mHidDevice = null
                mHostDevice = null
                stopSendThread()
            }
        }
    }

    /**
     * 初始化 HID 设备
     * ★ 关键：先设置蓝牙适配器名称，再注册 ★
     */
    fun initialize(context: Context, callback: HidDeviceCallback) {
        callbacks.add(callback)
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (!adapter.isEnabled) {
            Log.e(TAG, "蓝牙未开启")
            return
        }

        // ★ 关键步骤：设置蓝牙适配器名称 ★
        // HidPeripheral 项目中这一步至关重要，确保远程设备正确识别
        val oldName = adapter.name
        adapter.name = Constants.HID_DEVICE_NAME
        Log.d(TAG, "蓝牙名称已更改: \"$oldName\" -> \"${adapter.name}\"")

        // 启动发送线程
        startSendThread()

        // 获取 HID Device 代理
        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun release(context: Context, callback: HidDeviceCallback) {
        callbacks.remove(callback)
        if (callbacks.isEmpty()) {
            stopSendThread()
            unregisterApp()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            mHidDevice?.let { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
            mHidDevice = null
            mHostDevice = null
        }
    }

    /**
     * 注册 HID 应用
     * ★ 使用 SUBCLASS1_COMBO（鼠标+键盘组合设备）★
     * ★ QoS 参数传 null ★
     */
    private fun registerApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            Constants.HID_DEVICE_NAME,
            Constants.DESCRIPTION,
            Constants.PROVIDER,
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,  // ★ 纯键盘设备 ★
            Constants.HID_DESCRIPTOR
        )

        try {
            mHidDevice?.registerApp(
                sdpSettings,
                null,  // inQos = null
                null,  // outQos = null
                Executors.newCachedThreadPool(),
                mCallback
            )
            Log.d(TAG, "registerApp 已调用: SUBCLASS1_COMBO, QoS=null, name=\"${Constants.HID_DEVICE_NAME}\"")
        } catch (e: Exception) {
            Log.e(TAG, "registerApp 失败: ${e.message}", e)
            Log.e(TAG, "registerApp 5参数版本失败，无法继续")
        }
    }

    private fun unregisterApp() {
        if (isRegistered && mHidDevice != null) {
            try {
                mHidDevice?.unregisterApp()
                Log.d(TAG, "unregisterApp 成功")
            } catch (e: Exception) {
                Log.e(TAG, "unregisterApp 失败", e)
            }
        }
    }

    fun connect(device: BluetoothDevice): Boolean {
        Log.d(TAG, "连接设备: ${device.name} (${device.address})")
        return try {
            mHidDevice?.connect(device) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "连接失败", e)
            false
        }
    }

    fun disconnect(device: BluetoothDevice) {
        try {
            mHidDevice?.disconnect(device)
            Log.d(TAG, "已断开设备: ${device.name}")
        } catch (e: Exception) {
            Log.e(TAG, "断开失败", e)
        }
    }

    fun isConnected(): Boolean = mHostDevice != null

    fun getCurrentDevice(): BluetoothDevice? = mHostDevice

    // ========== ★ 关键：队列 + 定时器发送机制 ==========

    /**
     * 启动发送线程
     * 每5ms轮询队列，取出报告并通过 sendReport 发送
     * 这是 HidPeripheral 项目的核心发送方式
     */
    private fun startSendThread() {
        if (isSending.getAndSet(true)) return

        sendThread = Thread({
            Log.d(TAG, "发送线程已启动")
            while (isSending.get()) {
                try {
                    val report = reportQueue.poll()
                    if (report != null) {
                        sendReportToDevice(report)
                    }
                    Thread.sleep(Constants.SEND_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    Log.d(TAG, "发送线程被中断")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "发送线程异常", e)
                }
            }
            Log.d(TAG, "发送线程已停止")
        }, "HidSendThread").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * 停止发送线程
     */
    private fun stopSendThread() {
        isSending.set(false)
        sendThread?.interrupt()
        sendThread = null
    }

    /**
     * 实际发送报告到设备
     */
    private fun sendReportToDevice(report: ByteArray) {
        val hid = mHidDevice
        val device = mHostDevice

        if (hid != null && device != null) {
            try {
                hid.sendReport(device, Constants.REPORT_ID_KEYBOARD, report)
                Log.d(TAG, "sendReport: reportId=${Constants.REPORT_ID_KEYBOARD}, data=[${report.joinToString(",") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }}]")
            } catch (e: Exception) {
                Log.e(TAG, "sendReport 失败", e)
            }
        } else {
            Log.w(TAG, "sendReport 跳过: hid=${hid != null}, device=${device != null}")
        }
    }

    // ========== 键盘发送接口 ==========

    /**
     * 发送键盘报告 - 使用队列机制
     * 
     * 报告格式（8字节）：
     * [0] = 修饰键 (Ctrl=0x01, Shift=0x02, Alt=0x04, GUI=0x08)
     * [1] = 保留字节 (0x00)
     * [2] = 按键码 (HID Usage ID, 0x00表示无按键)
     * [3-7] = 保留 (0x00)
     */
    fun sendKeyboardReport(modifier: Byte, keyCode: Byte) {
        synchronized(this) {
            modifierByte = modifier
            keyByte = keyCode
        }
        // 构造8字节报告并加入队列
        val report = byteArrayOf(
            modifier,  // 修饰键
            0,         // 保留字节
            keyCode,   // 按键码
            0, 0, 0, 0, 0  // 保留
        )
        reportQueue.offer(report)
    }

    /**
     * 发送释放所有按键的报告
     */
    fun sendReleaseReport() {
        synchronized(this) {
            modifierByte = 0
            keyByte = 0
        }
        val report = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        reportQueue.offer(report)
    }

    /**
     * 发送原始8字节报告（兼容旧接口）
     */
    fun sendRawReport(report: ByteArray) {
        reportQueue.offer(report.copyOf())
    }
}
