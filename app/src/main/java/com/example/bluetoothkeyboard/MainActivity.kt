package com.example.bluetoothkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * 主活动 - 蓝牙键盘应用主界面
 * 功能：蓝牙设备配对、连接管理、文本输入和传输
 */
class MainActivity : AppCompatActivity() {

    // 蓝牙相关
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isHidServiceReady = false

    // UI 控件
    private lateinit var etInput: EditText
    private lateinit var btnPair: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnBackspace: Button
    private lateinit var btnSend: Button
    private lateinit var tvDeviceStatus: TextView
    private lateinit var tvEnterMode: TextView
    private lateinit var ivBackground: ImageView
    private lateinit var deviceListContainer: FrameLayout
    private lateinit var lvDevices: ListView
    private lateinit var pbScanning: ProgressBar
    private lateinit var tvNoDevices: TextView
    private lateinit var btnCancelScan: Button

    // 设备列表相关
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceListAdapter: DeviceListAdapter
    private var isScanning = false

    // 设置相关
    private var replaceEnterWithSend = false
    private var customBackgroundPath: String? = null

    // Handler 用于主线程更新
    private val handler = Handler(Looper.getMainLooper())

    // 广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                            updateDeviceList()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    updateScanningState()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        // 配对成功，尝试连接
                        device?.let { connectToDevice(it) }
                    }
                }
            }
        }
    }

    // HID Profile Service Listener
    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                isHidServiceReady = true
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "蓝牙HID服务已就绪", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                isHidServiceReady = false
                hidDevice = null
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "蓝牙HID服务已断开", Toast.LENGTH_SHORT).show()
                    updateConnectionState(false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initBluetooth()
        loadSettings()
        updateBackground()
        setupListeners()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        registerBluetoothReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
        bluetoothAdapter.getProfileProxy(this, profileServiceListener, BluetoothProfile.HID_DEVICE)?.let {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
    }

    /**
     * 初始化视图控件
     */
    private fun initViews() {
        etInput = findViewById(R.id.et_input)
        btnPair = findViewById(R.id.btn_pair)
        btnSettings = findViewById(R.id.btn_settings)
        btnBackspace = findViewById(R.id.btn_backspace)
        btnSend = findViewById(R.id.btn_send)
        tvDeviceStatus = findViewById(R.id.tv_device_status)
        tvEnterMode = findViewById(R.id.tv_enter_mode)
        ivBackground = findViewById(R.id.iv_background)
        deviceListContainer = findViewById(R.id.device_list_container)
        lvDevices = findViewById(R.id.lv_devices)
        pbScanning = findViewById(R.id.pb_scanning)
        tvNoDevices = findViewById(R.id.tv_no_devices)
        btnCancelScan = findViewById(R.id.btn_cancel_scan)

        // 初始化设备列表适配器
        deviceListAdapter = DeviceListAdapter(this, discoveredDevices)
        lvDevices.adapter = deviceListAdapter
    }

    /**
     * 初始化蓝牙
     */
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        }

        // 连接HID Profile服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bluetoothAdapter.getProfileProxy(this, profileServiceListener, BluetoothProfile.HID_DEVICE)
        } else {
            Toast.makeText(this, R.string.error_hid_not_supported, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        val sharedPref = getSharedPreferences("bluetooth_keyboard_settings", Context.MODE_PRIVATE)
        replaceEnterWithSend = sharedPref.getBoolean("replace_enter_with_send", false)
        customBackgroundPath = sharedPref.getString("custom_background", null)

        updateEnterModeHint()
    }

    /**
     * 更新背景
     */
    private fun updateBackground() {
        if (customBackgroundPath != null) {
            val file = File(customBackgroundPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ivBackground.setImageBitmap(bitmap)
                ivBackground.visibility = View.VISIBLE
                return
            }
        }
        ivBackground.visibility = View.GONE
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 配对按钮点击
        btnPair.setOnClickListener {
            if (checkBluetoothPermissions()) {
                showDeviceList()
            }
        }

        // 设置按钮点击
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(intent, REQUEST_SETTINGS)
        }

        // 退格按钮点击
        btnBackspace.setOnClickListener {
            if (connectedDevice != null && isHidServiceReady) {
                sendBackspace()
            } else {
                Toast.makeText(this, R.string.tip_connect_first, Toast.LENGTH_SHORT).show()
            }
        }

        // 发送按钮点击
        btnSend.setOnClickListener {
            if (connectedDevice != null && isHidServiceReady) {
                val text = etInput.text.toString()
                if (text.isNotEmpty()) {
                    sendText(text)
                    etInput.text.clear()
                }
            } else {
                Toast.makeText(this, R.string.tip_connect_first, Toast.LENGTH_SHORT).show()
            }
        }

        // 输入框文本变化监听
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (connectedDevice != null && isHidServiceReady) {
                    val text = s.toString()
                    if (text.isNotEmpty()) {
                        // 只发送最后输入的字符（避免重复发送整个文本）
                        if (text.length > 1) {
                            val lastChar = text.last()
                            sendCharacter(lastChar)
                        } else {
                            sendCharacter(text[0])
                        }
                    }
                }
            }
        })

        // 输入框编辑器动作监听（用于回车键）
        etInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && 
                 event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed)) {
                
                if (replaceEnterWithSend) {
                    // 发送回车键事件
                    if (connectedDevice != null && isHidServiceReady) {
                        sendEnter()
                        // 不清空输入框，让用户看到输入的内容
                    }
                    return@setOnEditorActionListener true
                }
            }
            false
        }

        // 设备列表项点击
        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            connectToDevice(device)
        }

        // 取消扫描按钮
        btnCancelScan.setOnClickListener {
            hideDeviceList()
        }
    }

    /**
     * 请求权限
     */
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要权限才能使用蓝牙功能", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 检查蓝牙权限
     */
    private fun checkBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 显示设备列表
     */
    private fun showDeviceList() {
        discoveredDevices.clear()
        updateDeviceList()
        
        deviceListContainer.visibility = View.VISIBLE
        startDeviceDiscovery()
    }

    /**
     * 隐藏设备列表
     */
    private fun hideDeviceList() {
        stopDeviceDiscovery()
        deviceListContainer.visibility = View.GONE
    }

    /**
     * 开始设备发现
     */
    private fun startDeviceDiscovery() {
        if (!checkBluetoothPermissions()) {
            requestPermissions()
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        isScanning = true
        updateScanningState()
        
        // 清空之前的设备列表
        discoveredDevices.clear()
        updateDeviceList()
        
        // 开始扫描
        bluetoothAdapter.startDiscovery()
    }

    /**
     * 停止设备发现
     */
    private fun stopDeviceDiscovery() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        isScanning = false
        updateScanningState()
    }

    /**
     * 更新扫描状态
     */
    private fun updateScanningState() {
        handler.post {
            if (isScanning) {
                pbScanning.visibility = View.VISIBLE
                tvNoDevices.visibility = View.GONE
            } else {
                pbScanning.visibility = View.GONE
                if (discoveredDevices.isEmpty()) {
                    tvNoDevices.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * 更新设备列表
     */
    private fun updateDeviceList() {
        handler.post {
            deviceListAdapter.notifyDataSetChanged()
        }
    }

    /**
     * 连接到设备
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (!isHidServiceReady) {
            Toast.makeText(this, "蓝牙HID服务未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        // 停止扫描
        stopDeviceDiscovery()
        
        // 显示连接中
        Toast.makeText(this, getString(R.string.connecting_to_device, device.name), Toast.LENGTH_SHORT).show()

        // 检查是否已配对
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            // 请求配对
            device.createBond()
        } else {
            // 已配对，尝试连接
            performConnection(device)
        }
    }

    /**
     * 执行连接
     */
    @SuppressLint("MissingPermission")
    private fun performConnection(device: BluetoothDevice) {
        connectedDevice = device
        updateConnectionState(true)
        hideDeviceList()
        
        Toast.makeText(this, getString(R.string.connected_to_device, device.name), Toast.LENGTH_SHORT).show()
    }

    /**
     * 断开连接
     */
    private fun disconnectDevice() {
        connectedDevice = null
        updateConnectionState(false)
        Toast.makeText(this, R.string.success_disconnected, Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新连接状态
     */
    private fun updateConnectionState(isConnected: Boolean) {
        handler.post {
            if (isConnected && connectedDevice != null) {
                @SuppressLint("MissingPermission")
                val deviceName = connectedDevice!!.name ?: "未知设备"
                tvDeviceStatus.text = getString(R.string.label_connected_device) + deviceName
                btnSend.isEnabled = true
                btnPair.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                btnPair.contentDescription = getString(R.string.btn_disconnect)
                btnPair.setOnClickListener {
                    disconnectDevice()
                }
            } else {
                tvDeviceStatus.text = getString(R.string.label_no_device_connected)
                btnSend.isEnabled = false
                btnPair.setImageResource(android.R.drawable.ic_menu_add)
                btnPair.contentDescription = getString(R.string.btn_pair_bluetooth)
                btnPair.setOnClickListener {
                    if (checkBluetoothPermissions()) {
                        showDeviceList()
                    }
                }
            }
        }
    }

    /**
     * 发送文本
     */
    private fun sendText(text: String) {
        if (!isHidServiceReady || connectedDevice == null) return

        // 这里应该实现通过HID发送文本的逻辑
        // 由于Android HID API限制，实际实现需要更多代码
        Toast.makeText(this, "发送文本: $text", Toast.LENGTH_SHORT).show()
    }

    /**
     * 发送字符
     */
    private fun sendCharacter(char: Char) {
        if (!isHidServiceReady || connectedDevice == null) return

        // 发送单个字符
        // 这里应该实现通过HID发送字符的逻辑
        Toast.makeText(this, "发送字符: $char", Toast.LENGTH_SHORT).show()
    }

    /**
     * 发送退格
     */
    private fun sendBackspace() {
        if (!isHidServiceReady || connectedDevice == null) return

        // 发送退格键
        Toast.makeText(this, R.string.toast_backspace_sent, Toast.LENGTH_SHORT).show()
    }

    /**
     * 发送回车
     */
    private fun sendEnter() {
        if (!isHidServiceReady || connectedDevice == null) return

        // 发送回车键
        Toast.makeText(this, R.string.toast_enter_sent, Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新回车模式提示
     */
    private fun updateEnterModeHint() {
        handler.post {
            tvEnterMode.text = if (replaceEnterWithSend) {
                getString(R.string.tip_enter_send)
            } else {
                getString(R.string.tip_enter_newline)
            }
        }
    }

    /**
     * 注册蓝牙广播接收器
     */
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SETTINGS && resultCode == Activity.RESULT_OK) {
            // 重新加载设置
            loadSettings()
            updateBackground()
            updateEnterModeHint()
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val REQUEST_SETTINGS = 1002
    }

    /**
     * 设备列表适配器
     */
    private inner class DeviceListAdapter(
        private val context: Context,
        private val devices: List<BluetoothDevice>
    ) : BaseAdapter() {

        @SuppressLint("MissingPermission")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
            val device = devices[position]

            val tvDeviceName = view.findViewById<TextView>(R.id.tv_device_name)
            val tvDeviceAddress = view.findViewById<TextView>(R.id.tv_device_address)
            val ivStatus = view.findViewById<View>(R.id.iv_status)

            val deviceName = device.name ?: "未知设备"
            tvDeviceName.text = deviceName
            tvDeviceAddress.text = device.address

            // 根据连接状态更新指示器
            val statusColor = if (device.bondState == BluetoothDevice.BOND_BONDED) {
                ContextCompat.getColor(context, R.color.connected)
            } else {
                ContextCompat.getColor(context, R.color.disconnected)
            }
            ivStatus.setBackgroundColor(statusColor)

            return view
        }

        override fun getItem(position: Int): Any = devices[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getCount(): Int = devices.size
    }
}