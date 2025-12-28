package github.kasuminova.prototypemachinery.mixin.minecraft;

import github.kasuminova.prototypemachinery.client.impl.render.MachineRenderDispatcher;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into the render pipeline after all TESRs have completed their batch render.
 *
 * This allows PrototypeMachinery to perform centralized, ordered rendering of all machine models:
 * 1. All opaque (DEFAULT) passes from all machines
 * 2. All transparent (TRANSPARENT) passes from all machines
 * 3. Bloom passes deferred to GT bloom callback
 *
 * By rendering after TileEntityRendererDispatcher.drawBatch(), we ensure proper render order
 * across all machine models while maintaining compatibility with GT bloom post-processing.
 */
@SuppressWarnings("MethodMayBeStatic")
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    /**
     * Injects after TileEntityRendererDispatcher.drawBatch() to flush our centralized render queue.
     *
     * RenderPass 0 check prevents double rendering when Forge's multi-pass rendering is active.
     */
    @Inject(
        method = "renderEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;drawBatch(I)V",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void hookAfterTESRBatch(
        final Entity renderViewEntity,
        final ICamera camera,
        final float partialTicks,
        final CallbackInfo ci
    ) {
        // Only render on pass 0 to prevent double rendering with multi-pass systems
        if (MinecraftForgeClient.getRenderPass() == 0) {
            MachineRenderDispatcher.INSTANCE.flush();
        }
    }

    /**
     * Global TESRs (TileEntitySpecialRenderer#isGlobalRenderer=true) are rendered *after* drawBatch.
     *
     * Our machine TESR is sometimes marked global (e.g. very large bound Gecko models).
     * Those submissions would otherwise miss the after-drawBatch flush and only be drawn
     * by the late safety flush (RenderWorldLast), causing ordering issues and one-frame delay.
     *
     * Flushing again at the end is cheap (no-op when empty) and makes global-rendered machines
     * participate in the same centralized ordered pipeline.
     */
    @Inject(
        method = "renderEntities",
        at = @At("RETURN")
    )
    private void hookAfterGlobalTESR(
        final Entity renderViewEntity,
        final ICamera camera,
        final float partialTicks,
        final CallbackInfo ci
    ) {
        if (MinecraftForgeClient.getRenderPass() == 0) {
            MachineRenderDispatcher.INSTANCE.flush();
        }
    }
}
