package github.kasuminova.prototypemachinery.common.util

import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import org.apache.logging.log4j.Logger

@Suppress("UNNECESSARY_SAFE_CALL")
public fun Logger.warnWithBlockEntity(message: String, blockEntity: BlockEntity, throwable: Throwable? = null) {
    this.warn("$message [at world `${blockEntity.world?.provider?.dimensionType?.name}` ${blockEntity.pos}]", throwable)
}