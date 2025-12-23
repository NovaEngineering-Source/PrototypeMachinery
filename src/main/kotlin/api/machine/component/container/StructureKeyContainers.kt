package github.kasuminova.prototypemachinery.api.machine.component.container

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.FluidStack

/**
 * # StructureItemKeyContainer - Key-level Item Container
 * # StructureItemKeyContainer - 结构物品 Key 容器视图
 *
 * Key-level container view for items (based on [PMKey] + `Long` amount).
 * This is the preferred API for recipe IO and scanning.
 *
 * 基于 [PMKey] + `Long` 数量的物品容器视图。
 * 这是配方 IO 与扫描（parallelism/候选匹配）的首选 API。
 *
 * ## Why key-level? / 为什么使用 key-level？
 * - No `ItemStack` predicate matching / 不做 `ItemStack` predicate 匹配
 * - No per-slot snapshot/restore requirements / 不要求槽位级快照/回滚
 * - All amounts are `Long` / 所有数量语义均为 `Long`
 *
 * ## Port/Transaction semantics / 端口与事务语义
 * - [PortMode] controls whether the container can be used as INPUT/OUTPUT.
 * - [TransactionMode] controls simulate vs execute.
 *
 * - [PortMode] 控制该容器是否允许作为 输入/输出 端口。
 * - [TransactionMode] 控制 模拟/执行（是否产生副作用）。
 *
 * ## About unchecked methods / 关于 unchecked 方法
 * `insertUnchecked` / `extractUnchecked` ignore [PortMode] restrictions and are intended for rollback
 * and internal compensation logic. External callers should almost always use checked variants.
 *
 * `insertUnchecked` / `extractUnchecked` 会忽略 [PortMode] 约束，仅用于回滚或内部补偿逻辑。
 * 外部调用者几乎总是应该使用带 PortMode 检查的版本。
 */
public interface StructureItemKeyContainer : StructureComponent {

    public fun isAllowedPortMode(mode: PortMode): Boolean

    /** Inserts up to [amount] of [key]. Returns the amount actually inserted. / 插入最多 [amount]，返回实际插入量。 */
    public fun insert(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long

    /** Extracts up to [amount] of [key]. Returns the amount actually extracted. / 提取最多 [amount]，返回实际提取量。 */
    public fun extract(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). / 忽略 PortMode 的版本（用于回滚）。 */
    public fun insertUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). / 忽略 PortMode 的版本（用于回滚）。 */
    public fun extractUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long
}

/**
 * # StructureFluidKeyContainer - Key-level Fluid Container
 * # StructureFluidKeyContainer - 结构流体 Key 容器视图
 *
 * Key-level container view for fluids (based on [PMKey] + `Long` amount).
 *
 * 基于 [PMKey] + `Long` 数量的流体容器视图。
 *
 * NOTE / 注意：
 * Fluid key equality decides whether NBT / tag participates.
 * 流体键的相等性定义决定了 NBT / Tag 是否参与匹配（由具体 PMKey 实现决定）。
 *
 * Port/Transaction semantics are the same as [StructureItemKeyContainer].
 * 端口/事务语义与 [StructureItemKeyContainer] 相同。
 */
public interface StructureFluidKeyContainer : StructureComponent {

    public fun isAllowedPortMode(mode: PortMode): Boolean

    /** Inserts up to [amount] of [key]. Returns the amount actually inserted. / 插入最多 [amount]，返回实际插入量。 */
    public fun insert(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long

    /** Extracts up to [amount] of [key]. Returns the amount actually extracted. / 提取最多 [amount]，返回实际提取量。 */
    public fun extract(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). / 忽略 PortMode 的版本（用于回滚）。 */
    public fun insertUnchecked(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). / 忽略 PortMode 的版本（用于回滚）。 */
    public fun extractUnchecked(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long
}
