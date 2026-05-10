package com.example.bluetoothkeyboard

/**
 * 键盘数据发送接口
 */
interface KeyboardDataSender {
    fun sendKeyboard(
        modifier: Int,
        key1: Int,
        key2: Int,
        key3: Int,
        key4: Int,
        key5: Int,
        key6: Int
    )
}
