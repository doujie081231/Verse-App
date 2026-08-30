#pragma once

#include <jni.h>

struct ANativeWindow;

enum VerseCallbackId {
    VERSE_CB_CHAR = 0,
    VERSE_CB_CHAR_MODS,
    VERSE_CB_CURSOR_ENTER,
    VERSE_CB_CURSOR_POS,
    VERSE_CB_FRAMEBUFFER_SIZE,
    VERSE_CB_KEY,
    VERSE_CB_MOUSE_BUTTON,
    VERSE_CB_SCROLL,
    VERSE_CB_WINDOW_SIZE,
    VERSE_CB_COUNT
};

#ifdef __cplusplus
extern "C" {
#endif

void verseOnVmLoad(JavaVM* vm);
long verseSetCallback(int id, long callback);
void verseGetCursorPos(double* x, double* y);
void verseSetCursorPosValue(double x, double y);
void verseSetShowingWindow(long window);
void verseSetStackQueue(bool use);
void verseDispatchInput(int type, int i1, int i2, int i3, int i4);

#ifdef __cplusplus
}
#endif

// C++ 链接（glfw_hook.cpp 中定义）
ANativeWindow* getNativeWindowSafe();
