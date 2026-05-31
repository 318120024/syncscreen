# SyncScreen - WebRTC远程控制系统

一个基于WebRTC和Firebase的跨平台远程控制解决方案，支持网页端和安卓App之间的屏幕共享、语音视频通话、实时聊天和远程控制。

## 功能特性

### Phase 1: 基础连接 + 屏幕共享
- ✅ 扫码配对连接
- ✅ 安卓屏幕共享到网页
- ✅ 网页屏幕共享到安卓
- ✅ 实时视频传输

### Phase 2: 语音视频 + 聊天
- ✅ 双向音视频通话
- ✅ 摄像头视频通话
- ✅ 实时文本聊天
- ✅ 静音控制

### Phase 3: 远程控制
- ✅ 网页/App控制安卓设备
- ✅ 触摸事件模拟
- ✅ 辅助功能服务支持

## 技术架构

```
网页端 (Netlify)          Firebase Realtime DB          安卓App
    |                           |                           |
    |-------- WebRTC信令 ------->|<------- WebRTC信令 -------|
    |                           |                           |
    |<=============== P2P连接 (STUN) =====================>|
                    (音视频/屏幕共享/数据通道)
```

### 技术栈

**网页端:**
- HTML5 + CSS3 + JavaScript
- WebRTC API
- Firebase SDK 9.x
- QRCode.js

**安卓端:**
- Java
- WebRTC Android SDK
- Firebase Android SDK
- Material Design Components
- ZXing (二维码)

**后端/信令:**
- Firebase Realtime Database (免费)
- Google STUN服务器 (免费)

## 快速开始

### 1. Firebase配置

