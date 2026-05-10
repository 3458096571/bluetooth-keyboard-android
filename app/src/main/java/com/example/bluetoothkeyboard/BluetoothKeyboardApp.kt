package com.example.bluetoothkeyboard

import android.app.Application
import android.util.Log

/**
 * 应用入口类
 */
class BluetoothKeyboardApp : Application() {
    
    companion object {
        const val TAG = "BluetoothKeyboard"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Bluetooth Keyboard App initialized")
    }
}
