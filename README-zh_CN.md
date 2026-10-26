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

## 子模块

所有第三方依赖都是 git submodule，主仓库不含第三方源码，只固定提交号：

| 路径 | 上游 | 版本 |
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
| `love/src/jni/LuaJIT-2.1` | mleaf233/LuaJIT（`android-lovely`） | 本项目 fork |
| `love/src/jni/love` | mleaf233/love-balatro | `7dd48f26` |
| `love/src/jni/lua-modules/lua-https` | mleaf233/lua-https | `f4631eb` |

`LuaJIT-2.1` 是本项目 fork，`android-lovely` 分支带 lovely 注入补丁。换成上游 LuaJIT 会让 mod 静默失效。

上游都不提供可用的 `Android.mk`，所以构建脚本在 `love/src/jni/mk/`；上游缺失但编译必需的文件在 `love/src/jni/patches/` 和 `love/src/sdl-java/`。

## 构建

需要 JDK 17、Android SDK 34、NDK `25.2.9519653`、Rust nightly。

```bash
# 1. 子模块
git submodule update --init --recursive

# 2. 生成 LuaJIT 静态库（只需一次）
bash ./love/src/jni/LuaJIT-2.1/build_android.sh          # Linux / macOS
powershell -ExecutionPolicy Bypass -File .\love\src\jni\LuaJIT-2.1\build_android.ps1   # Windows，另需 VS2022

# 3. 出包
./gradlew assembleNormalRecordRelease
```

产物：`out/app/outputs/apk/normalRecord/release/H.apk`

- 第 2 步不能跳过，否则链接报 `cannot find -lluajit` 或 `lovely_*` 未定义。
- 换架构：脚本加 `x86_64`（Windows 用 `-Abi x86_64`），gradle 加 `-PtargetAbi=x86_64`。
- 没配 `ANDROID_SDK_ROOT` 时，根目录建 `local.properties` 写 `sdk.dir=...`。
- 输出目录固定在 `out/`，避免 Windows 路径过长导致资源编译失败。
- 子模块没初始化时报 `Missing LOVE`。

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
