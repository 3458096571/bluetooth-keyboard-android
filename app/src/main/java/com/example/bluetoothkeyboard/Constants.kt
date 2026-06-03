package com.example.bluetoothkeyboard

/**
 * HID 相关常量定义
 * 基于 HidPeripheral 项目的成功实现方式
 * 
 * 关键改动：
 * - Report ID = 0x02 (键盘), 0x01 (鼠标)
 * - 使用 COMBO 设备 (SUBCLASS1_COMBO)
 * - 描述符定义了鼠标+键盘组合设备
 */
object Constants {

    // HID 报告 ID - 键盘使用 Report ID 2
    const val REPORT_ID_MOUSE: Int = 0x01
    const val REPORT_ID_KEYBOARD: Int = 0x02

    // HID 设备名称（注册前会设置蓝牙适配器名称为此值）
    const val HID_DEVICE_NAME = "Bluetooth Keyboard"
    const val DESCRIPTION = "Bluetooth HID Keyboard"
    const val PROVIDER = "Android"

    /**
     * HID 报告描述符 - 鼠标+键盘组合设备
     * 完全基于 HidPeripheral 项目的成功描述符
     * 
     * 结构：
     * 1. 鼠标设备 (Report ID = 0x01)
     *    - 3个按键 + X/Y 坐标 + 滚轮
     * 2. 键盘设备 (Report ID = 0x02)
     *    - 8个修饰键位 + 保留字节 + 6个按键码
     * 
     * 键盘报告格式：8字节
     * [0] = 修饰键 (Ctrl=0x01, Shift=0x02, Alt=0x04, GUI=0x08)
     * [1] = 保留字节 (0x00)
     * [2-7] = 按键码 (HID Usage ID, 0x00表示无按键)
     */
    val HID_DESCRIPTOR = byteArrayOf(
        // ===== 鼠标设备 (Report ID = 0x01) =====
        0x05.toByte(), 0x01.toByte(),        // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),        // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(),        // Collection (Application)
        0x85.toByte(), 0x01.toByte(),        //   REPORT_ID (Mouse) = 1
        0x09.toByte(), 0x01.toByte(),        //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(),        //   Collection (Physical)
        0x05.toByte(), 0x09.toByte(),        //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),        //     Usage Minimum (1)
        0x29.toByte(), 0x03.toByte(),        //     Usage Maximum (3)
        0x15.toByte(), 0x00.toByte(),        //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),        //     Logical Maximum (1)
        0x95.toByte(), 0x03.toByte(),        //     Report Count (3)
        0x75.toByte(), 0x01.toByte(),        //     Report Size (1)
        0x81.toByte(), 0x02.toByte(),        //     Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(),        //     Report Count (1)
        0x75.toByte(), 0x05.toByte(),        //     Report Size (5)
        0x81.toByte(), 0x01.toByte(),        //     Input (Constant)
        0x05.toByte(), 0x01.toByte(),        //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),        //     Usage (X)
        0x09.toByte(), 0x31.toByte(),        //     Usage (Y)
        0x15.toByte(), 0x81.toByte(),        //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),        //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),        //     Report Size (8)
        0x95.toByte(), 0x02.toByte(),        //     Report Count (2)
        0x81.toByte(), 0x06.toByte(),        //     Input (Data, Variable, Relative)
        0x05.toByte(), 0x01.toByte(),        //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x38.toByte(),        //     Usage (Wheel)
        0x15.toByte(), 0x81.toByte(),        //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),        //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),        //     Report Size (8)
        0x95.toByte(), 0x01.toByte(),        //     Report Count (1)
        0x81.toByte(), 0x06.toByte(),        //     Input (Data, Variable, Relative)
        0xC0.toByte(),                       //   End Collection (Physical)
        0xC0.toByte(),                       // End Collection (Application)

        // ===== 键盘设备 (Report ID = 0x02) =====
        0x05.toByte(), 0x01.toByte(),        // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),        // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),        // Collection (Application)
        0x85.toByte(), 0x02.toByte(),        //   REPORT_ID (Keyboard) = 2  ★关键★
        0x05.toByte(), 0x07.toByte(),        //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),        //   Usage Minimum (Left Control = 224)
        0x29.toByte(), 0xE7.toByte(),        //   Usage Maximum (Right GUI = 231)
        0x15.toByte(), 0x00.toByte(),        //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),        //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),        //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),        //   Report Count (8) - 8个修饰键位
        0x81.toByte(), 0x02.toByte(),        //   Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(),        //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),        //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),        //   Input (Constant) - 保留字节
        0x95.toByte(), 0x06.toByte(),        //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),        //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),        //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),        //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),        //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),        //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),        //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),        //   Input (Data, Array) - 6字节按键码数组
        0xC0.toByte()                        // End Collection (Application)
    )

    // 键盘报告大小：8字节
    const val KEYBOARD_REPORT_SIZE = 8

    // 发送间隔（毫秒）
    const val SEND_INTERVAL_MS = 5L
}
