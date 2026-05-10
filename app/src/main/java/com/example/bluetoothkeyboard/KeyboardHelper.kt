package com.example.bluetoothkeyboard

import com.example.bluetoothkeyboard.KeyboardReport.KeyboardDataSender

/**
 * 键盘辅助类
 * 负责字符到扫描码的转换和按键发送
 */
class KeyboardHelper(private val dataSender: KeyboardDataSender) {

    /**
     * 修饰键常量
     */
    object Modifier {
        const val NONE = 0
        const val LEFT_CTRL = 1 shl 0
        const val LEFT_SHIFT = 1 shl 1
        const val LEFT_ALT = 1 shl 2
        const val LEFT_GUI = 1 shl 3
        const val RIGHT_CTRL = 1 shl 4
        const val RIGHT_SHIFT = 1 shl 5
        const val RIGHT_ALT = 1 shl 6
        const val RIGHT_GUI = 1 shl 7
    }

    /**
     * 特殊键扫描码
     */
    object Key {
        const val ENTER = 40
        const val ESCAPE = 41
        const val BACKSPACE = 42
        const val TAB = 43
        const val SPACE = 44
        const val DELETE = 76
        const val INSERT = 73
        const val HOME = 74
        const val END = 77
        const val PAGEUP = 75
        const val PAGEDOWN = 78
        const val RIGHT = 79
        const val LEFT = 80
        const val DOWN = 81
        const val UP = 82
        const val F1 = 58
        const val F2 = 59
        const val F3 = 60
        const val F4 = 61
        const val F5 = 62
        const val F6 = 63
        const val F7 = 64
        const val F8 = 65
        const val F9 = 66
        const val F10 = 67
        const val F11 = 68
        const val F12 = 69
    }

    // 字符到扫描码的映射表 (小写字母和数字)
    private val keyMap = mapOf(
        'a' to 0x04, 'b' to 0x05, 'c' to 0x06, 'd' to 0x07,
        'e' to 0x08, 'f' to 0x09, 'g' to 0x0A, 'h' to 0x0B,
        'i' to 0x0C, 'j' to 0x0D, 'k' to 0x0E, 'l' to 0x0F,
        'm' to 0x10, 'n' to 0x11, 'o' to 0x12, 'p' to 0x13,
        'q' to 0x14, 'r' to 0x15, 's' to 0x16, 't' to 0x17,
        'u' to 0x18, 'v' to 0x19, 'w' to 0x1A, 'x' to 0x1B,
        'y' to 0x1C, 'z' to 0x1D,
        '1' to 0x1E, '2' to 0x1F, '3' to 0x20, '4' to 0x21,
        '5' to 0x22, '6' to 0x23, '7' to 0x24, '8' to 0x25,
        '9' to 0x26, '0' to 0x27,
        ' ' to 0x2C,  // 空格
        '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30,
        '\\' to 0x31, ';' to 0x33, '\'' to 0x34, '`' to 0x35,
        ',' to 0x36, '.' to 0x37, '/' to 0x38
    )

    // 需要Shift的字符映射
    private val shiftKeyMap = mapOf(
        'A' to 0x04, 'B' to 0x05, 'C' to 0x06, 'D' to 0x07,
        'E' to 0x08, 'F' to 0x09, 'G' to 0x0A, 'H' to 0x0B,
        'I' to 0x0C, 'J' to 0x0D, 'K' to 0x0E, 'L' to 0x0F,
        'M' to 0x10, 'N' to 0x11, 'O' to 0x12, 'P' to 0x13,
        'Q' to 0x14, 'R' to 0x15, 'S' to 0x16, 'T' to 0x17,
        'U' to 0x18, 'V' to 0x19, 'W' to 0x1A, 'X' to 0x1B,
        'Y' to 0x1C, 'Z' to 0x1D,
        '!' to 0x1E, '@' to 0x1F, '#' to 0x20, '$' to 0x21,
        '%' to 0x22, '^' to 0x23, '&' to 0x24, '*' to 0x25,
        '(' to 0x26, ')' to 0x27,
        '_' to 0x2D, '+' to 0x2E, '{' to 0x2F, '}' to 0x30,
        '|' to 0x31, ':' to 0x33, '"' to 0x34, '~' to 0x35,
        '<' to 0x36, '>' to 0x37, '?' to 0x38
    )

    // 特殊键名称到扫描码的映射
    private val specialKeyMap = mapOf(
        "Enter" to Key.ENTER,
        "Escape" to Key.ESCAPE,
        "Backspace" to Key.BACKSPACE,
        "Tab" to Key.TAB,
        "Space" to Key.SPACE,
        "Delete" to Key.DELETE,
        "Insert" to Key.INSERT,
        "Home" to Key.HOME,
        "End" to Key.END,
        "PageUp" to Key.PAGEUP,
        "PageDown" to Key.PAGEDOWN,
        "Right" to Key.RIGHT,
        "Left" to Key.LEFT,
        "Down" to Key.DOWN,
        "Up" to Key.UP
    )

    /**
     * 发送单个字符
     * 自动处理大小写和Shift键
     */
    fun sendChar(char: Char) {
        val code = keyMap[char]
        if (code != null) {
            // 小写字符，直接发送
            sendKeyPress(Modifier.NONE, code)
        } else {
            // 尝试大写/符号映射
            val shiftCode = shiftKeyMap[char]
            if (shiftCode != null) {
                sendKeyPress(Modifier.LEFT_SHIFT, shiftCode)
            }
        }
    }

    /**
     * 发送字符串
     * 逐个字符发送
     */
    fun sendString(text: String) {
        for (char in text) {
            sendChar(char)
        }
    }

    /**
     * 发送特殊键
     */
    fun sendSpecialKey(keyName: String) {
        val code = specialKeyMap[keyName]
        if (code != null) {
            sendKeyPress(Modifier.NONE, code)
        }
    }

    /**
     * 发送回车键
     */
    fun sendEnter() {
        sendKeyPress(Modifier.NONE, Key.ENTER)
    }

    /**
     * 发送退格键
     */
    fun sendBackspace() {
        sendKeyPress(Modifier.NONE, Key.BACKSPACE)
    }

    /**
     * 发送带修饰键的组合键
     */
    fun sendCombo(modifier: Int, keyCode: Int) {
        sendKeyPress(modifier, keyCode)
    }

    /**
     * 发送按键按下和释放事件
     */
    private fun sendKeyPress(modifier: Int, keyCode: Int) {
        // 按下
        dataSender.sendKeyboard(modifier, keyCode, 0, 0, 0, 0, 0)
        // 释放
        dataSender.sendKeyboard(Modifier.NONE, 0, 0, 0, 0, 0, 0)
    }

    /**
     * 发送原始键盘数据
     */
    fun sendKeysDown(
        modifier: Int,
        key1: Int = 0,
        key2: Int = 0,
        key3: Int = 0,
        key4: Int = 0,
        key5: Int = 0,
        key6: Int = 0
    ) {
        dataSender.sendKeyboard(modifier, key1, key2, key3, key4, key5, key6)
    }

    /**
     * 发送所有按键释放
     */
    fun sendKeysUp() {
        dataSender.sendKeyboard(Modifier.NONE, 0, 0, 0, 0, 0, 0)
    }
}
