package com.example.bluetoothkeyboard

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 键盘辅助类
 * 负责字符到 HID 扫描码的转换和按键发送
 * 
 * 使用 HidDeviceManager 的队列发送机制
 * 每个字符：按下报告 → 释放报告
 */
class KeyboardHelper(private val hidManager: HidDeviceManager) {

    companion object {
        private const val TAG = "KeyboardHelper"
        private const val CHAR_DELAY_MS = 20L  // 每个字符之间的延迟
    }

    object Modifier {
        const val NONE: Byte = 0
        const val LEFT_CTRL: Byte = 0x01
        const val LEFT_SHIFT: Byte = 0x02
        const val LEFT_ALT: Byte = 0x04
        const val LEFT_GUI: Byte = 0x08
        const val RIGHT_CTRL: Byte = 0x10
        const val RIGHT_SHIFT: Byte = 0x20
        const val RIGHT_ALT: Byte = 0x40
        const val RIGHT_GUI: Byte = 0x80
    }

    object Key {
        const val ENTER: Byte = 0x28       // HID Usage ID 40
        const val ESCAPE: Byte = 0x29      // HID Usage ID 41
        const val BACKSPACE: Byte = 0x2A   // HID Usage ID 42
        const val TAB: Byte = 0x2B         // HID Usage ID 43
        const val SPACE: Byte = 0x2C        // HID Usage ID 44
        const val DELETE: Byte = 0x4C       // HID Usage ID 76
    }

    // 字符到 HID Usage ID 的映射表（小写字母和数字）
    private val keyMap: Map<Char, Byte> = mapOf(
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
        ' ' to 0x2C,
        '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30,
        '\\' to 0x31, ';' to 0x33, '\'' to 0x34, '`' to 0x35,
        ',' to 0x36, '.' to 0x37, '/' to 0x38,
        '\n' to 0x28  // 回车
    )

    // 需要 Shift 的字符映射
    private val shiftKeyMap: Map<Char, Byte> = mapOf(
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

    /**
     * 发送单个字符
     * 流程：按下报告（带修饰键+按键码）→ 释放报告（全零）
     */
    fun sendChar(char: Char) {
        val isShift = keyMap[char] == null
        val code = if (isShift) shiftKeyMap[char] else keyMap[char]

        if (code != null) {
            val modifier = if (isShift) Modifier.LEFT_SHIFT else Modifier.NONE

            // 发送按键按下
            hidManager.sendKeyboardReport(modifier, code)
            Log.d(TAG, "sendChar 按下: '$char' -> code=0x${code.toString(16)}, modifier=0x${modifier.toString(16)}")

            // 短暂延迟确保按下事件被处理
            try { Thread.sleep(10) } catch (_: InterruptedException) {}

            // 发送按键释放（全零报告）
            hidManager.sendReleaseReport()
            Log.d(TAG, "sendChar 释放: '$char'")
        } else {
            Log.w(TAG, "sendChar: 未知字符 '$char' (code=${char.code})")
        }
    }

    /**
     * 发送字符串（挂起函数，带延迟）
     */
    suspend fun sendString(text: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendString: \"$text\" (${text.length} 字符)")
        for (char in text) {
            sendChar(char)
            delay(CHAR_DELAY_MS)
        }
        Log.d(TAG, "sendString: 完成")
    }

    /**
     * 发送回车键
     */
    fun sendEnter() {
        Log.d(TAG, "sendEnter")
        hidManager.sendKeyboardReport(Modifier.NONE, Key.ENTER)
        try { Thread.sleep(10) } catch (_: InterruptedException) {}
        hidManager.sendReleaseReport()
    }

    /**
     * 发送退格键
     */
    fun sendBackspace() {
        Log.d(TAG, "sendBackspace")
        hidManager.sendKeyboardReport(Modifier.NONE, Key.BACKSPACE)
        try { Thread.sleep(10) } catch (_: InterruptedException) {}
        hidManager.sendReleaseReport()
    }

    /**
     * 发送特殊按键
     */
    fun sendKey(keyCode: Byte, modifier: Byte = Modifier.NONE) {
        hidManager.sendKeyboardReport(modifier, keyCode)
        try { Thread.sleep(10) } catch (_: InterruptedException) {}
        hidManager.sendReleaseReport()
    }
}
