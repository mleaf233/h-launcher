// This file is NOT part of OpenAL-soft.
//
// Points OpenAL Soft at an editable alsoft.ini on the app's external storage.
// Called from GameActivity.onCreate(), before the audio device is opened, so the
// environment variable is in place by the time OpenAL loads its configuration.

#include <jni.h>

#include <cstdlib>

extern "C"
{

JNIEXPORT void JNICALL
Java_org_love2d_android_GameActivity_nativeSetAlsoftConf(
    JNIEnv *env,
    jclass /*clazz*/,
    jstring path
) {
    if (path == nullptr)
        return;

    const char *cpath = env->GetStringUTFChars(path, nullptr);
    if (cpath == nullptr)
        return;

    setenv("ALSOFT_CONF", cpath, 1);
    env->ReleaseStringUTFChars(path, cpath);
}

}
