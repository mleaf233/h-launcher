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

Important
---------

If you want to build from source, you must sync submodules. Common failures include missing
`liblove.so` or `Missing LÖVE` during native build. Recommended clone command:

```bash
git clone --recurse-submodules https://github.com/mleaf233/h-launcher.git
```

Add `-b <branch/tag>` and `--depth 1` if needed.

If you already cloned the repository but forgot to initialize submodules, run this in the repository root:

```bash
git submodule sync --recursive
git submodule update --init --force --recursive
```

Add `--depth 1` to the last command if needed.

Project-specific submodule notes
--------------------------------

This repository also pins:

* `love/src/jni/lua-modules/lua-https`, from https://github.com/love2d/lua-https

After syncing submodules and building, you can verify that the generated APK contains the required native libraries:

```powershell
powershell -ExecutionPolicy Bypass -File .\script\verify-apk-libs.ps1
```

To verify a specific APK:

```powershell
powershell -ExecutionPolicy Bypass -File .\script\verify-apk-libs.ps1 -ApkPath .\out\app\outputs\apk\normalRecord\release\app-normal-record-release.apk
```

If you upgrade `lua-https`, pin the submodule to a specific commit instead of floating on branch head:

```bash
git submodule update --init --recursive
git -C love/src/jni/lua-modules/lua-https fetch --tags
git -C love/src/jni/lua-modules/lua-https checkout <commit>
git add .gitmodules love/src/jni/lua-modules/lua-https
```

Notes
-----

This repository is forked from the upstream project. For more complete upstream documentation, see:
https://github.com/love2d/love-android/wiki

Quick Start
-----------

Install `JDK 17` before you start. Do not use a version newer or older than 17. If you build with Android Studio,
you can use the bundled JDK 17.

Install Android SDK `34` and Android NDK `25.2.9519653`, set `ANDROID_SDK_ROOT`, then run:

```bash
./gradlew assembleNormalRecord
```

Under normal conditions, this generates an APK in `app/build/outputs/apk/normalRecord`.
`normalRecord` is the standard APK variant.

The following Windows flow has been verified in this repository.

Windows Build
-------------

1. If `ANDROID_SDK_ROOT` is not configured globally, create `local.properties` in the repository root:

```properties
sdk.dir=D:\\86133\\Android\\Sdk
```

2. Initialize submodules before building, otherwise native build fails with:

```text
Missing LOVE. Make sure to initialize the submodule correctly!
```

Run:

```powershell
git submodule update --init --recursive
```

3. This repository writes Gradle build output to local `out/` for two reasons:

- All temporary build artifacts stay inside the current project directory.
- It avoids Windows path-length failures in the default `app/build/intermediates/...` resource pipeline.

4. Verified Windows build command:

```powershell
.\gradlew.bat clean assembleNormalRecord --console=plain
```

5. Verified release APK output path:

```text
out/app/outputs/apk/normalRecord/release/app-normal-record-release.apk
```

Corresponding debug APK:

```text
out/app/outputs/apk/normalRecord/debug/app-normal-record-debug.apk
```

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

Then update the application ID, version code, version name, display name, and icons. See
[Game Packaging Wiki](https://github.com/love2d/love-android/wiki/Game-Packaging).

After that, run:

- `gradlew assembleEmbedNoRecordRelease`
- or `gradlew assembleEmbedRecordRelease` if your game needs microphone permission

This generates an installable APK.

To generate an AAB for Play Store upload, run:

- `gradlew bundleEmbedNoRecordRelease`
- or `gradlew bundleEmbedRecordRelease`

Alternatively, you can install Android Studio **2022.3.1** or later. On first launch, open SDK Manager,
go to the "SDK Tools" tab, enable "Show Package Details", then install NDK (Side By Side) version `25.2.9519653`.
After that, open the repository root.

Note: The older embed + APKTool approach is no longer the preferred path because Google distribution requirements have changed.

Issues
------

Submit bugs or feature requests to:

* https://github.com/mleaf233/h-launcher/issues
* https://github.com/love2d/love-android/issues - LÖVE-Android-specific issues
* https://github.com/love2d/love/issues - LÖVE issues in general

License
-------

This project contains code from multiple projects under different licenses. See LOVE's
[license.txt](https://github.com/love2d/love/blob/master/license.txt) for details.
