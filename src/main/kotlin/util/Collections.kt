package github.kasuminova.prototypemachinery.util

import java.util.Collections

public fun <K, V> Map<K, V>.toReadOnly(): Map<K, V> = Collections.unmodifiableMap(this)

public fun <T> List<T>.toReadOnly(): List<T> = Collections.unmodifiableList(this)

public fun <T> Set<T>.toReadOnly(): Set<T> = Collections.unmodifiableSet(this)