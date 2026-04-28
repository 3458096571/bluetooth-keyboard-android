package com.example.bluetoothkeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 设置活动 - 应用设置界面
 * 功能：键盘设置、背景自定义等
 */
class SettingsActivity : AppCompatActivity() {

    // UI 控件
    private lateinit var switchReplaceEnter: SwitchCompat
    private lateinit var ivCurrentBackground: ImageView
    private lateinit var btnSelectBackground: Button
    private lateinit var btnResetBackground: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivBackground: ImageView

    // 设置相关
    private var replaceEnterWithSend = false
    private var customBackgroundPath: String? = null
    private var tempImagePath: String? = null

    // Activity结果启动器
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelection(it) }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    handleImageFile(file)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        updateBackgroundPreview()
        setupListeners()
    }

    /**
     * 初始化视图控件
     */
    private fun initViews() {
        switchReplaceEnter = findViewById(R.id.switch_replace_enter)
        ivCurrentBackground = findViewById(R.id.iv_current_background)
        btnSelectBackground = findViewById(R.id.btn_select_background)
        btnResetBackground = findViewById(R.id.btn_reset_background)
        btnBack = findViewById(R.id.btn_back)
        ivBackground = findViewById(R.id.iv_background)
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        val sharedPref = getSharedPreferences("bluetooth_keyboard_settings", MODE_PRIVATE)
        replaceEnterWithSend = sharedPref.getBoolean("replace_enter_with_send", false)
        customBackgroundPath = sharedPref.getString("custom_background", null)

        switchReplaceEnter.isChecked = replaceEnterWithSend
    }

    /**
     * 更新背景预览
     */
    private fun updateBackgroundPreview() {
        if (customBackgroundPath != null) {
            val file = File(customBackgroundPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ivCurrentBackground.setImageBitmap(bitmap)
                ivBackground.setImageBitmap(bitmap)
                ivBackground.visibility = ImageView.VISIBLE
                return
            }
        }
        
        // 显示默认背景
        ivCurrentBackground.setImageResource(R.drawable.gradient_background)
        ivBackground.visibility = ImageView.GONE
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回按钮
        btnBack.setOnClickListener {
            saveSettings()
            setResult(Activity.RESULT_OK)
            finish()
        }

        // 回车键替换开关
        switchReplaceEnter.setOnCheckedChangeListener { _, isChecked ->
            replaceEnterWithSend = isChecked
        }

        // 选择背景按钮
        btnSelectBackground.setOnClickListener {
            showBackgroundSelectionDialog()
        }

        // 重置背景按钮
        btnResetBackground.setOnClickListener {
            resetBackground()
        }
    }

    /**
     * 显示背景选择对话框
     */
    private fun showBackgroundSelectionDialog() {
        val options = arrayOf("从相册选择", "拍照", "取消")
        
        AlertDialog.Builder(this)
            .setTitle("选择背景图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> selectFromGallery()
                    1 -> takePhoto()
                    2 -> {}
                }
            }
            .show()
    }

    /**
     * 从相册选择
     */
    private fun selectFromGallery() {
        if (checkStoragePermission()) {
            getContent.launch("image/*")
        }
    }

    /**
     * 拍照
     */
    private fun takePhoto() {
        if (checkCameraPermission()) {
            tempImagePath = createImageFile().absolutePath
            val photoURI = FileProvider.getUriForFile(
                this,
                "com.example.bluetoothkeyboard.fileprovider",
                File(tempImagePath!!)
            )
            takePicture.launch(photoURI)
        }
    }

    /**
     * 处理图片选择
     */
    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            bitmap?.let { saveAndSetBackground(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * 处理图片文件
     */
    private fun handleImageFile(file: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.let { saveAndSetBackground(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * 保存并设置背景
     */
    private fun saveAndSetBackground(bitmap: Bitmap) {
        try {
            // 创建应用私有目录
            val backgroundDir = File(filesDir, "backgrounds")
            if (!backgroundDir.exists()) {
                backgroundDir.mkdirs()
            }
            
            // 生成文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backgroundFile = File(backgroundDir, "background_$timestamp.jpg")
            
            // 保存图片
            val outputStream = FileOutputStream(backgroundFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // 保存路径到设置
            customBackgroundPath = backgroundFile.absolutePath
            
            // 更新预览
            updateBackgroundPreview()
            
            Toast.makeText(this, R.string.success_background_updated, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * 重置背景
     */
    private fun resetBackground() {
        customBackgroundPath = null
        
        // 删除已保存的图片文件
        val backgroundDir = File(filesDir, "backgrounds")
        if (backgroundDir.exists() && backgroundDir.isDirectory) {
            backgroundDir.listFiles()?.forEach { it.delete() }
        }
        
        // 更新预览
        updateBackgroundPreview()
        
        Toast.makeText(this, R.string.success_background_reset, Toast.LENGTH_SHORT).show()
    }

    /**
     * 保存设置
     */
    private fun saveSettings() {
        val sharedPref = getSharedPreferences("bluetooth_keyboard_settings", MODE_PRIVATE)
        with (sharedPref.edit()) {
            putBoolean("replace_enter_with_send", replaceEnterWithSend)
            if (customBackgroundPath != null) {
                putString("custom_background", customBackgroundPath)
            } else {
                remove("custom_background")
            }
            apply()
        }
    }

    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
            false
        }
    }

    /**
     * 检查相机权限
     */
    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    selectFromGallery()
                }
            }
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                }
            }
        }
    }

    /**
     * 创建临时图片文件
     */
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    /**
     * 按返回键时的处理
     */
    override fun onBackPressed() {
        saveSettings()
        setResult(Activity.RESULT_OK)
        super.onBackPressed()
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 2001
        private const val REQUEST_CAMERA_PERMISSION = 2002
    }
}