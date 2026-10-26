# Android.mk script to build OpenAL-soft 1.21.0 in Android

# 必须在任何 BUILD_* 之前捕获：include $(BUILD_SHARED_LIBRARY) 会引入 NDK 内部 makefile，
# 之后再 $(call my-dir) 拿到的就是 NDK 目录而不是本文件所在目录。
OPENAL_MK_DIR := $(call my-dir)
LOCAL_PATH := $(OPENAL_MK_DIR)/../openal-soft
include $(CLEAR_VARS)

#
# Module definition
#
LOCAL_MODULE := libopenal
LOCAL_ARM_NEON := true

#
# Flags
#
LOCAL_CFLAGS := -std=c11 -DAL_ALEXT_PROTOTYPES -DHAVE_OBOE -DHAVE_OPENSL -fvisibility=hidden
LOCAL_CPPFLAGS := -std=c++14 -fvisibility-inlines-hidden
LOCAL_CPP_FEATURES := exceptions

#
# Include directories
#
# config.h / version.h 正常由上游 CMake 生成，Android 下没有 CMake，故放在 patches 里；
# opensl_latency.cpp 是 love-android 追加的 OpenSL ES 低延迟实现，上游没有。
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../patches/openal-soft \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/alc \
    $(LOCAL_PATH)/common \
    $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

#
# config.h defines
#
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    # Defines for ARM32
    LOCAL_CFLAGS += \
        -DHAVE_NEON
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    # Defines for ARM64
    LOCAL_CFLAGS += \
        -DHAVE_POSIX_MEMALIGN \
        -DHAVE_NEON
else ifeq ($(TARGET_ARCH_ABI),x86)
    # Defines for x86. UNTESTED!
    LOCAL_CFLAGS += \
        -DHAVE_SSE \
        -DHAVE_SSE2 \
        -DHAVE_SSE3 \
        -DHAVE_CPUID_H \
        -DHAVE_GCC_GET_CPUID \
        -DHAVE_SSE_INTRINSICS
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    # Defines for x86-64. UNTESTED!
    LOCAL_CFLAGS += \
        -DHAVE_POSIX_MEMALIGN \
        -DHAVE_SSE \
        -DHAVE_SSE2 \
        -DHAVE_SSE3 \
        -DHAVE_SSE4_1 \
        -DHAVE_CPUID_H \
        -DHAVE_GCC_GET_CPUID \
        -DHAVE_SSE_INTRINSICS
endif

#
# Source files
#
LOCAL_SRC_FILES := \
    common/alcomplex.cpp \
    common/alexcpt.cpp \
    common/alfstream.cpp \
    common/almalloc.cpp \
    common/alstring.cpp \
    common/dynload.cpp \
    common/polyphase_resampler.cpp \
    common/strutils.cpp \
    common/threads.cpp \
    al/auxeffectslot.cpp \
    al/buffer.cpp \
    al/effect.cpp \
    al/error.cpp \
    al/event.cpp \
    al/extension.cpp \
    al/filter.cpp \
    al/listener.cpp \
    al/source.cpp \
    al/state.cpp \
    alc/alc.cpp \
    alc/alu.cpp \
    alc/alconfig.cpp \
    alc/ambdec.cpp \
    alc/bformatdec.cpp \
    alc/bs2b.cpp \
    alc/bsinc_tables.cpp \
    alc/buffer_storage.cpp \
    alc/converter.cpp \
    alc/cpu_caps.cpp \
    alc/effects/autowah.cpp \
    alc/effects/chorus.cpp \
    alc/effects/compressor.cpp \
    alc/effects/convolution.cpp \
    alc/effects/dedicated.cpp \
    alc/effects/distortion.cpp \
    alc/effects/echo.cpp \
    alc/effects/equalizer.cpp \
    alc/effects/fshifter.cpp \
    alc/effects/modulator.cpp \
    alc/effects/null.cpp \
    alc/effects/pshifter.cpp \
    alc/effects/reverb.cpp \
    alc/effects/vmorpher.cpp \
    alc/filters/biquad.cpp \
    alc/filters/nfc.cpp \
    alc/filters/splitter.cpp \
    alc/fmt_traits.cpp \
    alc/fpu_ctrl.cpp \
    alc/helpers.cpp \
    alc/hrtf.cpp \
    alc/mastering.cpp \
    alc/panning.cpp \
    alc/ringbuffer.cpp \
    alc/uhjfilter.cpp \
    alc/uiddefs.cpp \
    alc/voice.cpp \
    alc/mixer/mixer_c.cpp

#
# Conditionals source files
#
ifneq (,$(findstring HAVE_SSE4_1,${LOCAL_CFLAGS}))
    # SSE4.1 mixer
    LOCAL_SRC_FILES += \
        alc/mixer/mixer_sse.cpp \
        alc/mixer/mixer_sse2.cpp \
        alc/mixer/mixer_sse3.cpp \
        alc/mixer/mixer_sse41.cpp
else ifneq (,$(findstring HAVE_SSE3,${LOCAL_CFLAGS}))
    # SSE3 mixer
    LOCAL_SRC_FILES += \
        alc/mixer/mixer_sse.cpp \
        alc/mixer/mixer_sse2.cpp \
        alc/mixer/mixer_sse3.cpp
else ifneq (,$(findstring HAVE_SSE2,${LOCAL_CFLAGS}))
    # SSE2 mixer
    LOCAL_SRC_FILES += \
        alc/mixer/mixer_sse.cpp \
        alc/mixer/mixer_sse2.cpp
else ifneq (,$(findstring HAVE_NEON,${LOCAL_CFLAGS}))
    # NEON mixer
    LOCAL_SRC_FILES += alc/mixer/mixer_neon.cpp
endif

#
# backends
#
LOCAL_SRC_FILES += \
    alc/backends/base.cpp \
    alc/backends/loopback.cpp \
    alc/backends/null.cpp \
    alc/backends/oboe.cpp \
    alc/backends/opensl.cpp \
    alc/backends/wave.cpp

#
# Libraries related
#
LOCAL_STATIC_LIBRARIES := oboe
# opensl_latency.cpp（LÖVE 的 JNI 入口）编成独立静态库，见文件末尾。
# 用 WHOLE 是因为 JNI 符号没有任何内部引用，普通静态库会被链接器丢弃。
LOCAL_WHOLE_STATIC_LIBRARIES := love_android_jni
LOCAL_LDLIBS := -lOpenSLES -llog

# Build
include $(BUILD_SHARED_LIBRARY)

#
# LÖVE 追加的 JNI 入口：opensl_latency.cpp 不属于上游 openal-soft，
# 放在 jni/patches/openal-soft 下，单独编成静态库再整块链进 libopenal.so。
#
include $(CLEAR_VARS)
LOCAL_PATH := $(OPENAL_MK_DIR)/../patches/openal-soft
LOCAL_MODULE := love_android_jni
LOCAL_SRC_FILES := opensl_latency.cpp
LOCAL_C_INCLUDES := $(OPENAL_MK_DIR)/../oboe/include
include $(BUILD_STATIC_LIBRARY)
