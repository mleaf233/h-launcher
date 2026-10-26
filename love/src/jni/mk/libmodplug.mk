LOCAL_PATH := $(call my-dir)/../libmodplug-0.8.8.4

# libogg
include $(CLEAR_VARS)

LOCAL_MODULE    := libmodplug
# HAVE_SETENV / HAVE_SINF：git 版本的 load_abc.cpp、load_pat.cpp 里各带一个
# 内置 shim（#ifndef HAVE_XXX 保护），会和 bionic 的 stdlib.h / math.h 声明冲突。
# 这两个函数 Android 本来就有，直接定义掉让 shim 跳过。
LOCAL_CFLAGS    := -fexceptions -g -Dlinux -DHAVE_GCC_DESTRUCTOR=1 -DOPT_GENERIC -DREAL_IS_FLOAT -DHAVE_SETENV=1 -DHAVE_SINF=1
LOCAL_CPPFLAGS  := ${LOCAL_CFLAGS}

LOCAL_C_INCLUDES := \
	${LOCAL_PATH}/../patches/libmodplug \
	${LOCAL_PATH} \
	${LOCAL_PATH}/src \
	${LOCAL_PATH}/src/libmodplug
LOCAL_EXPORT_C_INCLUDES := ${LOCAL_PATH}/src
LOCAL_SRC_FILES := \
	$(filter-out \
  , $(subst $(LOCAL_PATH)/,,\
	$(wildcard ${LOCAL_PATH}/src/*.cpp) ))

# $(info libmodplug: include dirs $(LOCAL_C_INCLUDES))
# $(info libmodplug: src files $(LOCAL_SRC_FILES))

include $(BUILD_STATIC_LIBRARY)
