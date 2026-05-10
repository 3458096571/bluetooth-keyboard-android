package com.example.bluetoothkeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 主界面 Activity
 * 实现蓝牙配对、文字输入和 HID 发送功能
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "BluetoothKeyboardPrefs"
        private const val KEY_ENTER_TO_SEND = "enter_to_send"
        private const val KEY_CUSTOM_BACKGROUND = "custom_background"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // UI 组件
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var pairButton: Button
    private lateinit var deviceNameText: TextView
    private lateinit var inputContainer: LinearLayout
    private lateinit var inputEditText: EditText
    private lateinit var settingsButton: ImageButton
    private lateinit var backgroundImage: ImageView

    // 蓝牙相关
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothHidService: BluetoothHidService? = null
    private var isServiceBound = false

    // 配置
    private lateinit var prefs: SharedPreferences
    private var enterToSend = false
    private var keyboardHelper: KeyboardHelper? = null

    // 发送队列任务
    private var sendJob: Job? = null

    // 图片选择器
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    // 蓝牙开启请求
    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 用户处理蓝牙开启 */ }

    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothHidService.LocalBinder
            bluetoothHidService = binder.getService()
            isServiceBound = true

            // 初始化 HID 设备管理器
            bluetoothHidService?.initializeHid(hidDeviceCallback)

            // 更新 UI 状态
            updateConnectionStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothHidService = null
            isServiceBound = false
        }
    }

    // HID 设备回调
    private val hidDeviceCallback = object : HidDeviceManager.HidDeviceCallback {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            runOnUiThread {
                when (state) {
                    BluetoothAdapter.STATE_CONNECTED -> {
                        showConnected(device)
                    }
                    BluetoothAdapter.STATE_DISCONNECTED -> {
                        showDisconnected()
                    }
                }
            }
        }

        override fun onAppRegistered(success: Boolean) {
            Log.d(TAG, "HID app registered: $success")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enterToSend = prefs.getBoolean(KEY_ENTER_TO_SEND, false)

        // 初始化视图
        initViews()

        // 初始化蓝牙
        initBluetooth()

        // 检查权限
        checkAndRequestPermissions()

        // 绑定服务
        bindHidService()

        // 加载自定义背景
        loadCustomBackground()
    }

    /**
     * 初始化视图组件
     */
    private fun initViews() {
        rootLayout = findViewById(R.id.root_layout)
        pairButton = findViewById(R.id.pair_button)
        deviceNameText = findViewById(R.id.device_name)
        inputContainer = findViewById(R.id.input_container)
        inputEditText = findViewById(R.id.input_edit_text)
        settingsButton = findViewById(R.id.settings_button)
        backgroundImage = findViewById(R.id.background_image)

        // 设置毛玻璃效果 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            inputContainer.setRenderEffect(
                RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            )
        }

        // 配对按钮点击
        pairButton.setOnClickListener {
            showPairedDevicesDialog()
        }

        // 设置按钮点击
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        // 设置输入监听
        setupInputListener()
    }

    /**
     * 初始化蓝牙适配器
     */
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        }
    }

    /**
     * 绑定 HID 服务
     */
    private fun bindHidService() {
        val intent = Intent(this, BluetoothHidService::class.java).apply {
            action = BluetoothHidService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 设置输入监听器
     * 监听文本变化并发送到已连接设备
     */
    private fun setupInputListener() {
        // 文本变化监听 - 实时发送
        inputEditText.addTextChangedListener(object : TextWatcher {
            private var beforeText: String = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                beforeText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 不在这里处理，避免频繁发送
            }

            override fun afterTextChanged(s: Editable?) {
                val currentText = s?.toString() ?: ""

                // 检测新增的文字
                if (currentText.length > beforeText.length) {
                    val addedText = currentText.substring(beforeText.length)
                    sendText(addedText)
                } else if (currentText.length < beforeText.length) {
                    // 删除了文字，发送退格键
                    val deleteCount = beforeText.length - currentText.length
                    repeat(deleteCount) {
                        sendBackspace()
                    }
                }
            }
        })

        // 键盘按键监听（用于处理回车键）
        inputEditText.setOnEditorActionListener { _, actionId, event ->
            if (enterToSend && (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN))) {
                sendEnter()
                // 清空输入框
                inputEditText.text?.clear()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    /**
     * 发送文本到已连接设备
     */
    private fun sendText(text: String) {
        if (!isServiceBound || bluetoothHidService?.isConnected() != true) {
            return
        }

        // 取消之前的发送任务
        sendJob?.cancel()

        sendJob = lifecycleScope.launch(Dispatchers.IO) {
            keyboardHelper?.sendString(text)
        }
    }

    /**
     * 发送回车键
     */
    private fun sendEnter() {
        if (!isServiceBound || bluetoothHidService?.isConnected() != true) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            keyboardHelper?.sendEnter()
        }
    }

    /**
     * 发送退格键
     */
    private fun sendBackspace() {
        if (!isServiceBound || bluetoothHidService?.isConnected() != true) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            keyboardHelper?.sendBackspace()
        }
    }

    /**
     * 显示已配对设备对话框
     */
    private fun showPairedDevicesDialog() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "需要蓝牙权限", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()

        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "没有已配对的设备，请先在系统设置中配对", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: "未知设备" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择要连接的设备")
            .setItems(deviceNames) { _, which ->
                val device = pairedDevices[which]
                connectToDevice(device)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 连接到指定设备
     */
    private fun connectToDevice(device: BluetoothDevice) {
        if (!isServiceBound) {
            Toast.makeText(this, "服务未就绪，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }

        val success = bluetoothHidService?.connectDevice(device) ?: false
        if (success) {
            Toast.makeText(this, "正在连接 ${device.name}...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val enterSwitch = dialogView.findViewById<Switch>(R.id.enter_to_send_switch)
        val changeBgButton = dialogView.findViewById<Button>(R.id.change_background_button)
        val resetBgButton = dialogView.findViewById<Button>(R.id.reset_background_button)

        enterSwitch.isChecked = enterToSend

        val dialog = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                enterToSend = enterSwitch.isChecked
                prefs.edit().putBoolean(KEY_ENTER_TO_SEND, enterToSend).apply()
                updateInputImeOptions()
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()

        changeBgButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
            dialog.dismiss()
        }

        resetBgButton.setOnClickListener {
            prefs.edit().remove(KEY_CUSTOM_BACKGROUND).apply()
            loadCustomBackground()
            Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    /**
     * 更新输入法选项
     */
    private fun updateInputImeOptions() {
        inputEditText.imeOptions = if (enterToSend) {
            EditorInfo.IME_ACTION_SEND
        } else {
            EditorInfo.IME_ACTION_UNSPECIFIED
        }
    }

    /**
     * 处理选择的图片
     */
    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // 保存到内部存储
            val fileName = "custom_background.jpg"
            openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }

            // 保存路径
            val filePath = filesDir.absolutePath + "/" + fileName
            prefs.edit().putString(KEY_CUSTOM_BACKGROUND, filePath).apply()

            // 加载背景
            loadCustomBackground()
            Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set background", e)
            Toast.makeText(this, "设置背景失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 加载自定义背景
     */
    private fun loadCustomBackground() {
        val backgroundPath = prefs.getString(KEY_CUSTOM_BACKGROUND, null)

        if (backgroundPath != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(backgroundPath)
                backgroundImage.setImageBitmap(bitmap)
                backgroundImage.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load background", e)
                backgroundImage.visibility = View.GONE
            }
        } else {
            backgroundImage.visibility = View.GONE
        }
    }

    /**
     * 显示已连接状态
     */
    private fun showConnected(device: BluetoothDevice?) {
        pairButton.visibility = View.GONE
        deviceNameText.visibility = View.VISIBLE
        deviceNameText.text = "已连接: ${device?.name ?: "未知设备"}"

        // 初始化 keyboardHelper
        bluetoothHidService?.let {
            val hidManager = HidDeviceManager.getInstance()
            keyboardHelper = KeyboardHelper(hidManager)
        }
    }

    /**
     * 显示断开连接状态
     */
    private fun showDisconnected() {
        pairButton.visibility = View.VISIBLE
        deviceNameText.visibility = View.GONE
        keyboardHelper = null
    }

    /**
     * 更新连接状态显示
     */
    private fun updateConnectionStatus() {
        if (bluetoothHidService?.isConnected() == true) {
            showConnected(bluetoothHidService?.getCurrentDevice())
        } else {
            showDisconnected()
        }
    }

    /**
     * 检查和请求权限
     */
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "需要权限才能使用蓝牙功能", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sendJob?.cancel()

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
