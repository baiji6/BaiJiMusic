# 白姬音乐 (BaiJi Music)

一个 Android 平台的第三方音乐播放器，对接 QQ 音乐接口，包含 Kotlin 应用层与 C/C++ 原生层。
原生层复刻了 QQ 音乐客户端在请求签名、加密参数、设备信息等方面的算法（hash33、zzc sign、QRC 解密、qimei、3DES、comm 组包等），
应用层基于 Media3 / ExoPlayer 完成播放与下载。

> 本项目为个人学习与技术研究用途，非官方客户端，与腾讯 / QQ 音乐无任何关联。

## 功能特性

- **QQ 扫码登录**：登录态与 Cookie 持久化保存，可查看当前登录 UID
- **搜索**：按歌曲 / 歌手 / 专辑搜索，分页加载（下拉刷新）
- **在线播放**：基于 Media3 ExoPlayer 后台播放，前台服务 + 通知栏控制
- **音质切换**：默认播放音质可持久化设置，支持多档音质选项
- **歌曲下载**：支持自定义下载目录（SAF 目录授权后持久化）
- **歌单与播放历史**：本地歌单管理与历史记录
- **日志查看器**：应用内直接查看运行日志，便于排查接口问题

## 技术栈

| 层次 | 使用技术 |
| --- | --- |
| 语言 | Kotlin（应用层） / C、C++17（原生层） |
| 构建 | Gradle 8.x + Android Gradle Plugin 8.7.3，CMake 3.22.1 + NDK 27.2 |
| 网络 | OkHttp 4.12、Okio 3.9、Gson 2.11 |
| 播放 | AndroidX Media3 (ExoPlayer / common / ui) 1.4.1 |
| UI | AppCompat、Material Components、ConstraintLayout、RecyclerView、ViewBinding、Glide |
| 异步 | Kotlin Coroutines、Lifecycle KTX |
| 加密 | mbedTLS（以 `mbedcrypto` 静态库形式内置） |

- `minSdk 24` / `targetSdk 36` / `compileSdk 36`
- ABI：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`
- 原生库已按 16KB page size 对齐（`-Wl,-z,max-page-size=16384`），兼容 Android 15 的 16KB 页大小设备

## 项目结构

```
app/src/main/
├── java/com/baiji/music/
│   ├── App.kt                     # Application 入口，持有全局 Api
│   ├── network/                   # QQMusicClient、SongApi、SearchApi、LoginApi、Model
│   ├── native/                    # SecurityApi、QimeiApi、DeviceApi（JNI 封装）
│   ├── player/                    # PlayerController（单例播放控制）、PlaybackService
│   ├── download/                  # DownloadManager、DownloadLocation
│   ├── data/                      # PlaylistStore、HistoryStore、DeviceManager、Credential
│   ├── ui/                        # MainActivity、PlayerActivity、LoginActivity、
│   │                              # DownloadActivity、PlaylistActivity、
│   │                              # SearchFragment、MyFragment、SettingsFragment、
│   │                              # SongAdapter、LogViewerActivity
│   └── util/AppLog.kt
├── cpp/
│   ├── CMakeLists.txt             # 构建 qqmusicapi 共享库
│   ├── qqapi/
│   │   ├── qqapi.h                # 原生能力对外声明
│   │   ├── comm.c                 # comm 请求体组装
│   │   ├── jni_bridge.cpp         # JNI 桥接
│   │   └── alg/                   # hash33 / zzc_sign / tripledes / qrc /
│   │                              # qimei / device / base64 / hex / random
│   └── third_party/mbedtls/       # 内置 mbedTLS 源码
└── res/                           # 布局、字符串、图标等资源
```

## 构建

前置条件：

- JDK 17
- Android SDK（`compileSdk 36`）
- Android NDK `27.2.12479018` 与 CMake `3.22.1`（构建原生库必需）

步骤：

```bash
# 1. 配置本机 SDK 路径
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 2. 构建 Debug 包
./gradlew :app:assembleDebug

# 3. 构建 Release 包
./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/`。

## 自动构建 APK

仓库内置 GitHub Actions 工作流 [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)，推送代码后自动构建 APK：

- **触发时机**：推送到 `main` / `master`、发起 PR、打 `v*` 标签，或在 Actions 页面手动触发
- **构建内容**：Debug 与 Release 两个 APK
- **下载方式**：在 Actions 运行详情页的 `Artifacts` 区域下载 `BaiJiMusic-apk-<commit>` 压缩包
- **发布 Release**：推送形如 `v1.0.0` 的标签时，APK 会自动挂到对应 Release 上

CI 会自动安装所需的 JDK 17、Android SDK、NDK `27.2.12479018` 与 CMake `3.22.1`，无需额外配置密钥即可构建（Release 无 `keystore.properties` 时回退 debug 签名）。

## 签名配置

Release 签名从项目根目录的 `keystore.properties` 读取，该文件已被 `.gitignore` 排除，不会入库。

```bash
cp keystore.properties.example keystore.properties
# 然后填入 storeFile / storePassword / keyAlias / keyPassword
```

**未提供 `keystore.properties` 时，Release 构建会自动回退到 debug 签名**，因此直接 clone 后即可构建运行，无需先准备密钥。

## 原生层说明

`qqmusicapi` 由以下算法模块组成，供上层通过 JNI 调用：

| 模块 | 作用 |
| --- | --- |
| `hash33` | QQ 系接口常用的 qq 号哈希算法 |
| `zzc_sign` | 基于 SHA1 的请求签名（zzc） |
| `tripledes` | 自定义 3DES 实现，用于 QRC 解密链路 |
| `qrc` | QRC 加密参数解密（hex 输入） |
| `qimei` | Qimei 请求构建：key / params / nonce / reqSign / header sign |
| `device` | 默认设备信息 JSON 生成 |
| `base64` / `hex` / `random` | 编解码与随机数基础工具 |
| `comm` | Android 平台 comm 请求体（JSON）组装 |

> 原生库不是可选依赖：应用的登录与接口调用强依赖它，缺少 NDK 环境将无法完成构建。

## 免责声明

- 本项目仅用于个人学习 Android 逆向、JNI、网络协议与媒体播放技术，**请勿用于任何商业用途**。
- 项目使用的接口与算法均来自公开资料与学习分析，版权归腾讯 / QQ 音乐所有。
- 使用本项目产生的任何后果由使用者自行承担，作者不对账号安全、数据丢失或法律风险负责。
- 若相关权利方认为本项目侵犯其权益，请联系删除。

## 致谢

- [AndroidX Media3](https://github.com/androidx/media)
- [OkHttp](https://github.com/square/okhttp)
- [mbedTLS](https://github.com/Mbed-TLS/mbedtls)