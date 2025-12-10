package github.kasuminova.prototypemachinery.common.util

import net.minecraft.util.math.BlockPos

/**
 * Extension functions for BlockPos arithmetic operations.
 * BlockPos 的算术运算扩展函数。
 */

/**
 * Multiply a BlockPos by a scalar (operator overload).
 * 将 BlockPos 乘以标量（操作符重载）。
 */
public operator fun BlockPos.times(scalar: Int): BlockPos = BlockPos(this.x * scalar, this.y * scalar, this.z * scalar)

/**
 * Multiply a BlockPos by a scalar (operator overload).
 * 将 BlockPos 乘以标量（操作符重载）。
 */
public operator fun Int.times(pos: BlockPos): BlockPos = BlockPos(pos.x * this, pos.y * this, pos.z * this)

/**
 * Divide a BlockPos by a scalar (operator overload).
 * 将 BlockPos 除以标量（操作符重载）。
 */
public operator fun BlockPos.div(scalar: Int): BlockPos = BlockPos(this.x / scalar, this.y / scalar, this.z / scalar)

/**
 * Add two BlockPos (operator overload, alternative to vanilla add).
 * 将两个 BlockPos 相加（操作符重载，作为原版 add 的替代）。
 */
public operator fun BlockPos.plus(other: BlockPos): BlockPos = this.add(other)

/**
 * Subtract two BlockPos (operator overload, alternative to vanilla subtract).
 * 将两个 BlockPos 相减（操作符重载，作为原版 subtract 的替代）。
 */
public operator fun BlockPos.minus(other: BlockPos): BlockPos = this.subtract(other)

/**
 * Negate a BlockPos (operator overload).
 * 取 BlockPos 的负值（操作符重载）。
 */
public operator fun BlockPos.unaryMinus(): BlockPos = BlockPos(-this.x, -this.y, -this.z)
