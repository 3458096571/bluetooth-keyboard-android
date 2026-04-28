# GitHub Actions 自动化构建指南

## 📋 快速开始（5分钟获取APK）

### 步骤 1：创建 GitHub 仓库
1. 登录 GitHub (https://github.com)
2. 点击右上角 "+" → "New repository"
3. 仓库名：`bluetooth-keyboard-android`
4. 描述：`Android Bluetooth Keyboard App`
5. 选择 **Public**（公开）
6. **不要**选择 "Add a README file"（我们已有）
7. 点击 "Create repository"

### 步骤 2：上传项目文件
**方法 A：使用 Git 命令行**
```bash
# 1. 克隆空仓库
git clone https://github.com/你的用户名/bluetooth-keyboard-android.git

# 2. 复制项目文件到仓库
cp -r "C:\Users\LENOVO\.molili\workspaces\default\蓝牙键盘应用"/* bluetooth-keyboard-android/

# 3. 提交并推送
cd bluetooth-keyboard-android
git add .
git commit -m "添加蓝牙键盘Android应用"
git push origin main
```

**方法 B：使用网页上传**
1. 进入新建的仓库页面
2. 点击 "Add file" → "Upload files"
3. 选择我发给你的 ZIP 文件（解压后所有文件）
4. 点击 "Commit changes"

### 步骤 3：触发 GitHub Actions 构建
1. 进入仓库 → "Actions" 标签页
2. 选择 "Build and Release APK" workflow
3. 点击右侧 "Run workflow" → "Run workflow"
4. 等待约 5 分钟完成构建

### 步骤 4：下载 APK
1. 构建完成后，进入 "Actions" → 找到完成的 workflow
2. 点击 workflow 名称
3. 在底部 "Artifacts" 部分下载 `bluetooth-keyboard-apk`
4. **或者** 进入 "Releases" 标签页下载已发布的版本

---

## 🔧 GitHub Actions 配置说明

### 已配置的 Workflow 文件
`.github/workflows/build-and-release.yml`

**触发条件**：
- 推送到 main/master 分支时自动构建
- 创建 Pull Request 时自动构建
- 支持手动触发构建

**构建流程**：
1. **环境设置**：Ubuntu Linux + JDK 11
2. **依赖缓存**：Gradle 依赖加速
3. **编译构建**：生成 Debug APK
4. **打包上传**：发布到 GitHub Releases

### 生成的 APK 信息
- **文件名**：`app-debug.apk`
- **应用名称**：蓝牙键盘
- **包名**：`com.example.bluetoothkeyboard`
- **版本**：1.0.构建号（自动递增）

---

## 📱 手机端快速安装方法

### 方法一：GitHub Releases 直接下载
1. 手机浏览器访问：`https://github.com/你的用户名/bluetooth-keyboard-android/releases`
2. 点击最新版本
3. 找到 `.apk` 文件下载
4. 安装时选择"允许安装未知来源应用"

### 方法二：通过 Artifacts 下载
1. 手机浏览器访问 Actions 页面
2. 找到完成的 workflow
3. 下载 Artifacts 中的 APK

### 方法三：使用 GitHub Mobile App
1. 安装 GitHub Mobile App
2. 登录你的账户
3. 进入仓库 → Releases
4. 直接下载安装

---

## 🔄 后续更新与维护

### 代码更新后重新构建
```bash
# 修改代码后
git add .
git commit -m "更新功能描述"
git push origin main
# GitHub 会自动构建新版本
```

### 查看构建状态
- 访问：`https://github.com/你的用户名/bluetooth-keyboard-android/actions`
- 绿色勾号 ✅ 表示构建成功
- 红色叉号 ❌ 表示构建失败（点击查看详细日志）

---

## 🛠️ 故障排除

### 构建失败常见原因

#### 1. Gradle 依赖下载失败
**解决方法**：
- 检查网络连接
- 重试构建工作流
- 查看 workflow 日志中的错误信息

#### 2. APK 签名问题
**解决方法**：
- 使用默认 debug 密钥库（已配置）
- 如需发布版本，需配置正式签名

#### 3. 权限不足
**解决方法**：
- 确保有仓库的推送权限
- 检查 GitHub Token 权限

### 手动构建命令
如果 GitHub Actions 有问题，可以在本地使用：
```bash
./gradlew assembleDebug
# 生成的 APK 在: app/build/outputs/apk/debug/
```

---

## 📈 高级配置（可选）

### 启用 CodeQL 代码安全扫描
在 `.github/workflows/` 添加：
```yaml
name: CodeQL
on: [push, pull_request]
jobs:
  analyze:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Initialize CodeQL
      uses: github/codeql-action/init@v2
      with:
        languages: kotlin
    - name: Autobuild
      uses: github/codeql-action/autobuild@v2
    - name: Perform CodeQL Analysis
      uses: github/codeql-action/analyze@v2
```

### 添加测试自动化
```yaml
name: Run Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Run unit tests
      run: ./gradlew test
    - name: Run instrumented tests
      run: ./gradlew connectedAndroidTest
```

---

## 🌐 在线演示链接生成

构建成功后，你还可以：
1. **生成二维码**：通过 QR Code 分享 APK 下载链接
2. **创建短链接**：使用 URL 缩短服务
3. **分享 Releases 页面**：直接分享 GitHub 链接

**示例二维码生成**：
```
https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://github.com/用户名/仓库/releases/latest/download/app-debug.apk
```

---

## 🎯 最佳实践建议

### 1. 版本管理
- 使用语义化版本号（SemVer）
- 每次重大更新创建新 Release
- 保留旧版本 APK 供回滚

### 2. 文档维护
- 更新 README.md 中的最新功能
- 记录已知问题和解决方案
- 提供用户反馈渠道

### 3. 质量控制
- 定期运行自动化测试
- 监控构建成功率
- 收集用户使用反馈

---

## 📞 技术支持

遇到问题可以：
1. **查看构建日志**：GitHub Actions → 点击构建 → 查看详细日志
2. **搜索现有 Issue**：GitHub Issues 标签页
3. **创建新 Issue**：描述具体问题
4. **查阅文档**：README.md 和本指南

---

**✨ 现在就去 GitHub 创建仓库，5分钟后就能获得 APK 安装包！**