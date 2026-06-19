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

本仓库还固定了：

* `love/src/jni/lua-modules/lua-https`，来源于 https://github.com/love2d/lua-https

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
./gradlew assembleNormalRecord
```

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
