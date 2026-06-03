package com.example.bluetoothkeyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 蓝牙 HID 前台服务
 * 保持应用在后台时蓝牙连接不中断
 * 
 * 增强功能：
 * 1. 前台服务 + 通知保持存活
 * 2. WakeLock 防止 CPU 休眠导致连接断开
 * 3. 连接状态心跳检测
 */
class BluetoothHidService : Service() {

    companion object {
        private const val TAG = "BluetoothHidService"
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_hid_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = LocalBinder()
    private var hidDeviceManager: HidDeviceManager? = null
    private var serviceCallback: HidDeviceManager.HidDeviceCallback? = null
    
    // WakeLock 防止连接因 CPU 休眠而断开
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 心跳检测 Handler
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            heartbeatHandler.postDelayed(this, 5000) // 每5秒更新一次通知
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        hidDeviceManager = HidDeviceManager.getInstance()
        
        // 获取 WakeLock
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BluetoothKeyboard::WakeLock"
        )
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("蓝牙键盘服务运行中", "正在保持蓝牙连接...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // 获取 WakeLock 保持 CPU 运行
        try {
            wakeLock?.acquire(10 * 60 * 1000L) // 10分钟超时
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
        
        // 启动心跳检测
        heartbeatHandler.post(heartbeatRunnable)
        
        Log.d(TAG, "Foreground service started")
    }

    private fun stopForegroundService() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        
        // 释放 WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Foreground service stopped")
    }

    fun initializeHid(callback: HidDeviceManager.HidDeviceCallback) {
        serviceCallback = callback
        hidDeviceManager?.initialize(this, callback)
    }

    fun releaseHid() {
        serviceCallback?.let {
            hidDeviceManager?.release(this, it)
        }
    }

    fun connectDevice(device: BluetoothDevice): Boolean {
        val result = hidDeviceManager?.connect(device) ?: false
        if (result) {
            updateNotification("已连接: ${device.name ?: "未知设备"}", "蓝牙键盘正在工作")
        }
        return result
    }

    fun disconnectDevice(device: BluetoothDevice) {
        hidDeviceManager?.disconnect(device)
        updateNotification("蓝牙键盘服务运行中", "等待连接...")
    }

    fun isConnected(): Boolean {
        return hidDeviceManager?.isConnected() ?: false
    }

    fun getCurrentDevice(): BluetoothDevice? {
        return hidDeviceManager?.getCurrentDevice()
    }

    private fun updateNotification(title: String = "蓝牙键盘服务", content: String = "正在保持蓝牙连接...") {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "蓝牙键盘服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持蓝牙键盘连接"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock on destroy", e)
        }
        
        releaseHid()
        Log.d(TAG, "Service destroyed")
    }
}
