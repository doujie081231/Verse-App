/*
 * glibc 兼容 stub 库
 *
 * Android bionic libc 不提供 glibc 版本化的库名（libdl.so.2、libpthread.so.0、
 * libc.so.6、libm.so.6、libgcc_s.so.1、libstdc++.so.6 等），但 LWJGL 官方
 * natives（从 lwjgl-*-natives-linux.jar 提取）是用 glibc 工具链编译的，其
 * ELF 的 DT_NEEDED 段硬性依赖这些版本化库名，导致 Android 动态链接器报
 * "library not found" 错误。
 *
 * 本 stub 库只提供一个有效的 ELF 共享库骨架，部署时复制为各 glibc 版本化
 * 库名。Android 动态链接器加载这些 stub 后，实际符号（dlopen、pthread_create、
 * malloc 等）会从已加载的系统 libc.so / libm.so / libdl.so 中解析。
 */

/* 提供一个导出符号，确保编译器生成有效的 ELF 共享库 */
__attribute__((visibility("default"))) int glibc_stub_marker(void) {
    return 0;
}
