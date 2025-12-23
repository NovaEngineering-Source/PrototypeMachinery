package github.kasuminova.prototypemachinery.api.util

/**
 * Port mode/direction from the machine/recipe runtime perspective.
 *
 * 端口模式/方向（从机器/配方运行时视角）：
 * - [INPUT]：机器向该端口写入（插入/输出到端口）。
 * - [OUTPUT]：机器从该端口读取（提取/从端口输入到机器）。
 */
public enum class PortMode {
    INPUT,
    OUTPUT;
}
