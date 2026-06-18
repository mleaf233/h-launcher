# 变更说明

本文档用于说明本次对仓库所做的实际改动。

## 1. 构建输出目录调整

为了让所有临时编译产物都保留在当前项目目录内，并避免 Windows 下默认 Gradle 中间目录路径过长导致的资源编译失败，调整了根项目的构建输出策略：

- 默认将 Gradle 构建输出目录设置为 `out/`
- 所有子模块的构建输出统一落到 `out/<module>/`
- 保留了通过 Gradle 属性覆盖输出目录的能力，便于排障时临时切换输出位置

对应改动文件：

- `build.gradle`

## 2. Android SDK 本地定位方式补充

为了让仓库在当前机器上可以稳定完成构建，补充了本地 SDK 定位方式：

- 在仓库根目录使用 `local.properties` 指向本机 Android SDK
- 当前已验证的 SDK 路径为 `D:\86133\Android\Sdk`

说明：

- `local.properties` 属于本地环境配置，不应提交到远程仓库

## 3. 子模块初始化后完成原生构建

构建初期发现 native 构建依赖的子模块工作树缺失，导致 `Missing LOVE` 错误。为解决该问题，执行并验证了子模块初始化流程：

- 初始化 `love/src/jni/love`
- 初始化 `love/src/jni/lua-modules/lua-https`

说明：

- 这一步属于构建前置条件，不是源码逻辑变更，但已经在文档中补充清楚

## 4. 签名配置改为“模板 + 本地覆盖”

原有签名配置直接在 `keystore.properties` 中保存真实信息，不适合提交到远程仓库。现在已经改为两层结构：

- `keystore.properties` 只保留模板值
- `keystore.properties.local` 保存本机真实签名信息
- `app/build.gradle` 优先读取 `keystore.properties.local`，缺失时回退到 `keystore.properties`

这样处理后：

- 仓库可以提交签名配置模板
- 本机仍可使用正式签名构建
- 真实密码不会进入远程仓库

对应改动文件：

- `app/build.gradle`
- `keystore.properties`

## 5. 正式签名文件落地到仓库内目录

为了让正式签名文件可以和项目一起管理路径，同时避免误提交，补充了本地 keystore 目录约定：

- 正式签名文件放在 `keystore/release.jks`
- 仓库保留 `keystore/.gitkeep`
- `keystore/` 内容加入忽略规则

对应改动文件：

- `.gitignore`
- `keystore/.gitkeep`

## 6. 生成并验证正式签名

已在当前仓库内生成正式签名文件，并完成实际构建验证。

当前正式签名文件：

- `keystore/release.jks`

当前签名配置：

- `keyAlias=release`
- `storeFile=keystore/release.jks`

实际重新构建后的 release APK 已确认使用该正式签名，而不是 Android 默认 debug 签名。

已验证的证书信息包括：

- 证书主题：`CN=love-android-balatro, OU=love-android-balatro, O=love-android-balatro, L=Shanghai, ST=Shanghai, C=CN`
- SHA-256：`7531b96181a10a45128c03340dcbb1e2ad752590fdc94b3b9b11ceea6c86c164`
- SHA-1：`8ecb4ce491ef47e674ca28d12bdc7a4c62f7f051`

## 7. 重新构建并确认默认输出目录可用

在签名验证过程中，曾出现旧的 release APK 被系统占用，导致 Gradle 无法删除 `out/.../release` 目录的问题。为完成排障，临时使用过替代输出目录进行构建验证。

之后已经再次按默认 `out/` 路径重新构建并确认：

- `out/app/outputs/apk/normalRecord/release/app-normal-record-release.apk` 可以正常生成
- 默认输出目录已经恢复可用
- 正式签名在默认输出目录构建下同样生效

## 8. 文档重写与补充

本次对文档做了较大整理，重点是让实际构建方式、签名方式和仓库当前用途保持一致。

### 英文文档调整

重写了 `README.md` 的结构和内容，使其更贴合当前仓库，而不是继续沿用上游仓库的大段说明。内容中补充了：

- 当前项目定位
- Windows 下已验证的构建流程
- 仓库内构建输出目录约定
- 正式签名说明
- 本地签名覆盖策略

### 中文文档新增

新增了 `README-zh_CN.md`，用于提供完整中文说明，内容与当前英文 README 结构对齐，包含：

- 项目简介
- 构建前准备
- Windows 构建方式
- 正式签名说明
- 内嵌游戏打包方式
- 问题反馈入口

### 中文文档迁移

原先独立的中文构建说明已经迁移并并入 `README-zh_CN.md`，避免中文文档分散。

对应改动文件：

- `README.md`
- `README-zh_CN.md`

## 9. 仓库忽略规则补充

为了避免本地构建和签名文件误提交，补充了忽略规则：

- 忽略 `out/`
- 忽略 `keystore/`
- 忽略 `keystore.properties.local`

同时保留：

- `keystore/.gitkeep`

对应改动文件：

- `.gitignore`

## 10. Git 远程地址调整

已将当前仓库的 `origin` 远程地址改为：

- `https://github.com/mleaf233/h-launcher`

该改动只影响当前本地仓库的远程配置，不影响源码内容。

## 11. 已完成的分类提交

本次改动已经按类别完成提交，当前已有以下提交记录：

1. `2856210f` `build: keep Gradle outputs inside repo`
2. `a489baf6` `signing: support local release keystore overrides`
3. `d239097a` `docs: align README layout and ignore local signing files`

说明：

- 第一笔提交中顺带包含了 `README-zh_CN.md`，因为该文件在提交前已处于暂存状态
- 其余提交边界已经按构建、签名、文档进行了拆分
