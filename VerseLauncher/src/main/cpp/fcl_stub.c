/*
 * fcl_stub.c — fcl_orig poemav exec 独立构建用的 fcl 模块依赖补齐。
 *
 * fcl_orig 的 poemav exec 源码链（input_bridge_v3.c / jre_launcher.c / lwjgl_dlopen_hook.c / egl_bridge.c ...）
 * 原与 libfcl.so 链接（fcl/fcl_bridge.c 提供 struct FCLInternal *fcl 与 nominal_exit）。
 * Verse 的 FclCore/ART 侧继续使用 jniLibs 预编译 libfcl.so，因此这里按"仅符号存在"补齐
 * poemav exec 自身编译链接所需的最小定义，避免引入 bytehook/整链 fcl 构建：
 *   - struct FCLInternal *fcl：header 声明 + poemav 代码不引用，仅需存在以通过链接
 *   - nominal_exit(int)：jre_launcher.c 的信号处理器调用，指向 exit()
 */
#include <stdlib.h>
#include "fcl/include/fcl_internal.h"
#include "fcl/include/fcl_bridge.h"

struct FCLInternal *fcl;

_Noreturn void nominal_exit(int code) {
    exit(code);
}

/*
 * fclSetInjectorCallback / fclSetHitResultType：egl_bridge.c 引用，原由 fcl 模块的
 * fcl_event.c 提供（经 ART 侧 FCLBridge Java 方法转发）。Verse 的 ART/JRE 侧 hit result
 * 通道由预编译 libfcl.so（含自身 fcl_event.c 实现）承担，此处仅需满足 poemav exec 链接。
 */
void fclSetInjectorCallback(FCLinjectorfun callback) {
    (void) callback;
}

void fclSetHitResultType(int type) {
    (void) type;
}