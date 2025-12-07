package github.kasuminova.prototypemachinery.common.util

import net.minecraftforge.fml.common.eventhandler.EventBus
import kotlin.reflect.KClass

public fun EventBus.register(eventClass: KClass<*>): Unit = register(eventClass.java)
