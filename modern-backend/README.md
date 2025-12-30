# PrototypeMachinery – Modern Backend (Cleanroom / Java 21+)

这个目录是一个**独立的 Gradle 工程**（不是主工程 `PrototypeMachinery/` 的 Gradle 子模块）。它的目的：

- 在 **Java 21+ / Cleanroom Loader** 环境里做实验性后端（如虚拟线程、Vector API 等）；
- 不影响主模组的 **Java 8 / RFG** 构建与发布。

## IntelliJ IDEA 导入方式（解决“无法识别子项目”）

由于它是一个“嵌套但独立”的 Gradle 工程，IDEA 默认不会把它当成主工程的子模块自动导入；需要单独“附加/打开”。

推荐做法（二选一）：

### A. Attach Gradle Project（推荐，保留主工程打开状态）

1. 打开主工程 `PrototypeMachinery/`（你现在的工程）。
2. 打开 **Gradle** 工具窗口。
3. 点击 **+ / Attach Gradle Project**。
4. 选择：`PrototypeMachinery/modern-backend/build.gradle`
5. 在 IDEA 的 Gradle 设置中，确保：
   - **Gradle JVM = 21**（或更高）
   - 使用该目录下的 wrapper（`modern-backend/gradlew`）

### B. 直接 Open 目录（适合只开发 Modern Backend）

1. `File -> Open...`
2. 选择：`PrototypeMachinery/modern-backend/`
3. 作为 Gradle 工程打开。

## 重要说明

- 本目录只保留 `build.gradle`（Groovy DSL）。此前存在的 `build.gradle.kts` 已移除，避免 IDEA/Gradle 误选导致导入失败。
- 主工程仍然使用 Java 8；Modern Backend 使用 Java 21+。请不要试图把两者合并成单一 Gradle multi-project（Gradle 版本/插件链不兼容）。

## Vector API（jdk.incubator.vector）与后端选择

Modern Backend 内部包含一个“位置变换后端选择器”，会在初始化阶段通过反射尝试加载 Vector 实现；失败则回退到纯标量实现（scalar）。

要启用 Vector 实现，需要满足：

- 使用 JDK 21+ 运行
- 运行参数包含：`--add-modules=jdk.incubator.vector`

本工程的 Gradle dev-run 已提供属性开关：

- `-Penable_vector_api=true`

如需在日志中打印实际选用的后端，可加 JVM System Property：

- `-Dpm.debugTransformBackend=true`

> 说明：Vector 相关类已经被打进同一个 mod jar（不再需要额外的 accel-jar），但是否启用仍由运行时反射探测 + module 可用性决定。
