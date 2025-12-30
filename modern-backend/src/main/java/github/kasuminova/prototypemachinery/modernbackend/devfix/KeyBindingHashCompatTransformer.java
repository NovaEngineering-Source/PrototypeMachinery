package github.kasuminova.prototypemachinery.modernbackend.devfix;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Dev-only bytecode patch for Cleanroom FG3 dev runs.
 *
 * In this environment, {@code net.minecraft.client.settings.KeyBinding} contains the Forge-added
 * {@code net.minecraftforge.client.settings.KeyBindingMap} static field under the obf name {@code b}
 * even when running in a deobfuscated (MCP) environment.
 *
 * ModularUI expects a field named {@code HASH} and uses a mixin accessor without an explicit target name,
 * so it fails to apply if {@code HASH} does not exist.
 *
 * This transformer adds a new static field {@code HASH} and initializes it from {@code b} in <clinit>.
 */
public class KeyBindingHashCompatTransformer implements IClassTransformer {

    private static final String TARGET_CLASS_DOT = "net.minecraft.client.settings.KeyBinding";
    private static final String TARGET_CLASS_INTERNAL = "net/minecraft/client/settings/KeyBinding";

    private static final String KEYBINDING_MAP_DESC = "Lnet/minecraftforge/client/settings/KeyBindingMap;";

    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        final String tn = transformedName != null ? transformedName : name;
        if (!TARGET_CLASS_DOT.equals(tn)) {
            return basicClass;
        }

        try {
            final ClassNode cn = new ClassNode();
            new ClassReader(basicClass).accept(cn, 0);

            boolean hasHash = false;
            boolean hasB = false;
            for (FieldNode f : cn.fields) {
                if ("HASH".equals(f.name) && KEYBINDING_MAP_DESC.equals(f.desc)) {
                    hasHash = true;
                }
                if ("b".equals(f.name) && KEYBINDING_MAP_DESC.equals(f.desc)) {
                    hasB = true;
                }
            }

            // Already compatible, or cannot patch safely.
            if (hasHash || !hasB) {
                return basicClass;
            }

            // Add: private static KeyBindingMap HASH;
            cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "HASH", KEYBINDING_MAP_DESC, null, null));

            // Ensure <clinit> exists and assigns HASH = b
            MethodNode clinit = null;
            for (MethodNode m : cn.methods) {
                if ("<clinit>".equals(m.name) && "()V".equals(m.desc)) {
                    clinit = m;
                    break;
                }
            }

            if (clinit == null) {
                clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                cn.methods.add(clinit);
                clinit.instructions = new InsnList();
                clinit.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET_CLASS_INTERNAL, "b", KEYBINDING_MAP_DESC));
                clinit.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, TARGET_CLASS_INTERNAL, "HASH", KEYBINDING_MAP_DESC));
                clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            } else {
                final InsnList patch = new InsnList();
                patch.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET_CLASS_INTERNAL, "b", KEYBINDING_MAP_DESC));
                patch.add(new FieldInsnNode(Opcodes.PUTSTATIC, TARGET_CLASS_INTERNAL, "HASH", KEYBINDING_MAP_DESC));

                // Insert before the last RETURN (TAIL)
                InsnNode ret = null;
                for (int i = clinit.instructions.size() - 1; i >= 0; i--) {
                    if (clinit.instructions.get(i) instanceof InsnNode) {
                        InsnNode in = (InsnNode) clinit.instructions.get(i);
                        if (in.getOpcode() == Opcodes.RETURN) {
                            ret = in;
                            break;
                        }
                    }
                }
                if (ret != null) {
                    clinit.instructions.insertBefore(ret, patch);
                } else {
                    // Fallback: append.
                    clinit.instructions.add(patch);
                    clinit.instructions.add(new InsnNode(Opcodes.RETURN));
                }
            }

            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            // Fail open: don't break class loading if something changes.
            t.printStackTrace();
            return basicClass;
        }
    }
}
