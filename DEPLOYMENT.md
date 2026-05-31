# 部署指南

## 网页端部署到Netlify

### 方法1: 通过Git部署（推荐）

#### 步骤1: 准备Git仓库

```bash
# 进入项目目录
cd d:\Project\SyncScreen

# 初始化Git仓库
git init

# 添加所有文件
git add firebase-index.html app.js README.md FIREBASE_SETUP.md

# 创建.gitignore文件
echo "node_modules/" > .gitignore
echo ".DS_Store" >> .gitignore
echo "android/local.properties" >> .gitignore
echo "android/.gradle/" >> .gitignore
echo "android/build/" >> .gitignore
echo "android/app/build/" >> .gitignore
echo "android/app/google-services.json" >> .gitignore

# 提交
git commit -m "Initial commit: SyncScreen WebRTC app"
```

#### 步骤2: 推送到GitHub

1. 在GitHub上创建新仓库
2. 推送代码：

```bash
git remote add origin https://github.com/YOUR_USERNAME/syncscreen.git
git branch -M main
git push -u origin main
```

#### 步骤3: 在Netlify部署

1. 访问 [Netlify](https://www.netlify.com/)
2. 点击 "Sign up" 或 "Log in"（可以使用GitHub账号登录）
3. 点击 "Add new site" -> "Import an existing project"
4. 选择 "GitHub"
5. 授权Netlify访问你的GitHub仓库
6. 选择 `syncscreen` 仓库
7. 配置构建设置：
   - **Branch to deploy**: main
   - **Build command**: (留空)
   - **Publish directory**: `.`
8. 点击 "Deploy site"

#### 步骤4: 配置自定义域名（可选）

1. 在Netlify站点设置中，点击 "Domain settings"
2. 点击 "Add custom domain"
3. 输入你的域名，例如 `syncscreen.yourdomain.com`
4. 按照提示配置DNS记录

### 方法2: 拖拽部署

#### 步骤1: 准备文件

创建一个文件夹，包含以下文件：
- `firebase-index.html` (重命名为 `index.html`)
- `app.js`

#### 步骤2: 部署

1. 访问 [Netlify Drop](https://app.netlify.com/drop)
2. 将文件夹拖拽到页面中
3. 等待上传完成
4. 获取生成的URL

### 方法3: Netlify CLI部署

#### 安装Netlify CLI

```bash
npm install -g netlify-cli
```

#### 登录

```bash
netlify login
```

#### 部署

```bash
# 进入项目目录
cd d:\Project\SyncScreen

# 初始化Netlify站点
netlify init

# 部署
netlify deploy --prod
```

### 配置重定向（重要）

创建 `_redirects` 文件：

```
# 将所有请求重定向到index.html（用于单页应用）
/*    /firebase-index.html   200
```

或创建 `netlify.toml` 文件：

```toml
[[redirects]]
  from = "/*"
  to = "/firebase-index.html"
  status = 200
```

### 环境变量配置（可选）

如果不想在代码中暴露Firebase配置，可以使用环境变量：

1. 在Netlify站点设置中，进入 "Environment variables"
2. 添加以下变量：
   - `FIREBASE_API_KEY`
   - `FIREBASE_AUTH_DOMAIN`
   - `FIREBASE_DATABASE_URL`
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_STORAGE_BUCKET`
   - `FIREBASE_MESSAGING_SENDER_ID`
   - `FIREBASE_APP_ID`

3. 修改 `app.js` 使用环境变量（需要构建步骤）

## 安卓App编译和分发

### 开发版本（Debug）

#### 使用Android Studio

1. 打开Android Studio
2. File -> Open -> 选择 `android` 目录
3. 等待Gradle同步完成
4. Build -> Build Bundle(s) / APK(s) -> Build APK(s)
5. APK位置: `android/app/build/outputs/apk/debug/app-debug.apk`

#### 使用命令行

```bash
cd android

# Windows
gradlew.bat assembleDebug

# Mac/Linux
./gradlew assembleDebug
```

### 发布版本（Release）

#### 步骤1: 生成签名密钥

```bash
keytool -genkey -v -keystore syncscreen-release.keystore -alias syncscreen -keyalg RSA -keysize 2048 -validity 10000
```

按提示输入：
- 密钥库密码
- 姓名、组织等信息
- 密钥密码

#### 步骤2: 配置签名

创建 `android/keystore.properties` 文件：

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=syncscreen
storeFile=../syncscreen-release.keystore
```

修改 `android/app/build.gradle`：

```gradle
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
keystoreProperties.load(new FileInputStream(keystorePropertiesFile))

android {
    ...
    
    signingConfigs {
        release {
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

#### 步骤3: 编译Release版本

```bash
cd android

# Windows
gradlew.bat assembleRelease

# Mac/Linux
./gradlew assembleRelease
```

APK位置: `android/app/build/outputs/apk/release/app-release.apk`

### 发布到Google Play Store

#### 步骤1: 创建开发者账号

1. 访问 [Google Play Console](https://play.google.com/console)
2. 支付25美元注册费
3. 完成账号设置

#### 步骤2: 创建应用

1. 点击 "创建应用"
2. 填写应用信息：
   - 应用名称: SyncScreen
   - 默认语言: 中文（简体）
   - 应用类型: 应用
   - 免费/付费: 免费

#### 步骤3: 准备商店信息

**应用图标**: 512x512 PNG

**功能图片**: 1024x500 PNG

**截图**: 至少2张，最多8张
- 手机: 16:9 或 9:16
- 平板: 16:9 或 9:16

**应用描述**:

```
SyncScreen是一款强大的远程控制应用，支持：

✨ 屏幕共享 - 实时共享你的屏幕
🎥 视频通话 - 高清音视频通话
💬 实时聊天 - 即时消息传输
🎮 远程控制 - 远程操作设备

特点：
• 基于WebRTC技术，点对点连接
• 无需注册，扫码即连
• 支持网页端和安卓端互联
• 完全免费，无广告

适用场景：
• 远程技术支持
• 在线教学演示
• 团队协作
• 家庭设备管理
```

#### 步骤4: 上传APK/AAB

推荐使用AAB格式（Android App Bundle）：

```bash
cd android
./gradlew bundleRelease
```

AAB位置: `android/app/build/outputs/bundle/release/app-release.aab`

在Google Play Console中：
1. 进入 "发布" -> "生产"
2. 点击 "创建新版本"
3. 上传AAB文件
4. 填写版本说明
5. 点击 "审核"

#### 步骤5: 内容分级

1. 完成内容分级问卷
2. 选择适当的年龄分级

#### 步骤6: 提交审核

1. 检查所有必填项
2. 点击 "提交审核"
3. 等待审核（通常1-3天）

### 其他分发方式

#### 1. 直接分发APK

将APK文件上传到：
- 自己的网站
- GitHub Releases
- 云存储服务

用户下载后需要：
1. 在设置中启用"未知来源"
2. 安装APK

#### 2. 第三方应用商店

- 华为应用市场
- 小米应用商店
- OPPO软件商店
- vivo应用商店
- 应用宝（腾讯）
- 豌豆荚

每个商店都有自己的审核流程和要求。

## 更新和维护

### 网页端更新

#### Git部署方式

```bash
# 修改代码后
git add .
git commit -m "Update: 描述更新内容"
git push

# Netlify会自动检测并重新部署
```

#### 手动部署方式

1. 在Netlify站点设置中，进入 "Deploys"
2. 拖拽新文件到 "Drag and drop" 区域

### 安卓App更新

1. 修改 `android/app/build.gradle` 中的版本号：

```gradle
defaultConfig {
    versionCode 2  // 增加版本号
    versionName "1.1"  // 更新版本名称
}
```

2. 重新编译Release版本
3. 上传到Google Play Console
4. 提交审核

### 版本管理建议

使用语义化版本号：`主版本.次版本.修订号`

- **主版本**: 重大功能变更或不兼容的API修改
- **次版本**: 新增功能，向下兼容
- **修订号**: Bug修复

例如：
- 1.0.0 - 初始版本
- 1.1.0 - 添加新功能
- 1.1.1 - 修复Bug

## 监控和分析

### Netlify Analytics

1. 在Netlify站点设置中启用Analytics
2. 查看访问量、带宽使用等数据

### Google Analytics（可选）

在 `firebase-index.html` 中添加：

```html
<!-- Google Analytics -->
<script async src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());
  gtag('config', 'GA_MEASUREMENT_ID');
</script>
```

### Firebase Analytics（安卓）

已在项目中集成，自动收集基本数据。

在Firebase Console中查看：
- 用户活跃度
- 屏幕浏览量
- 崩溃报告

## 故障排除

### Netlify部署失败

1. 检查文件名是否正确
2. 查看部署日志
3. 确认文件路径正确

### 安卓编译失败

1. 清理项目：`./gradlew clean`
2. 检查 `google-services.json` 是否存在
3. 更新Gradle版本
4. 检查依赖版本兼容性

### Google Play审核被拒

常见原因：
- 缺少隐私政策
- 权限说明不清楚
- 截图不符合要求
- 应用崩溃或功能不完整

解决方法：
1. 仔细阅读拒绝原因
2. 修复问题
3. 重新提交

## 成本估算

### 免费方案

- **Netlify**: 100GB带宽/月（免费）
- **Firebase**: 1GB存储 + 10GB下载/月（免费）
- **Google STUN**: 完全免费

适合：个人项目、小规模使用

### 付费方案

如果超出免费额度：

- **Netlify Pro**: $19/月（400GB带宽）
- **Firebase Blaze**: 按量付费
  - 存储: $5/GB/月
  - 下载: $1/GB
- **TURN服务器**: $10-50/月

## 完成！

现在你的SyncScreen应用已经成功部署，用户可以通过以下方式访问：

- **网页端**: https://your-site.netlify.app
- **安卓端**: 从Google Play或直接下载APK安装

祝你使用愉快！
