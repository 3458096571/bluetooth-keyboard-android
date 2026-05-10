package com.example.bluetoothkeyboard

/**
 * HID 相关常量定义
 * 使用与成功项目完全一致的 HID 描述符
 */
object Constants {

    // HID 报告 ID - 必须与描述符中的 0x85, 0x08 对应
    const val ID_KEYBOARD: Int = 8

    // 键盘报告描述符
    // 关键：0x85, 0x08 定义了 Report ID = 8
    val KEYBOARD_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),                         // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),                         // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),                         // Collection (Application)
        0x85.toByte(), 0x08.toByte(),                         // REPORT_ID (Keyboard) = 8
        0x05.toByte(), 0x07.toByte(),                         // Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),                         // Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(),                         // Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(),                         // Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),                         // Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),                         // Report Size (1)
        0x95.toByte(), 0x08.toByte(),                         // Report Count (8)
        0x81.toByte(), 0x02.toByte(),                         // Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(),                         // Report Count (1)
        0x75.toByte(), 0x08.toByte(),                         // Report Size (8)
        0x81.toByte(), 0x01.toByte(),                         // Input (Constant) reserved byte(1)
        0x95.toByte(), 0x05.toByte(),                         // Report Count (5)
        0x75.toByte(), 0x01.toByte(),                         // Report Size (1)
        0x05.toByte(), 0x08.toByte(),                         // Usage Page (Page# for LEDs)
        0x19.toByte(), 0x01.toByte(),                         // Usage Minimum (1)
        0x29.toByte(), 0x05.toByte(),                         // Usage Maximum (5)
        0x91.toByte(), 0x02.toByte(),                         // Output (Data, Variable, Absolute), Led report
        0x95.toByte(), 0x01.toByte(),                         // Report Count (1)
        0x75.toByte(), 0x03.toByte(),                         // Report Size (3)
        0x91.toByte(), 0x01.toByte(),                         // Output (Data, Variable, Absolute), Led report padding
        0x95.toByte(), 0x06.toByte(),                         // Report Count (6)
        0x75.toByte(), 0x08.toByte(),                         // Report Size (8)
        0x15.toByte(), 0x00.toByte(),                         // Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),                         // Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),                         // Usage Page (Key codes)
        0x19.toByte(), 0x00.toByte(),                         // Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),                         // Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),                         // Input (Data, Array) Key array(6 bytes)
        0xC0.toByte()                                          // End Collection (Application)
    )
}
