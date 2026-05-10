package com.example.bluetoothkeyboard

/**
 * HID 相关常量定义
 */
object Constants {

    // HID 报告 ID
    const val ID_KEYBOARD: Byte = 1

    // 键盘报告描述符
    // 标准 USB HID 键盘描述符
    val SDP_RECORD = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x06.toByte(),
        0xA1.toByte(), 0x01.toByte(),
        0x05.toByte(), 0x07.toByte(),
        0x19.toByte(), 0xE0.toByte(),
        0x29.toByte(), 0xE7.toByte(),
        0x15.toByte(), 0x00.toByte(),
        0x25.toByte(), 0x01.toByte(),
        0x75.toByte(), 0x01.toByte(),
        0x95.toByte(), 0x08.toByte(),
        0x81.toByte(), 0x02.toByte(),
        0x95.toByte(), 0x01.toByte(),
        0x75.toByte(), 0x08.toByte(),
        0x81.toByte(), 0x01.toByte(),
        0x95.toByte(), 0x05.toByte(),
        0x75.toByte(), 0x01.toByte(),
        0x05.toByte(), 0x08.toByte(),
        0x19.toByte(), 0x01.toByte(),
        0x29.toByte(), 0x05.toByte(),
        0x91.toByte(), 0x02.toByte(),
        0x95.toByte(), 0x01.toByte(),
        0x75.toByte(), 0x03.toByte(),
        0x91.toByte(), 0x01.toByte(),
        0x95.toByte(), 0x06.toByte(),
        0x75.toByte(), 0x08.toByte(),
        0x15.toByte(), 0x00.toByte(),
        0x25.toByte(), 0x65.toByte(),
        0x05.toByte(), 0x07.toByte(),
        0x19.toByte(), 0x00.toByte(),
        0x29.toByte(), 0x65.toByte(),
        0x81.toByte(), 0x00.toByte(),
        0xC0.toByte()
    )

    // QoS 参数
    val QOS_OUT = byteArrayOf(
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
    )
}
