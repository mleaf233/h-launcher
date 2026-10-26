<div align="center">
    <h1> H Launcher </h1>
    <p>🩷 Android launcher for Balatro </p>
    <p>English &nbsp;&nbsp; <a href="https://github.com/mleaf233/h-launcher/blob/master/README-zh_CN.md">简体中文</a></p>
</div>

## Introduction

Android launcher for Balatro.

This project uses the LÖVE game engine. LÖVE is a powerful 2D game engine for Lua:
http://love2d.org

Copyright (c) 2006-2024 LOVE Development Team

Download
--------

Automated packaging is still in progress. Download builds from QQ group: `755944835`

Notes
-----

* This repository is forked from the upstream project. For more complete upstream documentation, see the [official wiki](https://github.com/love2d/love-android/wiki)
* This repository does not include game source code, game art, game audio, or other copyrighted game assets
* If you want to build from source, you must sync submodules. Common failures include missing `liblove.so` or `Missing LÖVE` during native build. Recommended clone command:

```bash
git clone --recurse-submodules https://github.com/mleaf233/h-launcher.git
```

* Add `-b <branch/tag>` and `--depth 1` if needed.

* If you already cloned the repository but forgot to initialize submodules, run the following in the repository root. Add `--depth 1` to the last command if needed:

```bash
git submodule sync --recursive
git submodule update --init --force --recursive
```

## Submodules

Every third-party dependency is a git submodule. The main repository holds no third-party
source code, only pinned commit IDs:

| Path | Upstream | Pinned |
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
| `love/src/jni/LuaJIT-2.1` | mleaf233/LuaJIT (`android-lovely`) | this project's fork |
| `love/src/jni/love` | mleaf233/love-balatro | `7dd48f26` |
| `love/src/jni/lua-modules/lua-https` | mleaf233/lua-https | `f4631eb` |

`LuaJIT-2.1` is this project's fork; the `android-lovely` branch carries the lovely injection
patch. Swapping it for upstream LuaJIT silently breaks mod loading.

None of the upstreams ship a usable `Android.mk`, so build scripts live in `love/src/jni/mk/`.
Files upstream does not provide but the build needs live in `love/src/jni/patches/` and
`love/src/sdl-java/`.

## Build

Requires JDK 17, Android SDK 34, NDK `25.2.9519653`, VS2022, Rust nightly.

```powershell
# 1. Submodules
git submodule update --init --recursive

# 2. Build the LuaJIT static library (once)
powershell -ExecutionPolicy Bypass -File .\love\src\jni\LuaJIT-2.1\build_android.ps1

# 3. Build the APK
.\gradlew.bat assembleNormalRecordRelease
```

Output: `out/app/outputs/apk/normalRecord/release/H.apk`

- Step 2 is mandatory, otherwise linking fails with `cannot find -lluajit` or undefined `lovely_*`.
- Other ABIs: pass `-Abi x86_64` to the script and `-PtargetAbi=x86_64` to Gradle.
- Without `ANDROID_SDK_ROOT`, create `local.properties` with `sdk.dir=D:\Android\Sdk`.
- Build output is pinned to `out/` to avoid Windows path-length failures.
- A missing submodule checkout fails with `Missing LOVE`.

Release Signing
---------------

The `app` module reads signing settings from `keystore.properties`.

Rules:

1. The `app` module reads `keystore.properties.local` first, and falls back to `keystore.properties` if it does not exist.
2. `storeFile` can be an absolute path or a path relative to the repository root.
3. The default repository template points to `keystore/release.jks`.
4. If the configured keystore file does not exist, both `debug` and `release` builds fall back to the default Android debug signing key.
5. Only commit the template `keystore.properties`. Put real passwords in `keystore.properties.local`. The repository already ignores `keystore/` contents and `keystore.properties.local`.

Current template:

```properties
keyAlias=release
keyPassword=CHANGE_ME
storeFile=keystore/release.jks
storePassword=CHANGE_ME
```

Example command to generate a new release keystore on Windows:

```powershell
keytool -genkeypair -v `
  -keystore .\keystore\release.jks `
  -alias release `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

If you want to embed the game directly into the APK, choose one of these approaches:

1. Put all game files in `app/src/embed/assets`, with `main.lua` at `app/src/embed/assets/main.lua`.
2. Put your packaged `*.love` file in `app/src/embed/assets` and name it `game.love`.

Then update the application ID, version code, version name, display name, and icons. See [Game Packaging Wiki](https://github.com/love2d/love-android/wiki/Game-Packaging).

After that, run:

- `gradlew assembleEmbedNoRecordRelease`
- or `gradlew assembleEmbedRecordRelease` if your game needs microphone permission

This generates an installable APK.

To generate an AAB for Play Store upload, run:

- `gradlew bundleEmbedNoRecordRelease`
- or `gradlew bundleEmbedRecordRelease`

Alternatively, you can install Android Studio **2022.3.1** or later. On first launch, open SDK Manager, go to the "SDK Tools" tab, enable "Show Package Details", then install NDK (Side By Side) version `25.2.9519653`. After that, open the repository root.

Note: The older embed + APKTool approach is no longer the preferred path because Google distribution requirements have changed.

Issues
------

Submit bugs or feature requests to:

* https://github.com/mleaf233/h-launcher/issues

* https://github.com/love2d/love-android/issues - LÖVE-Android-specific issues
* https://github.com/love2d/love/issues - LÖVE issues in general

License
-------

This project contains code from multiple projects under different licenses. See LOVE's [license.txt](https://github.com/love2d/love/blob/master/license.txt) for details.
