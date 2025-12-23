# Task scheduler (TaskScheduler)

TaskScheduler is the abstraction used by the machine runtime to host "periodic tasks / concurrent execution".

## Code locations

- implementation: `src/main/kotlin/impl/scheduler/TaskSchedulerImpl.kt`

## Lifecycle

- registered to the event bus during mod runtime (see `PrototypeMachinery.kt` and the proxy wiring)
- server shutdown triggers `TaskSchedulerImpl.shutdown()` for cleanup

> Exact scheduling strategy, thread model, and task categories are defined by the implementation; this page is an entry point for navigation.

---

Chinese original:

- [`docs/TaskScheduler.md`](../TaskScheduler.md)
