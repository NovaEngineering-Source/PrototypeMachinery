package github.kasuminova.prototypemachinery.modernbackend.devfix;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Dev-only compatibility coremod.
 *
 * Loaded via JVM arg in Unimined run configs (fml.coreMods.load) so it will not run in normal environments.
 *
 * Purpose: patch {@code net.minecraft.client.settings.KeyBinding} to expose a {@code HASH} field expected by
 * ModularUI in some dev environments.
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(0)
public class DevKeyBindingHashCoremod implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{KeyBindingHashCompatTransformer.class.getName()};
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
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
