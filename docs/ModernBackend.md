# Modern Backend（Cleanroom / Java 21+）

> 这是一条“与主工程并行的实验性路线”：主模组仍然是 **Forge 1.12.2 + Java 8 (RFG)**；Modern Backend 则面向 **Cleanroom Loader / Java 21+**。

---

## 1. 目录定位与导入方式

Modern Backend 位于：`PrototypeMachinery/modern-backend/`

它是一个 **独立 Gradle 工程**（不是主工程的 Gradle 子模块）。IDEA 的导入方式与注意事项请看：

- [`modern-backend/README.md`](../modern-backend/README.md)

---

## 2. 与主工程的关系：可选的 Platform Provider

主工程新增了一组“可选平台能力”的 API（用于在 Java 8 环境里通过 SPI/反射对接现代实现）：

- `src/main/java/github/kasuminova/prototypemachinery/api/platform/PMPlatform.java`
- `src/main/java/github/kasuminova/prototypemachinery/api/platform/PMPlatformProvider.java`
- `src/main/java/github/kasuminova/prototypemachinery/api/platform/PMGeckoVertexPipeline.java`
- `src/main/java/github/kasuminova/prototypemachinery/impl/platform/PMPlatformManager.java`

Modern Backend 通过 SPI 暴露 Provider：

- `modern-backend/src/main/resources/META-INF/services/github.kasuminova.prototypemachinery.api.platform.PMPlatformProvider`

这允许主模组在“检测到现代后端存在”时使用更快的实现；否则自动回退到 Java 8 的默认路径。

---

## 3. Vector API：反射探测 + 安全回退

Modern Backend 中包含一个“位置变换（PositionTransform）后端选择器”：

- `modern-backend/src/main/java/.../accel/PositionTransformBackends.java`

其策略：

- 优先尝试通过反射加载 `VectorPositionTransformBackend`
- 如果失败（常见原因：没有开启 incubator module、JDK 不支持、类不可用等）则回退到 scalar 实现

Vector 实现位于：

- `modern-backend/src/vectorAccel/java/.../accel/vector/VectorPositionTransformBackend.java`

### 3.1 运行条件（必须）

Vector 实现依赖 `jdk.incubator.vector`，运行时需要允许该模块解析，例如：

- `--add-modules=jdk.incubator.vector`

在本工程的 Gradle 运行配置中，通过属性控制：

- `-Penable_vector_api=true`

### 3.2 调试：打印实际选用的后端

运行时设置 JVM System Property：

- `-Dpm.debugTransformBackend=true`

会在初始化阶段输出当前选用的后端（vector / scalar）。

---

## 4. 关于 vectorAccel：已合并进主 jar（不再需要额外 accel-jar）

Modern Backend 的 `vectorAccel` sourceSet 仍然存在，但其输出会被打进同一个 mod jar：

- 运行时仍由 `PositionTransformBackends` 反射决定是否使用
- 不再需要构建/复制单独的 accel jar

这样能减少工程复杂度，同时保留“未开启 Vector module 时自动回退”的安全性。

---

## 5. 相关实现入口

- 平台抽象：`PMPlatform*` + `PMPlatformManager`
- Modern 平台实现：`modern-backend/src/main/kotlin/.../platform/*`
- 顶点批处理管线（示例）：`modern-backend/src/main/java/.../accel/BatchedVertexPipeline.java`
- JMH 基准：`modern-backend/src/jmh/java/.../bench/*`
