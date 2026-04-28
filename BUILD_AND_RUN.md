# 构建和运行指南

## 环境要求

### 开发环境
- Android Studio 4.2+
- Gradle 7.0+
- Java 8+
- Android SDK 31

### 运行环境
- Android 9.0+ (API 28+) 设备
- 设备需要支持蓝牙 HID
- 物理设备（模拟器不支持蓝牙 HID 功能）

## 构建步骤

### 1. 导入项目到 Android Studio
```bash
# 打开 Android Studio
# 选择 "Open an existing project"
# 导航到项目根目录并打开
```

### 2. 等待 Gradle 同步
- Android Studio 会自动下载依赖项
- 确保网络连接正常
- 可能需要配置代理（如有需要）

### 3. 配置构建变体
1. 打开 `Build Variants` 工具窗口
2. 选择 `debug` 变体
3. 确保选择了正确的模块 (`app`)

### 4. 构建 APK
```bash
# 方式1：通过 Android Studio
# 点击 Build > Make Project

# 方式2：命令行
./gradlew assembleDebug
```

## 运行步骤

### 1. 连接设备
1. 确保 Android 设备已开启开发者模式
2. 启用 USB 调试
3. 通过 USB 线连接设备到电脑

### 2. 运行应用
```bash
# 方式1：通过 Android Studio
# 点击 Run > Run 'app'

# 方式2：命令行
./gradlew installDebug
```

### 3. 设备上操作
1. 首次运行需要授予权限
2. 确保设备蓝牙已开启
3. 搜索并连接目标设备

## 调试

### 日志查看
```bash
# 查看应用日志
adb logcat -s "蓝牙键盘"

# 查看蓝牙相关日志
adb logcat | grep -i bluetooth
```

### 常见问题解决

#### 问题1：构建失败，Gradle 同步错误
**解决**：
1. 检查网络连接
2. 清理 Gradle 缓存：`./gradlew clean`
3. 重新同步：File > Sync Project with Gradle Files

#### 问题2：无法安装到设备
**解决**：
1. 检查设备是否已连接：`adb devices`
2. 确认设备已开启 USB 调试
3. 重新插拔 USB 线

#### 问题3：蓝牙功能无法使用
**解决**：
1. 确认设备支持蓝牙 HID
2. 检查是否已授予所有权限
3. 重启设备和应用

## 测试

### 单元测试
```bash
# 运行单元测试
./gradlew testDebugUnitTest
```

### 功能测试
1. 蓝牙配对功能
2. 文本输入和传输
3. 设置保存和恢复
4. 自定义背景功能

## 发布

### 生成发布 APK
```bash
./gradlew assembleRelease
```

### 签名配置
1. 创建密钥库文件
2. 配置 `signingConfigs` 在 `build.gradle`
3. 设置 `buildTypes.release.signingConfig`

### 发布到应用商店
1. 优化 APK 大小
2. 添加应用图标
3. 准备截图和描述
4. 提交到 Google Play Store

## 性能优化建议

### 构建优化
1. 启用 R8 代码优化
2. 配置资源压缩
3. 使用 ProGuard 规则

### 运行优化
1. 减少不必要的蓝牙扫描
2. 优化文本传输算法
3. 合理使用内存缓存

## 安全注意事项

### 权限最小化
1. 只请求必要的权限
2. 运行时请求危险权限
3. 提供权限使用说明

### 数据安全
1. 用户设置本地存储加密
2. 避免敏感信息日志
3. 安全处理文件操作

---

**注意**：本应用需要实际 Android 设备测试蓝牙 HID 功能，模拟器无法提供完整的蓝牙支持。