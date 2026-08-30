# Native Libraries

原生库（LWJGL、渲染器、JNA、OpenAL、jemalloc 等）不再放置在 `assets/natives/` 根目录。

它们由各模块按 ABI 打包进 APK 的 `lib/<abi>/`（配合 `abiFilters` + `splits.abi` 按架构出包），
运行时由 `NativesDeployer` 从 APK `lib/<当前ABI>/` 提取正确架构的版本并部署到内部存储。

因此 `assets/natives/` 目录刻意留空，避免把某个架构的 .so 错放到其他架构的安装包中。