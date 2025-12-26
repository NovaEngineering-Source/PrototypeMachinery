#loader crafttweaker reloadable

/*
 * PrototypeMachinery - Gecko structure bindings demo
 * Gecko 结构级绑定示例（绑定 top/mid/tail 三段模型）
 *
 * Place this file in: scripts/prototypemachinery/
 */

import mods.prototypemachinery.render.RenderBindings;

val machineId = "prototypemachinery:structure_render_top_mid_tail_demo";

// Bind each segment model to its structure id.
RenderBindings.bindGeckoToStructure(
    machineId,
    "example_structure_render_top_mid_tail_top",
    RenderBindings.gecko()
        .geo("prototypemachinery:geo/test_top.geo.json")
        .texture("prototypemachinery:textures/geo/test_top.png")
    .animation("prototypemachinery:geo/animation/test_top.animation.json")
    // Extra translation in local units (blocks). Useful for small in-game alignment tweaks.
    // This offset rotates with the structure/controller orientation.
    // Note: for structure-bound rendering, the structure node's own offset is applied automatically.
    .modelOffset(0.0, 0.0, 0.0)
);

RenderBindings.bindGeckoToStructure(
    machineId,
    "example_structure_render_top_mid_tail_mid",
    RenderBindings.gecko()
        .geo("prototypemachinery:geo/test_mid.geo.json")
        .texture("prototypemachinery:textures/geo/test_mid.png")
    .animation("prototypemachinery:geo/animation/test_mid.animation.json")
    .modelOffset(0.0, 0.0, 0.0)
);

// MID repeats 3 times in the example structure (mid, mid_2, mid_3).
// Bind the same MID model to all three so they render as a continuous tube.
RenderBindings.bindGeckoToStructure(
    machineId,
    "example_structure_render_top_mid_tail_mid_2",
    RenderBindings.gecko()
        .geo("prototypemachinery:geo/test_mid.geo.json")
        .texture("prototypemachinery:textures/geo/test_mid.png")
    .animation("prototypemachinery:geo/animation/test_mid.animation.json")
    .modelOffset(0.0, 0.0, 0.0)
);

RenderBindings.bindGeckoToStructure(
    machineId,
    "example_structure_render_top_mid_tail_mid_3",
    RenderBindings.gecko()
        .geo("prototypemachinery:geo/test_mid.geo.json")
        .texture("prototypemachinery:textures/geo/test_mid.png")
    .animation("prototypemachinery:geo/animation/test_mid.animation.json")
    .modelOffset(0.0, 0.0, 0.0)
);

RenderBindings.bindGeckoToStructure(
    machineId,
    "example_structure_render_top_mid_tail_tail",
    RenderBindings.gecko()
        .geo("prototypemachinery:geo/test_tail.geo.json")
        .texture("prototypemachinery:textures/geo/test_tail.png")
    .animation("prototypemachinery:geo/animation/test_tail.animation.json")
    .modelOffset(0.0, 0.0, 0.0)
);
