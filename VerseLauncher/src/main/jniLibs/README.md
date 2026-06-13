# Native Libraries

This directory contains prebuilt `.so` files extracted from [Amethyst-Android v1.1.4](https://github.com/AngelAuraMC/Amethyst-Android/releases/tag/1.1.4).

## Libraries

| File | Source | Purpose |
|------|--------|---------|
| `libgl4es.so` | NG-GL4ES (Amethyst) | OpenGL → OpenGL ES translation (rendering engine) |
| `libopenal.so` | OpenAL-Soft | 3D audio library for Minecraft sounds |
| `libpojavexec.so` | PojavLauncher | JNI execution bridge for game launch |
| `libSDL3.so` | SDL3 | Cross-platform input/windowing |
| `libbytehook.so` | ByteDance bhook | Native exit code trapping |
| `libjnidispatch.so` | JNA | Java Native Access dispatch |

## How to Update

Download the latest Amethyst APK from GitHub Releases, rename to `.zip`, extract, and copy `lib/arm64-v8a/*.so` to here.

## License

These libraries are subject to their respective licenses from the Amethyst-Android / PojavLauncher projects (LGPL-3.0).
