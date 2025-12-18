package github.kasuminova.prototypemachinery.integration.crafttweaker.bracket;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.BracketHandler;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.zenscript.IBracketHandler;
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType;
import github.kasuminova.prototypemachinery.integration.crafttweaker.MachineComponentTypeLookup;
import stanhebben.zenscript.compiler.IEnvironmentGlobal;
import stanhebben.zenscript.expression.ExpressionCallStatic;
import stanhebben.zenscript.expression.ExpressionString;
import stanhebben.zenscript.parser.Token;
import stanhebben.zenscript.symbols.IZenSymbol;
import stanhebben.zenscript.type.natives.IJavaMethod;

import java.util.List;

/**
 * Bracket handler for machine component types.
 *
 * Usage examples:
 * - <machinecomponent:prototypemachinery:factory_recipe_processor>
 * - <pmcomponent:factory_recipe_processor>
 */
@ZenRegister
@BracketHandler(priority = 100)
public class BracketHandlerMachineComponentType implements IBracketHandler {

    private static final String DEFAULT_NAMESPACE = "prototypemachinery";

    private final IJavaMethod method;

    public BracketHandlerMachineComponentType() {
        method = CraftTweakerAPI.getJavaMethod(BracketHandlerMachineComponentType.class, "getComponentType", String.class);
    }

    public static MachineComponentType<?> getComponentType(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return null;

        if (!trimmed.contains(":")) {
            trimmed = DEFAULT_NAMESPACE + ":" + trimmed;
        }

        return MachineComponentTypeLookup.get(trimmed);
    }

    @Override
    public IZenSymbol resolve(IEnvironmentGlobal environment, List<Token> tokens) {
        if (tokens == null || tokens.size() < 3) return null;

        String head = tokens.get(0).getValue();
        if (!"machinecomponent".equals(head) && !"pmcomponent".equals(head) && !"pmcomp".equals(head)) return null;
        if (!":".equals(tokens.get(1).getValue())) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < tokens.size(); i++) {
            sb.append(tokens.get(i).getValue());
        }

        String id = sb.toString();
        if (id.isEmpty()) return null;

        // Validate once at parse time to avoid creating a symbol for unknown ids.
        if (getComponentType(id) == null) return null;

        return position -> new ExpressionCallStatic(
                position,
                environment,
                method,
                new ExpressionString(position, id)
        );
    }

    @Override
    public Class<?> getReturnedClass() {
        return MachineComponentType.class;
    }
}
