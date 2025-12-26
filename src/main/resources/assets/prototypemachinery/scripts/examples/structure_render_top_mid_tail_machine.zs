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
//   - example_structure_render_top_mid_tail_top  (hollow 5x5 tube, repeats 5)
//   - example_structure_render_top_mid_tail_mid  (hollow 5x5 tube, repeats 1)
//   - example_structure_render_top_mid_tail_mid_2 (hollow 5x5 tube, repeats 1)
//   - example_structure_render_top_mid_tail_mid_3 (hollow 5x5 tube, repeats 1)
//   - example_structure_render_top_mid_tail_tail (hollow 5x5 tube, repeats 5)
val demo = MachineRegistry.create("prototypemachinery", "structure_render_top_mid_tail_demo");

demo.name("Structure Render Top/Mid/Tail Demo");
demo.structure("example_structure_render_top_mid_tail_root");

MachineRegistry.register(demo);
