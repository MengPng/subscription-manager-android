# 订阅 Android 开发与发布

这份说明面向项目维护者。普通用户请从仓库首页进入最新 Release 下载正式 APK。

## 本地环境

- JDK 17
- Android SDK 35
- 项目自带并锁定校验值的 Gradle Wrapper

运行完整的本地质量检查：

```bash
./gradlew --no-daemon \
  testDebugUnitTest \
  lintDebug \
  lintRelease \
  assembleDebug \
  compileReleaseAndroidTestKotlin
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 持续集成

Pull Request 和 `main` 分支会运行单元测试、Debug/Release Lint、Debug 编译与原生设备测试编译。

推送符合 `vMAJOR.MINOR.PATCH` 格式的标签后，发布流程会：

1. 只构建一次已签名 Release 候选 APK；
2. 校验包名、版本名、版本号、发布证书 SHA-256 指纹和 APK SHA-256；
3. 在 Android 8.0（API 26）安装同一候选 APK，验证最低系统兼容与原生登录页启动；
4. 在 Android 15（API 35）安装同一候选 APK，使用专用测试账号验证正式 API 登录、云端账本加载、用户信息和核心导航；
5. 仅在两个设备验证都通过后，将原候选 APK 与校验文件发布到 GitHub Releases。

验证任务不会重新构建 APK，因此实际发布的文件与设备测试通过的文件保持一致。

## GitHub Actions 配置

仓库需要配置以下 Secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SMOKE_EMAIL`
- `SMOKE_PASSWORD`

正式版必须继续使用项目原始发布证书。Android 只允许使用同一证书签名的更高版本覆盖安装已发布应用。

## 发布版本

质量检查与 Pull Request 合并完成后，由项目维护者创建版本标签：

```bash
git tag v2.0.0
git push origin v2.0.0
```

GitHub Actions 会从标签生成版本名和 Android `versionCode`，完成签名、双版本设备验证，并发布 APK 与校验文件。
