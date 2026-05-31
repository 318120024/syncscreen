# Firebase配置指南

## 1. 创建Firebase项目

1. 访问 [Firebase Console](https://console.firebase.google.com/)
2. 点击"添加项目"
3. 输入项目名称，例如 "SyncScreen"
4. 选择是否启用Google Analytics（可选）
5. 点击"创建项目"

## 2. 启用Realtime Database

1. 在Firebase Console左侧菜单中，点击"Realtime Database"
2. 点击"创建数据库"
3. 选择数据库位置（建议选择离用户最近的区域）
4. 选择安全规则模式：
   - **测试模式**: 开发阶段使用（30天后自动锁定）
   - **锁定模式**: 生产环境使用（需要配置规则）
5. 点击"启用"

## 3. 配置安全规则

在Realtime Database页面，点击"规则"标签，设置以下规则：

### 开发环境（测试用）

```json
{
  "rules": {
    "rooms": {
      "$roomId": {
        ".read": true,
        ".write": true
      }
    }
  }
}
```

### 生产环境（推荐）

```json
{
  "rules": {
    "rooms": {
      "$roomId": {
        ".read": true,
        ".write": true,
        ".validate": "newData.hasChildren(['host']) || newData.hasChildren(['guest'])",
        "host": {
          ".validate": "newData.hasChildren(['offer'])"
        },
        "guest": {
          ".validate": "newData.hasChildren(['answer'])"
        }
      }
    }
  }
}
```

点击"发布"保存规则。

## 4. 获取网页端配置

1. 在Firebase Console首页，点击"</>"图标（添加Web应用）
2. 输入应用昵称，例如 "SyncScreen Web"
3. 不需要勾选"Firebase Hosting"
4. 点击"注册应用"
5. 复制显示的配置代码

配置示例：
```javascript
const firebaseConfig = {
  apiKey: "AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  authDomain: "syncscreen-xxxxx.firebaseapp.com",
  databaseURL: "https://syncscreen-xxxxx-default-rtdb.firebaseio.com",
  projectId: "syncscreen-xxxxx",
  storageBucket: "syncscreen-xxxxx.appspot.com",
  messagingSenderId: "123456789012",
  appId: "1:123456789012:web:abcdef1234567890"
};
```

6. 将此配置粘贴到 `app.js` 文件中替换现有配置

## 5. 获取安卓端配置

1. 在Firebase Console首页，点击Android图标（添加Android应用）
2. 输入Android包名: `com.syncscreen`
3. 输入应用昵称（可选）: "SyncScreen Android"
4. 输入调试签名证书SHA-1（可选，用于Google登录等功能）
5. 点击"注册应用"
6. 下载 `google-services.json` 文件
7. 将文件放到 `android/app/` 目录下

### 获取SHA-1证书指纹（可选）

```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Mac/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

## 6. 配置Firebase数据库索引（可选）

如果需要查询优化，可以添加索引：

```json
{
  "rules": {
    "rooms": {
      ".indexOn": ["timestamp", "status"]
    }
  }
}
```

## 7. 设置数据保留策略（推荐）

为了避免数据库无限增长，建议设置自动清理规则：

### 方法1: 使用Firebase Functions（需要付费计划）

```javascript
const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// 每天清理超过24小时的房间
exports.cleanupOldRooms = functions.pubsub
  .schedule('every 24 hours')
  .onRun(async (context) => {
    const db = admin.database();
    const now = Date.now();
    const cutoff = now - 24 * 60 * 60 * 1000; // 24小时前

    const snapshot = await db.ref('rooms').once('value');
    const updates = {};

    snapshot.forEach((child) => {
      const roomData = child.val();
      if (roomData.timestamp && roomData.timestamp < cutoff) {
        updates[child.key] = null; // 删除
      }
    });

    return db.ref('rooms').update(updates);
  });
```

### 方法2: 手动清理

定期在Firebase Console中手动删除旧房间数据。

## 8. 监控使用情况

1. 在Firebase Console中查看"使用情况"标签
2. 监控以下指标：
   - **连接数**: 同时在线用户数
   - **存储**: 数据库大小
   - **下载**: 数据传输量
   - **写入**: 数据库写入次数

### Firebase免费额度（Spark计划）

- **Realtime Database存储**: 1 GB
- **下载**: 10 GB/月
- **连接数**: 100个同时连接

如果超出免费额度，需要升级到Blaze计划（按量付费）。

## 9. 安全建议

### 生产环境安全规则

```json
{
  "rules": {
    "rooms": {
      "$roomId": {
        ".read": true,
        ".write": "!data.exists() || data.child('timestamp').val() > (now - 3600000)",
        ".validate": "newData.hasChildren(['timestamp'])",
        "timestamp": {
          ".validate": "newData.isNumber() && newData.val() <= now"
        },
        "host": {
          ".write": "!data.exists()",
          "offer": {
            ".validate": "newData.hasChildren(['type', 'sdp'])"
          },
          "ice": {
            "$candidateId": {
              ".validate": "newData.hasChildren(['sdpMid', 'sdpMLineIndex', 'candidate'])"
            }
          }
        },
        "guest": {
          ".write": "root.child('rooms').child($roomId).child('host').exists()",
          "answer": {
            ".validate": "newData.hasChildren(['type', 'sdp'])"
          },
          "ice": {
            "$candidateId": {
              ".validate": "newData.hasChildren(['sdpMid', 'sdpMLineIndex', 'candidate'])"
            }
          }
        }
      }
    }
  }
}
```

### 关键安全措施

1. **限制写入权限**: 只允许创建新房间和更新自己的数据
2. **时间戳验证**: 防止创建未来时间的数据
3. **数据结构验证**: 确保数据格式正确
4. **房间过期**: 1小时后不允许修改
5. **启用App Check**: 防止滥用API

## 10. 启用App Check（推荐）

App Check可以防止未授权的客户端访问你的Firebase资源。

### 网页端

1. 在Firebase Console中启用App Check
2. 注册reCAPTCHA v3站点
3. 在网页中添加：

```html
<script src="https://www.gstatic.com/firebasejs/9.22.0/firebase-app-check-compat.js"></script>
<script>
  const appCheck = firebase.appCheck();
  appCheck.activate('YOUR_RECAPTCHA_SITE_KEY', true);
</script>
```

### 安卓端

1. 在Firebase Console中启用App Check
2. 注册Play Integrity API
3. 在 `build.gradle` 中添加：

```gradle
implementation 'com.google.firebase:firebase-appcheck-playintegrity:17.0.0'
```

4. 在Application类中初始化：

```java
FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
firebaseAppCheck.installAppCheckProviderFactory(
    PlayIntegrityAppCheckProviderFactory.getInstance());
```

## 11. 故障排除

### 问题1: "Permission denied"错误

**原因**: 安全规则配置错误
**解决**: 检查Firebase Console中的安全规则，确保允许读写

### 问题2: 连接超时

**原因**: 数据库URL错误或网络问题
**解决**: 
- 检查 `databaseURL` 是否正确
- 确保网络可以访问Firebase服务器
- 检查防火墙设置

### 问题3: "Quota exceeded"错误

**原因**: 超出免费额度
**解决**: 
- 升级到Blaze计划
- 优化数据使用（减少写入次数）
- 清理旧数据

### 问题4: 数据未实时更新

**原因**: 监听器未正确设置
**解决**: 
- 检查 `.on('value')` 或 `.addValueEventListener()` 是否正确
- 确保没有调用 `.off()` 或 `removeEventListener()`

## 12. 测试配置

### 测试网页端

1. 打开浏览器开发者工具（F12）
2. 查看Console是否有Firebase初始化成功的消息
3. 在Network标签中查看是否有到Firebase的请求

### 测试安卓端

1. 在Android Studio中查看Logcat
2. 搜索 "Firebase" 关键字
3. 确认看到 "Firebase initialized" 消息

### 测试数据库连接

在浏览器Console中运行：

```javascript
firebase.database().ref('test').set({
  message: 'Hello Firebase',
  timestamp: Date.now()
}).then(() => {
  console.log('写入成功');
}).catch((error) => {
  console.error('写入失败:', error);
});
```

在Firebase Console的Realtime Database中应该能看到 `test` 节点。

## 13. 备份和恢复

### 导出数据

1. 在Firebase Console中打开Realtime Database
2. 点击右上角的"..."菜单
3. 选择"导出JSON"
4. 保存文件

### 导入数据

1. 点击"..."菜单
2. 选择"导入JSON"
3. 选择之前导出的文件

## 完成！

配置完成后，你的SyncScreen应用应该可以正常使用Firebase进行信令交换了。

如有问题，请查看Firebase文档: https://firebase.google.com/docs
