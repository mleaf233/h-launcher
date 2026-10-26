<div align="center">
    <h1> H Launcher </h1>
    <p>🩷 Balatro的安卓启动器 </p>
    <p><a href="https://github.com/mleaf233/h-launcher/blob/master/README.md">English</a> &nbsp;&nbsp; 简体中文</p>
</div>

## 介绍

Balatro 的安卓启动器

该项目使用LÖVE 游戏引擎，LÖVE是一个很强的 Lua 2D 游戏引擎：http://love2d.org

Copyright (c) 2006-2024 LOVE Development Team

下载
----

自动化构建打包正在施工中，前往 QQ群下载：755944835

说明
----

* 本仓库基于上游仓库Fork而来，更详细的官方说明见[官方wiki](https://github.com/love2d/love-android/wiki)
* 本仓库不含游戏源代码、游戏美术、游戏音频等版权资源
* 如果你要从源码构建，必须同步子模块。常见报错包括缺失 `liblove.so`，或者构建时提示 `Missing LÖVE`。推荐的克隆方式：

```bash
git clone --recurse-submodules https://github.com/mleaf233/h-launcher.git
```

* 如有需要，可以额外带上 `-b <branch/tag>` 和 `--depth 1`。

* 如果仓库已经克隆，但忘了初始化子模块，在仓库根目录执行（最后一条命令按需可加 `--depth 1`）：

```bash
git submodule sync --recursive
git submodule update --init --force --recursive
```

### 子模块说明

本仓库把所有第三方依赖都做成了 git submodule，**主仓库不再存放任何第三方源码**，只固定提交号：

| 路径 | 上游仓库 | 固定版本 |
| --- | --- | --- |
| `love/src/jni/SDL2` | libsdl-org/SDL | `release-2.28.5` |
| `love/src/jni/freetype2` | freetype/freetype | `VER-2-8-1` |
| `love/src/jni/openal-soft` | kcat/openal-soft | `1.21.0` |
| `love/src/jni/oboe` | google/oboe | `1.4.3` |
| `love/src/jni/libogg-1.3.2` | xiph/ogg | `v1.3.2` |
| `love/src/jni/libvorbis-1.3.5` | xiph/vorbis | `v1.3.5` |
| `love/src/jni/libtheora-1.2.0alpha1` | xiph/theora | `v1.2.0alpha1` |
| `love/src/jni/libmodplug-0.8.8.4` | Konstanty/libmodplug | `edab762` |
| `love/src/jni/mpg123-1.17.0` | madebr/mpg123 | `e58b7fd` |
| `love/src/jni/LuaJIT-2.1` | mleaf233/LuaJIT（`android-lovely` 分支） | 见下 |
| `love/src/jni/love` | mleaf233/love-balatro | `7dd48f26` |
| `love/src/jni/lua-modules/lua-https` | mleaf233/lua-https | `f4631eb` |

> `LuaJIT-2.1` 是本项目自己的 fork，`android-lovely` 分支上打了 lovely 注入层补丁
> （`crates/liblovely/luajit.patch` 来自 lovely-injector）。不要换成上游 LuaJIT/LuaJIT，
> 否则 mod 加载会静默失效。

因为上游这些库都不提供可用的 `Android.mk`（SDL2 自带的那个会和本项目冲突），
构建脚本统一放在 `love/src/jni/mk/` 下，各自把 `LOCAL_PATH` 指回对应的 submodule；
上游没有、但编译必需的文件（`config.h`、`hrtf_default.h`、`SDLActivity.java` 等）
放在 `love/src/jni/patches/` 和 `love/src/sdl-java/` 下。

### LuaJIT 预编译静态库（构建前必做）

`libluajit.a` 约 18 MB，已经合并了 Rust 侧的 lovely 核心，**不进版本库**（`.gitignore` 里有 `*.a`）。
所以 clone 完不能直接构建，必须先自己生成一次：

```powershell
powershell -ExecutionPolicy Bypass -File .\love\src\jni\LuaJIT-2.1\build_android.ps1
```

产物：`love/src/jni/LuaJIT-2.1/android/arm64-v8a/libluajit.a`

前置条件（缺一不可）：

- **Windows**（脚本用 MSVC 编译 LuaJIT 的 host 工具 `minilua` / `buildvm`，目前只验证了 Windows）
- **Visual Studio 2022**（提供 MSVC）
- **Android NDK `25.2.9519653`**（提供交叉编译器，以及 GNU make）
- **Rust nightly** 和 android target：

```powershell
rustup toolchain install nightly
rustup target add aarch64-linux-android --toolchain nightly
```

脚本会依次做：编译 host 工具 → 生成 `luajit.h` / `buildvm_arch.h` → 交叉编译 `libluajit.a`
→ `cargo build -p liblovely` → 把两者合并成一个归档。中间某步失败会直接报错退出。


同步子模块并完成构建后，可以校验 APK 中是否包含必需的 native 库：

```powershell
powershell -ExecutionPolicy Bypass -File .\script\verify-apk-libs.ps1
```

如果要校验指定 APK：

```powershell
powershell -ExecutionPolicy Bypass -File .\script\verify-apk-libs.ps1 -ApkPath .\out\app\outputs\apk\normalRecord\release\app-normal-record-release.apk
```

如果要升级 `lua-https`，请将子模块固定到具体提交，而不是浮动在分支最新提交：

```bash
git submodule update --init --recursive
git -C love/src/jni/lua-modules/lua-https fetch --tags
git -C love/src/jni/lua-modules/lua-https checkout <commit>
git add .gitmodules love/src/jni/lua-modules/lua-https
```



快速开始
--------

开始前请安装 `JDK 17`。不能高于 17，也不能低于 17。如果你使用 Android Studio 构建，可以直接用它内置的 JDK 17。

安装 Android SDK `34` 和 Android NDK `25.2.9519653`，配置好 `ANDROID_SDK_ROOT` 后执行：

```bash
# 1. 同步子模块（12 个，必须）
git submodule update --init --recursive

# 2. 生成 LuaJIT 预编译库（只需做一次，详见上一节）
powershell -ExecutionPolicy Bypass -File .\love\src\jni\LuaJIT-2.1\build_android.ps1

# 3. 构建
./gradlew assembleNormalRecord
```

**跳过第 2 步会直接失败**，链接阶段会报 `cannot find -lluajit` 或一堆 `lovely_*` 未定义符号。

正常情况下，会在 `app/build/outputs/apk/normalRecord` 下得到 APK。`normalRecord` 就是通常下载到的 APK 变体。

本仓库在 Windows 上已经实际验证过以下构建方式。

Windows 构建
----------------

1. 如果系统没有全局配置 `ANDROID_SDK_ROOT`，就在仓库根目录创建 `local.properties`：

```properties
sdk.dir=D:\\Android\\Sdk
```

2. 构建前必须初始化子模块，否则 native 构建会报错：

```text
Missing LOVE. Make sure to initialize the submodule correctly!
```

执行命令：

```powershell
git submodule update --init --recursive
```

3. 本仓库将 Gradle 构建输出目录固定到本地 `out/`，目的有两个：

- 所有临时编译产物都保留在当前项目目录内。
- 避免 Windows 下默认 `app/build/intermediates/...` 路径过长，导致资源编译失败。

4. 已验证可用的 Windows 构建命令：

```powershell
.\gradlew.bat clean assembleNormalRecord --console=plain
```

5. 已验证的 release APK 输出路径：

```text
out/app/outputs/apk/normalRecord/release/app-normal-record-release.apk
```

对应的 debug APK：

```text
out/app/outputs/apk/normalRecord/debug/app-normal-record-debug.apk
```

正式签名
--------

本仓库的 `app` 模块从 `keystore.properties` 读取签名信息。

规则如下：

1. `app` 模块会优先读取 `keystore.properties.local`，如果不存在，再回退到 `keystore.properties`。
2. `storeFile` 可以写绝对路径，也可以写相对仓库根目录的路径。
3. 当前仓库内默认模板指向 `keystore/release.jks`。
4. 如果配置的 keystore 文件不存在，那么 `debug` 和 `release` 构建都会回退到 Android 默认 debug 签名。
5. 仓库只提交模板 `keystore.properties`。真实密码放到 `keystore.properties.local`。仓库已经忽略了 `keystore/` 目录内容以及 `keystore.properties.local`。

当前模板：

```properties
keyAlias=release
keyPassword=CHANGE_ME
storeFile=keystore/release.jks
storePassword=CHANGE_ME
```

Windows 下生成一套新的正式签名 keystore 示例：

```powershell
keytool -genkeypair -v `
  -keystore .\keystore\release.jks `
  -alias release `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

如果你想把游戏直接内嵌进 APK，可以选两种方式之一：

1. 把游戏文件放到 `app/src/embed/assets`，使 `main.lua` 的路径为 `app/src/embed/assets/main.lua`。
2. 把打包好的 `*.love` 文件放到 `app/src/embed/assets`，并命名为 `game.love`。

然后修改应用 ID、版本号、显示名称和图标。可参考 [Game Packaging Wiki](https://github.com/love2d/love-android/wiki/Game-Packaging)。

之后执行：

- `gradlew assembleEmbedNoRecordRelease`
- 或者如果游戏需要麦克风权限，执行 `gradlew assembleEmbedRecordRelease`

这样会生成可安装 APK。

如果要生成上传 Play Store 的 AAB，则执行：

- `gradlew bundleEmbedNoRecordRelease`
- 或者 `gradlew bundleEmbedRecordRelease`

或者，你也可以安装 Android Studio **2022.3.1** 或更高版本。首次打开后，在 SDK Manager 的 “SDK Tools” 页签里勾选 “Show Package Details”，然后安装 NDK (Side By Side) 的 `25.2.9519653` 版本。之后再打开仓库根目录即可。

注意：过去常用的 embed + APKTool 方式，现在已经不再适合作为主要方案，因为 Google 的发布要求已经变化。

问题反馈
--------

Bug 或功能请求请提交到：

* https://github.com/mleaf233/h-launcher/issues

* https://github.com/love2d/love-android/issues - LÖVE-Android 专属问题
* https://github.com/love2d/love/issues - LÖVE 通用问题

许可证
------

本项目包含多个项目的代码，分别使用不同许可证。具体内容请查看 LOVE 的
[license.txt](https://github.com/love2d/love/blob/master/license.txt)。
