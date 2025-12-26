package github.kasuminova.prototypemachinery.mixin;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Early mixin loader for PrototypeMachinery.
 *
 * This loader is invoked during FML loading to inject mixin configurations
 * before most classes are loaded.
 */
@SuppressWarnings("unused")
public class PMMixinLoader implements IFMLLoadingPlugin {

    public PMMixinLoader() {
        Mixins.addConfiguration("mixins.prototypemachinery.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(final Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
