#loader preinit

/*
 * PrototypeMachinery - Structure Render Top/Mid/Tail Demo
 * 三段结构渲染示例（top/mid/tail）
 *
 * Place this file in: scripts/prototypemachinery/
 */

import mods.prototypemachinery.MachineRegistry;

// This machine uses the bundled structure example:
// - root id: example_structure_render_top_mid_tail_root
// - children:
//   - example_structure_render_top_mid_tail_top  (template, 1x segment)
//   - example_structure_render_top_mid_tail_mid  (slice, repeats 3)
//   - example_structure_render_top_mid_tail_tail (template, 1x segment)
//
// If you ran an older version of this example before:
// - delete the stale mid_2/mid_3 JSONs in config/prototypemachinery/structures/
//   (they are no longer used by the root structure).
val demo = MachineRegistry.create("prototypemachinery", "structure_render_top_mid_tail_demo");

demo.name("Structure Render Top/Mid/Tail Demo");
demo.structure("example_structure_render_top_mid_tail_root");

MachineRegistry.register(demo);
