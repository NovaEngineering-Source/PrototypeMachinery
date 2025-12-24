#loader crafttweaker reloadable

/*
 * PrototypeMachinery - Custom Structure Validators Demo
 * 自定义结构验证器演示
 *
 * This script can be reloaded with /ct reload
 * 此脚本可以使用 /ct reload 重新加载
 *
 * Place this file in: scripts/prototypemachinery/
 * 将此文件放置在：scripts/prototypemachinery/
 *
 * This script demonstrates how to register custom structure validators
 * using the PrototypeMachinery CraftTweaker API.
 * 此脚本演示如何使用 PrototypeMachinery 的 CraftTweaker API 注册自定义结构验证器。
 *
 * Validators can check world state, biome, dimension, position, and more
 * to control where structures can be placed.
 * 验证器可以检查世界状态、生物群系、维度、位置等，以控制结构的放置位置。
 */

import mods.prototypemachinery.StructureValidatorRegistry;

print("=== Loading Structure Validator Demo ===");

// ============================================================================
// Example 1: Height Validator (0-64)
// 示例 1：高度验证器（0-64）
// ============================================================================
// Only allows structures to be placed between Y=0 and Y=64
// 只允许结构放置在 Y=0 到 Y=64 之间
StructureValidatorRegistry.register("examples:height_0_64", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.y >= 0 && ctx.y <= 64;
});
print("✓ Registered height validator (0-64)");


// ============================================================================
// Example 2: Height Validator (100-200)
// 示例 2：高度验证器（100-200）
// ============================================================================
// Only allows structures to be placed between Y=100 and Y=200
// 只允许结构放置在 Y=100 到 Y=200 之间
StructureValidatorRegistry.register("examples:height_100_200", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.y >= 100 && ctx.y <= 200;
});
print("✓ Registered height validator (100-200)");


// ============================================================================
// Example 3: Biome Validator - Forest Only
// 示例 3：生物群系验证器 - 仅限森林
// ============================================================================
// Only allows structures in biomes with "forest" in the name
// 只允许生物群系名称包含 "forest" 的结构
StructureValidatorRegistry.register("examples:forest_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.biome.name.contains("forest");
});
print("✓ Registered forest validator");


// ============================================================================
// Example 4: Biome Validator - Plains Only
// 示例 4：生物群系验证器 - 仅限平原
// ============================================================================
// Only allows structures in plains biome
// 只允许平原生物群系中的结构
StructureValidatorRegistry.register("examples:plains_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.biome.name.contains("plains");
});
print("✓ Registered plains validator");


// ============================================================================
// Example 5: Day Only Validator
// 示例 5：仅限白天验证器
// ============================================================================
// Only allows structures to be built during day time (world time 0-12000)
// 只允许在白天（世界时间 0-12000）建造结构
StructureValidatorRegistry.register("examples:day_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    var time = world.getWorldTime() % 24000;
    return time >= 0 && time < 12000;
});
print("✓ Registered day-only validator");


// ============================================================================
// Example 6: Night Only Validator
// 示例 6：仅限夜晚验证器
// ============================================================================
// Only allows structures to be built during night time (world time 12000-24000)
// 只允许在夜晚（世界时间 12000-24000）建造结构
StructureValidatorRegistry.register("examples:night_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    var time = world.getWorldTime() % 24000;
    return time >= 12000;
});
print("✓ Registered night-only validator");


// ============================================================================
// Example 7: Overworld Only Validator
// 示例 7：仅限主世界验证器
// ============================================================================
// Only allows structures in the overworld dimension
// 只允许主世界维度中的结构
StructureValidatorRegistry.register("examples:overworld_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    return world.getDimensionType().contains("overworld");
});
print("✓ Registered overworld-only validator");


// ============================================================================
// Example 8: Nether Only Validator
// 示例 8：仅限下界验证器
// ============================================================================
// Only allows structures in the Nether dimension
// 只允许下界维度中的结构
StructureValidatorRegistry.register("examples:nether_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    return world.getDimensionType().contains("nether");
});
print("✓ Registered nether-only validator");


// ============================================================================
// Example 9: End Only Validator
// 示例 9：仅限末地验证器
// ============================================================================
// Only allows structures in the End dimension
// 只允许末地维度中的结构
StructureValidatorRegistry.register("examples:end_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    return world.getDimensionType().contains("end");
});
print("✓ Registered end-only validator");


// ============================================================================
// Example 10: Mountain Forest Validator
// 示例 10：山地森林验证器
// ============================================================================
// Only allows structures in forest biomes above Y=100
// 只允许 Y>100 的森林生物群系中的结构
StructureValidatorRegistry.register("examples:mountain_forest", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.biome.name.contains("forest") && ctx.y > 100;
});
print("✓ Registered mountain forest validator");


// ============================================================================
// Example 11: Clear Weather Only Validator
// 示例 11：仅限晴天验证器
// ============================================================================
// Only allows structures when the weather is clear (no rain)
// 只允许天气晴朗（无雨）时的结构
StructureValidatorRegistry.register("examples:clear_weather_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    return !world.isRaining();
});
print("✓ Registered clear weather validator");


// ============================================================================
// Example 12: High Temperature Biomes
// 示例 12：高温生物群系验证器
// ============================================================================
// Only allows structures in biomes with high temperature (> 0.8)
// 只允许高温生物群系（温度 > 0.8）中的结构
StructureValidatorRegistry.register("examples:hot_biomes_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.biome.temperature > 0.8;
});
print("✓ Registered hot biomes validator");


// ============================================================================
// Example 13: Desert Validator
// 示例 13：沙漠验证器
// ============================================================================
// Only allows structures in desert biomes
// 只允许沙漠生物群系中的结构
StructureValidatorRegistry.register("examples:desert_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.biome.name == "desert";
});
print("✓ Registered desert validator");


// ============================================================================
// Example 14: Snowy Biomes Only
// 示例 14：雪地生物群系验证器
// ============================================================================
// Only allows structures in snowy biomes
// 只允许雪地生物群系中的结构
StructureValidatorRegistry.register("examples:snowy_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    return ctx.biome.isSnowyBiome;
});
print("✓ Registered snowy biomes validator");


// ============================================================================
// Example 15: Surface World Only
// 示例 15：仅限表面世界验证器
// ============================================================================
// Only allows structures in surface worlds (overworld)
// 只允许表面世界（主世界）中的结构
StructureValidatorRegistry.register("examples:surface_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var world = ctx.world;
    return world.isSurfaceWorld();
});
print("✓ Registered surface world validator");


// ============================================================================
// Example 16: Check Block Type at Position
// 示例 16：检查位置的方块类型
// ============================================================================
// Only allows structures if there's stone at the validation position
// 只允许验证位置是石头的结构
StructureValidatorRegistry.register("examples:on_stone_only", function(ctx as mods.prototypemachinery.StructureMatchContext) {
    var block = ctx.world.getBlock(ctx.blockPos);
    return block.definition.id.contains("minecraft:stone");
});
print("✓ Registered on-stone validator");


// ============================================================================
// Usage in Structure JSON
// ============================================================================
// You can use these validators in your structure JSON files:
// 你可以在结构 JSON 文件中使用这些验证器：
//
// {
//   "id": "my_machine",
//   "validators": [
//     "examples:height_0_64",
//     "examples:forest_only"
//   ],
//   "blocks": [...]
// }
//
// Multiple validators will all need to pass (AND logic).
// 多个验证器都需要通过（AND 逻辑）。

print("=== Structure Validator Demo Loaded Successfully ===");
print("Registered 16 custom validators!");
print("Use them in your structure JSON files with the 'validators' array.");
