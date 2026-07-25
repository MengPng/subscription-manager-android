# 订阅 Android

“订阅”正式 Android 客户端，应用标识为 `com.netkaize.subscription`。

客户端通过安全的 Android WebView 加载 `https://subscription.netkaize.com/`。账号登录、订阅数据和云端同步继续由正式服务器处理；常规页面和功能更新部署到服务器后会自动在客户端生效，无需重新发布 APK。

## 功能

- 使用正式账号登录并保持会话
- 云端订阅数据同步
- HTTPS 强制校验，拒绝明文流量和无效证书
- 官网等外部链接交给系统浏览器打开
- 支持网页中的图片/文件选择
- 断网提示与重新连接
- Android 8.0（API 26）及以上

## 本地构建

需要 JDK 17 和 Android SDK 35：

```bash
./gradlew assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## GitHub Release

推送 `v*` 标签时，GitHub Actions 会：

1. 编译、运行单元测试与 Android Lint；
2. 在 Android 模拟器安装调试 APK，并使用测试账号验证正式登录；
3. 使用固定发布证书签名 Release APK；
4. 发布 APK 与 SHA-256 校验文件到 GitHub Releases。

仓库需要配置以下 GitHub Actions Secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SMOKE_EMAIL`
- `SMOKE_PASSWORD`

发布新版本：

```bash
git tag v1.0.1
git push origin v1.0.1
```

不要更换发布证书。Android 只允许用同一证书签名的新版本覆盖安装旧版本。

