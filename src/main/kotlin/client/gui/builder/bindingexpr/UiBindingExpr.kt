package github.kasuminova.prototypemachinery.client.gui.builder.bindingexpr

/**
 * Lightweight expression parser for UI binding keys.
 *
 * Syntax: func(arg0;arg1;...)
 * - Uses ';' as separator to avoid comma conflicts.
 * - Supports nesting.
 */
internal sealed interface UiDoubleExpr {
    fun eval(resolve: (String) -> Double): Double
}

internal sealed interface UiBoolExpr {
    fun eval(resolve: (String) -> Boolean): Boolean
}

internal data class UiDoubleKey(val key: String) : UiDoubleExpr {
    override fun eval(resolve: (String) -> Double): Double = resolve(key)
}

internal data class UiDoubleConst(val value: Double) : UiDoubleExpr {
    override fun eval(resolve: (String) -> Double): Double = value
}

internal data class UiDoubleClamp(val value: UiDoubleExpr, val min: Double, val max: Double) : UiDoubleExpr {
    override fun eval(resolve: (String) -> Double): Double = value.eval(resolve).coerceIn(min, max)
}

internal data class UiDoubleNorm(val value: UiDoubleExpr, val min: Double, val max: Double) : UiDoubleExpr {
    override fun eval(resolve: (String) -> Double): Double {
        val x = value.eval(resolve)
        val denom = (max - min)
        if (denom == 0.0) return 0.0
        return ((x - min) / denom).coerceIn(0.0, 1.0)
    }
}

internal data class UiBoolKey(val key: String) : UiBoolExpr {
    override fun eval(resolve: (String) -> Boolean): Boolean = resolve(key)
}

internal data class UiBoolNot(val value: UiBoolExpr) : UiBoolExpr {
    override fun eval(resolve: (String) -> Boolean): Boolean = !value.eval(resolve)
}

internal data class UiBoolAnd(val values: List<UiBoolExpr>) : UiBoolExpr {
    override fun eval(resolve: (String) -> Boolean): Boolean = values.all { it.eval(resolve) }
}

internal data class UiBoolOr(val values: List<UiBoolExpr>) : UiBoolExpr {
    override fun eval(resolve: (String) -> Boolean): Boolean = values.any { it.eval(resolve) }
}

internal fun parseUiDoubleExpr(raw: String): UiDoubleExpr {
    val s = raw.trim()
    if (s.isEmpty()) return UiDoubleConst(0.0)

    // number literal
    s.toDoubleOrNull()?.let { return UiDoubleConst(it) }

    val call = parseCall(s) ?: return UiDoubleKey(s)
    val name = call.name.lowercase()
    val args = call.args

    return when (name) {
        "clamp" -> {
            if (args.size != 3) UiDoubleKey(s)
            else {
                val v = parseUiDoubleExpr(args[0])
                val min = args[1].trim().toDoubleOrNull() ?: 0.0
                val max = args[2].trim().toDoubleOrNull() ?: 1.0
                UiDoubleClamp(v, min, max)
            }
        }

        "norm" -> {
            if (args.size != 3) UiDoubleKey(s)
            else {
                val v = parseUiDoubleExpr(args[0])
                val min = args[1].trim().toDoubleOrNull() ?: 0.0
                val max = args[2].trim().toDoubleOrNull() ?: 1.0
                UiDoubleNorm(v, min, max)
            }
        }

        else -> UiDoubleKey(s)
    }
}

internal fun parseUiBoolExpr(raw: String): UiBoolExpr {
    val s = raw.trim()
    if (s.isEmpty()) return UiBoolKey("__false")

    val call = parseCall(s) ?: return UiBoolKey(s)
    val name = call.name.lowercase()
    val args = call.args

    return when (name) {
        "not" -> {
            if (args.size != 1) UiBoolKey(s) else UiBoolNot(parseUiBoolExpr(args[0]))
        }

        "and" -> {
            if (args.isEmpty()) UiBoolKey(s) else UiBoolAnd(args.map { parseUiBoolExpr(it) })
        }

        "or" -> {
            if (args.isEmpty()) UiBoolKey(s) else UiBoolOr(args.map { parseUiBoolExpr(it) })
        }

        else -> UiBoolKey(s)
    }
}

private data class ParsedCall(val name: String, val args: List<String>)

private fun parseCall(s: String): ParsedCall? {
    val nameEnd = s.indexOf('(')
    if (nameEnd <= 0) return null
    if (!s.endsWith(')')) return null

    val name = s.substring(0, nameEnd).trim()
    if (name.isEmpty()) return null

    val inside = s.substring(nameEnd + 1, s.length - 1)
    val args = splitTopLevelArgs(inside)
    return ParsedCall(name, args)
}

private fun splitTopLevelArgs(s: String): List<String> {
    val out = ArrayList<String>()
    var depth = 0
    var start = 0

    fun push(end: Int) {
        val part = s.substring(start, end).trim()
        if (part.isNotEmpty()) out.add(part)
    }

    for (i in s.indices) {
        val c = s[i]
        when (c) {
            '(' -> depth++
            ')' -> if (depth > 0) depth--
            ';' -> if (depth == 0) {
                push(i)
                start = i + 1
            }
        }
    }

    push(s.length)
    return out
}
