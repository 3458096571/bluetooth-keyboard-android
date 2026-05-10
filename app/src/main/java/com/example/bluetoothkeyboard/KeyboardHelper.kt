package com.example.bluetoothkeyboard

import android.util.Log
import com.example.bluetoothkeyboard.KeyboardReport.KeyboardDataSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 键盘辅助类
 * 负责字符到扫描码的转换和按键发送
 */
class KeyboardHelper(private val dataSender: KeyboardDataSender) {

    companion object {
        private const val TAG = "KeyboardHelper"
        // 每个按键之间的延迟（毫秒），确保接收端能正确处理
        private const val KEY_DELAY_MS = 20L
    }

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

    /**
     * 发送单个字符（同步版本）
     * 自动处理大小写和Shift键
     */
    fun sendChar(char: Char) {
        var shift = false
        var code = keyMap[char]
        
        if (code == null) {
            // 在 shiftKeyMap 中查找
            shift = true
            code = shiftKeyMap[char]
        }
        
        if (code != null) {
            // 发送按键按下
            val modifier = if (shift) Modifier.LEFT_SHIFT else Modifier.NONE
            dataSender.sendKeyboard(modifier, code, 0, 0, 0, 0, 0)
            // 发送按键释放
            dataSender.sendKeyboard(Modifier.NONE, 0, 0, 0, 0, 0, 0)
            Log.d(TAG, "sendChar: '$char' -> code=$code, shift=$shift")
        } else {
            Log.w(TAG, "sendChar: unknown char '$char'")
        }
    }

    /**
     * 发送字符串（挂起函数，带延迟）
     * 在 IO 线程中执行，每个字符之间有延迟
     */
    suspend fun sendString(text: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendString: \"$text\" (${text.length} chars)")
        for (char in text) {
            sendChar(char)
            delay(KEY_DELAY_MS)
        }
        Log.d(TAG, "sendString: done")
    }

    /**
     * 发送回车键
     */
    fun sendEnter() {
        Log.d(TAG, "sendEnter")
        dataSender.sendKeyboard(Modifier.NONE, Key.ENTER, 0, 0, 0, 0, 0)
        dataSender.sendKeyboard(Modifier.NONE, 0, 0, 0, 0, 0, 0)
    }

    /**
     * 发送退格键
     */
    fun sendBackspace() {
        Log.d(TAG, "sendBackspace")
        dataSender.sendKeyboard(Modifier.NONE, Key.BACKSPACE, 0, 0, 0, 0, 0)
        dataSender.sendKeyboard(Modifier.NONE, 0, 0, 0, 0, 0, 0)
    }

    /**
     * 发送按键按下和释放事件
     */
    fun sendKeyPress(modifier: Int, keyCode: Int) {
        dataSender.sendKeyboard(modifier, keyCode, 0, 0, 0, 0, 0)
        dataSender.sendKeyboard(Modifier.NONE, 0, 0, 0, 0, 0, 0)
    }
}
