package github.kasuminova.prototypemachinery.impl.scheduler

internal enum class SchedulerBackendType {
    JAVA,
    COROUTINES;

    companion object {
        fun parse(value: String?): SchedulerBackendType {
            if (value == null) return JAVA
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: JAVA
        }
    }
}
