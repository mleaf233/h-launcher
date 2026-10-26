LOVE_JNI_DIR := $(call my-dir)

ifeq ($(OS),Windows_NT)
    SHELL_EXECUTOR := powershell -executionpolicy unrestricted
    SCRIPT_EXTENSION := ps1
else
    SHELL_EXECUTOR := sh
    SCRIPT_EXTENSION := sh
endif

IS_NDK_R17 := $(shell ${SHELL_EXECUTOR} ${LOVE_JNI_DIR}/detect_ndkrel.${SCRIPT_EXTENSION} $(NDK_ROOT)/source.properties 17)
IS_ANDROID_21 := $(shell ${SHELL_EXECUTOR} ${LOVE_JNI_DIR}/detect_androidapi.${SCRIPT_EXTENSION} $(TARGET_PLATFORM) 21)
HAS_LOVE := $(shell ${SHELL_EXECUTOR} ${LOVE_JNI_DIR}/detect_love.${SCRIPT_EXTENSION} ${LOVE_JNI_DIR})

ifneq (${HAS_LOVE},yes)
    $(error Missing LOVE. Make sure to initialize the submodule correctly!)
endif

# 这里刻意不用 $(call all-subdir-makefiles)：
# 上游 SDL2 自带一个 Android.mk（也定义模块 SDL2），自动扫描会和下面 mk/SDL2.mk 冲突，
# 而 LÖVE 链接的是 mk/SDL2.mk 里的 SDL2_static，所以必须显式指定要包含哪些。
include $(LOVE_JNI_DIR)/love/Android.mk
include $(LOVE_JNI_DIR)/lua-modules/Android.mk
include $(LOVE_JNI_DIR)/LuaJIT-2.1/Android.mk

# 第三方库目录本身都是 git submodule，上游不提供可用的 Android.mk，
# 所以它们的构建脚本统一放在 mk/ 下，各自把 LOCAL_PATH 指回对应的 submodule 目录。
# 额外文件（config.h、opensl_latency.cpp 等）放在 patches/ 下，由对应 mk 引用。
include $(LOVE_JNI_DIR)/mk/SDL2.mk
include $(LOVE_JNI_DIR)/mk/freetype2.mk
include $(LOVE_JNI_DIR)/mk/openal-soft.mk
include $(LOVE_JNI_DIR)/mk/oboe.mk
include $(LOVE_JNI_DIR)/mk/mpg123.mk
include $(LOVE_JNI_DIR)/mk/libogg.mk
include $(LOVE_JNI_DIR)/mk/libvorbis.mk
include $(LOVE_JNI_DIR)/mk/libtheora.mk
include $(LOVE_JNI_DIR)/mk/libmodplug.mk
