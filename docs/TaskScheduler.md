# 调度器（TaskScheduler）

TaskScheduler 用于承载机器运行时的“周期性任务/并发执行”的抽象。

## 代码位置

- 实现：`src/main/kotlin/impl/scheduler/TaskSchedulerImpl.kt`

## 生命周期

- 在 mod 运行期间注册到事件总线（见 `PrototypeMachinery.kt` 与相关 proxy）
- 服务器停止时会调用 `TaskSchedulerImpl.shutdown()` 做清理

> 具体调度策略、线程模型与任务类型以实现为准；本页作为导航与定位入口。
