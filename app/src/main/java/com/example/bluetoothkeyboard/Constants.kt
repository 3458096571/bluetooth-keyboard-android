package com.example.bluetoothkeyboard

/**
 * HID 相关常量定义
 * 
 * 关键改动：
 * - 使用纯键盘描述符（移除鼠标部分）
 * - 使用 SUBCLASS1_KEYBOARD（纯键盘设备）
 * - Report ID = 0x01（键盘唯一报告）
 */
object Constants {

    // HID 报告 ID - 纯键盘设备使用 Report ID 1
    const val REPORT_ID_KEYBOARD: Int = 0x01

    // HID 设备名称
    const val HID_DEVICE_NAME = "Bluetooth Keyboard"
    const val DESCRIPTION = "Bluetooth HID Keyboard"
    const val PROVIDER = "Android"

    /**
     * HID 报告描述符 - 纯键盘设备
     * 
     * 标准 USB HID 键盘描述符
     * 报告格式：8字节
     * [0] = 修饰键 (Ctrl=0x01, Shift=0x02, Alt=0x04, GUI=0x08)
     * [1] = 保留字节 (0x00)
     * [2-7] = 按键码 (HID Usage ID, 0x00表示无按键)
     */
    val HID_DESCRIPTOR = byteArrayOf(
        // ===== 键盘设备 (Report ID = 0x01) =====
        0x05.toByte(), 0x01.toByte(),        // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),        // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),        // Collection (Application)
        0x85.toByte(), 0x01.toByte(),        //   REPORT_ID (Keyboard) = 1
        
        // 修饰键（8个1位字段）
        0x05.toByte(), 0x07.toByte(),        //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),        //   Usage Minimum (Left Control = 224)
        0x29.toByte(), 0xE7.toByte(),        //   Usage Maximum (Right GUI = 231)
        0x15.toByte(), 0x00.toByte(),        //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),        //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),        //   Report Size (1 bit)
        0x95.toByte(), 0x08.toByte(),        //   Report Count (8 fields)
        0x81.toByte(), 0x02.toByte(),        //   Input (Data, Variable, Absolute)
        
        // 保留字节
        0x95.toByte(), 0x01.toByte(),        //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),        //   Report Size (8 bits)
        0x81.toByte(), 0x01.toByte(),        //   Input (Constant)
        
        // LED 输出报告（5个LED位）
        0x95.toByte(), 0x05.toByte(),        //   Report Count (5)
        0x75.toByte(), 0x01.toByte(),        //   Report Size (1 bit)
        0x05.toByte(), 0x08.toByte(),        //   Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(),        //   Usage Minimum (Num Lock)
        0x29.toByte(), 0x05.toByte(),        //   Usage Maximum (Kana)
        0x91.toByte(), 0x02.toByte(),        //   Output (Data, Variable, Absolute)
        
        // LED 填充位（3位）
        0x95.toByte(), 0x01.toByte(),        //   Report Count (1)
        0x75.toByte(), 0x03.toByte(),        //   Report Size (3 bits)
        0x91.toByte(), 0x01.toByte(),        //   Output (Constant)
        
        // 按键数组（6个8位字段）
        0x95.toByte(), 0x06.toByte(),        //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),        //   Report Size (8 bits)
        0x15.toByte(), 0x00.toByte(),        //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),        //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),        //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),        //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),        //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),        //   Input (Data, Array)
        
        0xC0.toByte()                        // End Collection (Application)
    )

    // 键盘报告大小：8字节
    const val KEYBOARD_REPORT_SIZE = 8

    // 发送间隔（毫秒）
    const val SEND_INTERVAL_MS = 5L
}
