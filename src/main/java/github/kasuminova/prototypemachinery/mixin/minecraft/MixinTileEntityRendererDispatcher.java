package github.kasuminova.prototypemachinery.mixin.minecraft;

import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to prevent multiblocked/component-model-hider from hiding the controller's TESR.
 *
 * When a block is marked as "disabled" by the model hider, TileEntityRendererDispatcher.getRenderer(TileEntity)
 * returns null for that tile entity, preventing the TESR from rendering. This mixin intercepts
 * the call and forces the renderer lookup by class instead of instance for our machine controllers,
 * bypassing the model hider's disabled check.
 */
@SuppressWarnings({"MethodMayBeStatic", "StaticVariableMayNotBeInitialized"})
@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Shadow
    public static TileEntityRendererDispatcher instance;

    /**
     * Prevents multiblocked/component-model-hider from hiding the controller's TESR.
     *
     * When getRenderer(TileEntity) is called for a MachineBlockEntity, we force
     * the lookup by class instead of instance, bypassing any per-instance disabled checks.
     */
    @Inject(
        method = "getRenderer(Lnet/minecraft/tileentity/TileEntity;)Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T extends TileEntity> void injectGetRenderer(TileEntity te, CallbackInfoReturnable<TileEntitySpecialRenderer<T>> cir) {
        if (te instanceof MachineBlockEntity) {
            cir.setReturnValue(instance.getRenderer(te.getClass()));
        }
    }
}
