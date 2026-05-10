package com.example.bluetoothkeyboard

/**
 * HID 键盘报告数据类
 * 封装键盘报告的8字节数据结构
 */
class KeyboardReport {
    
    private val report = ByteArray(8)
    
    /**
     * 设置键盘报告值
     * @param modifier 修饰键位掩码 (Ctrl, Shift, Alt, GUI)
     * @param key1-6 按键扫描码 (最多6个同时按键)
     * @return 8字节报告数据
     */
    fun setValue(
        modifier: Int,
        key1: Int = 0,
        key2: Int = 0,
        key3: Int = 0,
        key4: Int = 0,
        key5: Int = 0,
        key6: Int = 0
    ): ByteArray {
        report[0] = modifier.toByte()
        report[1] = 0 // 保留字节
        report[2] = key1.toByte()
        report[3] = key2.toByte()
        report[4] = key3.toByte()
        report[5] = key4.toByte()
        report[6] = key5.toByte()
        report[7] = key6.toByte()
        return report
    }
    
    /**
     * 获取当前报告数据
     */
    fun getReport(): ByteArray = report.copyOf()
    
    /**
     * 清空报告（所有按键释放）
     */
    fun clear(): ByteArray {
        report.fill(0)
        return report
    }
    
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
}
