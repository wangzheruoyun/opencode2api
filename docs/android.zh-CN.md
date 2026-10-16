# Android 客户端

把 opencode2api 的网关和它的 13 页管理台搬到 Android 上：网关仍然原样跑，界面换成一个原生 Compose 应用，外观取自 [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 所用的 miuix 组件库。

## 网关如何进入 APK

- 网关用 Go 交叉编译成 `libopencode2api.so`，放在 `jniLibs/arm64-v8a/` 与 `jniLibs/x86_64/`。它不是 JNI 库，只是借这个位置走包管理器的解包流程。
- **必须 `CGO_ENABLED=1`**。纯 Go 构建链接 Go 自带的解析器，它读 `/etc/resolv.conf`——Android 没有这个文件，域名解析走 netd 的 Binder 调用。这样的二进制会退回 `127.0.0.1:53`，`opencode.ai` 一个都解析不了。用 NDK 的 bionic clang 链接后，进程用的是平台解析器。
- 不能当 asset 放进 APK 再 `chmod`：Android 10 起禁止执行应用数据目录里的文件。只有 `nativeLibraryDir` 下形如 `lib*.so` 的文件会被解包到磁盘并带上可执行位，而这一步需要清单里的 `android:extractNativeLibs="true"`——它由 `packaging.jniLibs.useLegacyPackaging` 写入合并清单，不写在源清单里（写两处构建会告警，所以改由 CI 从产物里读回来验证）。

## 界面

- 13 页 WebUI（`overview`、`config`、`logs`、`model-quotas`、`guide`、`access`、`usage`、`playground`、`diagnostics`、`account`、`api-keys`、`rotation`、`availability`）逐一改写成原生屏幕，没有 WebView 外壳。另加一页「关于」。
- 底部五个标签是承载实时状态的那五页，长尾工具放在「更多」里，和 WebUI 的导航分组一致。
- 两套皮肤：miuix（默认）与 Material 3。屏幕只写一次——`ui/component/` 下是一套中性的 `O2A*` 词表，内部按 `LocalUiMode` 分派到 miuix 或 Material 实现。
- 管理端登录态由 `AdminClient` 持有（Cookie + CSRF），在屏幕之间共享，所以在一页登录等于全应用登录。

## 运行模式

- **Root**：用 `su -c` 启动。先让 shell 记下自己的 PID 再 `exec`，因为 JVM 看到的 PID 属于 `su`，杀 `su` 只会留下一个仍占着端口的孤儿进程。
- **普通应用**：直接 `ProcessBuilder` 启动，退出用 SIGTERM，超时后 SIGKILL。
- **AUTO**：能拿到 root 就用 root，否则回退到普通模式并在日志里说明。检测失败一律当「没有 root」，不抛异常。
- 进程只有一个，由前台服务持有；UI 只观察状态、下发启停。

## 构建

只走 GitHub Actions（`.github/workflows/android.yml`），本机不编译。推送后 CI 会：

1. 安装固定版本的 NDK、`platforms;android-37.0` 与 build-tools 37.0.0。API 37 只以 minor 版本发布，没有 `android-37` 这个包，所以 `compileSdk` 必须写成 release/minor 形式。
2. 为两个 ABI 交叉编译网关，并用 `readelf` 确认是 PIE 且没有链接 `libgo`。
3. `assembleDebug`。
4. 从产物里读回 `extractNativeLibs`，并确认两个 ABI 的 `.so` 都在包里——这两样丢了都是只在真机上才暴露的问题。
5. 上传 debug APK。

## 已知限制

- **只出 debug 签名**，没有 release keystore。要发布得先配签名密钥。
- `targetSdk` 是 36，`compileSdk` 是 37：miuix 0.9.4 与 material-kolor 5.0.1 都要求消费者按 37 编译。
- **尚未在真机上安装运行过。** 目前确认的是编译通过、APK 结构正确、静态检查通过；解包执行、DNS 解析、root 授权弹窗、后台启动前台服务这几件事都还没有实测。
- 没有单元测试和 UI 测试，Playground 与管理页需要网关在运行且已登录。
- 首次启动会在应用私有目录生成 `config.json`。网关解析配置时用了 `DisallowUnknownFields`，所以模板里每个键都必须是真实的 json tag，加错一个键就会启动失败。当前模板的键已逐个对照 `internal/config` 的结构体标签核对过，但应用没有对上游做版本对齐：上游改了配置键名，这里会先于任何人发现。
- 13 页里的配置编辑器按点分路径读写 JSON，不认识的字段原样保留，但界面上只暴露了其中一部分。
