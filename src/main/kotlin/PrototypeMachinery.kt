package github.kasuminova.prototypemachinery

import net.minecraftforge.fml.common.Mod

@Mod(
    modid = Tags.MOD_ID,
    name = "Prototype Machinery",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2847,);",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
public object PrototypeMachinery {

}