1. 访问 [Firebase Console](https://console.firebase.google.com/)
2. 创建新项目
3. 启用 Realtime Database
4. 获取配置信息

#### 网页端配置

编辑 `app.js` 文件，替换Firebase配置:

```javascript
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
  databaseURL: "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID"
};
```

#### 安卓端配置

1. 在Firebase Console下载 `google-services.json`
2. 将文件放到 `android/app/` 目录

### 2. 部署网页端到Netlify

#### 方法1: 通过Git部署

```bash
# 初始化Git仓库
git init
git add .
git commit -m "Initial commit"

# 推送到GitHub
git remote add origin YOUR_GITHUB_REPO
git push -u origin main
```

在Netlify中:
1. 登录 [Netlify](https://www.netlify.com/)
2. 点击 "New site from Git"
3. 选择你的GitHub仓库
4. 设置构建配置:
   - Build command: (留空)
   - Publish directory: `.`
5. 点击 "Deploy site"

#### 方法2: 拖拽部署

1. 将 `firebase-index.html`, `app.js` 打包成文件夹
2. 直接拖拽到Netlify的部署区域

### 3. 编译安卓App

#### 环境要求
- Android Studio Arctic Fox 或更高版本
- JDK 11+
- Android SDK API 24+

#### 编译步骤

```bash
cd android

# 使用Gradle编译
./gradlew assembleDebug

# APK输出位置
# android/app/build/outputs/apk/debug/app-debug.apk
```

#### 在Android Studio中打开

1. 打开Android Studio
2. File -> Open -> 选择 `android` 目录
3. 等待Gradle同步完成
4. 连接安卓设备或启动模拟器
5. 点击 Run 按钮

## 使用说明

### 创建房间 (主机端)

1. **网页端或安卓App**: 点击"创建房间"
2. 系统生成8位房间ID
3. 显示二维码供对方扫描
4. 等待对方加入

### 加入房间 (客户端)

**方法1: 输入房间ID**
1. 切换到"加入房间"标签
2. 输入8位房间ID
3. 点击"加入房间"

**方法2: 扫描二维码**
1. 点击"扫描二维码"按钮
2. 扫描主机端显示的二维码
3. 自动加入房间

### 屏幕共享

**安卓端:**
1. 连接成功后，点击"开始屏幕共享"
2. 授予屏幕录制权限
3. 屏幕内容实时传输到对方

**网页端:**
1. 点击"共享屏幕"
2. 选择要共享的窗口/标签页/整个屏幕
3. 点击"共享"

### 语音视频通话

- **开启视频**: 同时启用摄像头和麦克风
- **开启语音**: 仅启用麦克风
- **静音**: 关闭麦克风但保持连接
- **停止**: 停止所有媒体流

### 远程控制

**安卓端 (被控端):**
1. 进入系统设置 -> 辅助功能
2. 启用 "SyncScreen" 辅助服务
3. 在App中点击"启用远程控制"

**网页端 (控制端):**
1. 点击"启用远程控制"
2. 在显示的屏幕画面上点击
3. 触摸事件发送到安卓设备

### 实时聊天

1. 在底部输入框输入消息
2. 点击"发送"或按回车键
3. 消息通过DataChannel实时传输

## 数据结构 (Firebase)

```json
{
  "rooms": {
    "ABCD1234": {
      "host": {
        "offer": {
          "type": "offer",
          "sdp": "..."
        },
        "ice": {
          "candidate1": {
            "sdpMid": "0",
            "sdpMLineIndex": 0,
            "candidate": "..."
          }
        }
      },
      "guest": {
        "answer": {
          "type": "answer",
          "sdp": "..."
        },
        "ice": {
          "candidate1": {
            "sdpMid": "0",
            "sdpMLineIndex": 0,
            "candidate": "..."
          }
        }
      }
    }
  }
}
```

## 安全规则 (Firebase)

在Firebase Console中设置以下规则:

```json
{
  "rules": {
    "rooms": {
      "$roomId": {
        ".read": true,
        ".write": true,
        ".indexOn": ["timestamp"]
      }
    }
  }
}
```

**注意**: 生产环境应该添加更严格的安全规则。

## 权限说明

### 安卓App权限

- **INTERNET**: 网络通信
- **CAMERA**: 视频通话
- **RECORD_AUDIO**: 语音通话
- **FOREGROUND_SERVICE**: 屏幕共享后台服务
- **FOREGROUND_SERVICE_MEDIA_PROJECTION**: 屏幕录制
- **SYSTEM_ALERT_WINDOW**: 悬浮窗显示
- **辅助功能服务**: 远程控制触摸模拟

### 网页端权限

- 摄像头访问
- 麦克风访问
- 屏幕共享权限

## 故障排除

### 连接失败

1. **检查Firebase配置**: 确保API Key和Database URL正确
2. **检查网络**: 确保双方都能访问互联网
3. **防火墙**: 确保UDP端口未被阻止
4. **STUN服务器**: Google STUN服务器可能在某些地区不可用

### 无法屏幕共享

**安卓端:**
- 确保授予了屏幕录制权限
- Android 10+需要前台服务权限

**网页端:**
- 使用HTTPS或localhost
- 浏览器需要支持getDisplayMedia API

### 远程控制不工作

1. 确保启用了辅助功能服务
2. Android 7.0+ 才支持手势模拟
3. 某些系统界面无法控制(安全限制)

### 视频卡顿

1. 降低视频分辨率
2. 检查网络带宽
3. 关闭不必要的应用

## 性能优化

### 网络优化
- 使用自适应码率
- 启用VP8/VP9编码
- 调整视频分辨率和帧率

### 电池优化
- 不使用时及时断开连接
- 降低屏幕共享帧率
- 使用音频模式代替视频

## 限制说明

1. **仅支持一对一连接**: 不支持多人会议
2. **需要STUN服务器**: 某些NAT环境可能需要TURN服务器
3. **不持久化数据**: 聊天记录不保存
4. **房间自动过期**: Firebase数据需要手动清理

## 扩展建议

### 添加TURN服务器

如果STUN服务器无法穿透NAT，可以添加TURN服务器:

```javascript
const rtcConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    {
      urls: 'turn:your-turn-server.com:3478',
      username: 'username',
      credential: 'password'
    }
  ]
};
```

免费TURN服务器: [Metered.ca](https://www.metered.ca/tools/openrelay/)

### 添加用户认证

使用Firebase Authentication:

```javascript
firebase.auth().signInAnonymously()
  .then((userCredential) => {
    // 用户已登录
  });
```

### 添加聊天记录

将消息保存到Firebase:

```javascript
database.ref(`rooms/${roomId}/messages`).push({
  sender: 'user1',
  message: 'Hello',
  timestamp: Date.now()
});
```

## 开源协议

MIT License

## 贡献

欢迎提交Issue和Pull Request!

## 联系方式

- 项目地址: [GitHub Repository]
- 问题反馈: [Issues]

---

**注意**: 本项目仅供学习和研究使用，请勿用于非法用途。远程控制功能需要获得设备所有者的明确授权。
