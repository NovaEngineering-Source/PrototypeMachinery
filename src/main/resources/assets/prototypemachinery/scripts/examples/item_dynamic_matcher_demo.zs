#loader crafttweaker reloadable

import mods.prototypemachinery.recipe.PMRecipeRequirement;
import mods.prototypemachinery.recipe.ItemMatchers;

// Demonstrates:
// 1) oreDict as fuzzy input (expanded at load time)
// 2) dynamic item matcher (predicate-like) with a script lambda

// Register a dynamic matcher.
// - candidate: a concrete item variant from machine ports
// - pattern: the requirement pattern
// Return true if candidate should be accepted.
ItemMatchers.register("foo_eq", function(candidate, pattern) {
    // Same item id
    if (ItemMatchers.itemId(candidate) != ItemMatchers.itemId(pattern)) return false;

    // If pattern has foo(int), require candidate foo(int) equal.
    if (!ItemMatchers.hasNbtInt(pattern, "foo")) return true;
    if (!ItemMatchers.hasNbtInt(candidate, "foo")) return false;
    return ItemMatchers.nbtInt(candidate, "foo", 0) == ItemMatchers.nbtInt(pattern, "foo", 0);
});

// Example requirements (can be used in recipe registration):
// - OreDict fuzzy input (candidates are OreDictionary entries)
val oreInput = PMRecipeRequirement.itemOreDictFuzzyInput("in_ore", 2, "ingotIron");

// - Dynamic input: candidates are enumerated from ports at runtime.
//   For JEI display, we provide two representative candidates.
val dynInput = PMRecipeRequirement.itemDynamicInputWithDisplayed(
    "in_dyn",
    1,
    <minecraft:diamond>.withTag({foo: 1}),
    "foo_eq",
    [<minecraft:diamond>.withTag({foo: 1}), <minecraft:diamond>.withTag({foo: 2})]
);

// This file only demonstrates the API surface.
// See other bundled examples for full machine + recipe registration.
