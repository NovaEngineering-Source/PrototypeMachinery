export function typeSuggestions(): string[] {
  return [
    'slot',
    'tank',
    'energy',
    'parallelism',
    'decorator',
    'text',
    'custom',
  ];
}

export function typeIdSuggestionsForType(type: string): string[] {
  switch (type) {
    case 'slot':
      return ['prototypemachinery:item'];
    case 'tank':
      return ['prototypemachinery:fluid'];
    case 'energy':
      return ['prototypemachinery:energy'];
    case 'parallelism':
      return ['prototypemachinery:parallelism'];
    case 'decorator':
      return [];
    default:
      return ['prototypemachinery:item', 'prototypemachinery:fluid', 'prototypemachinery:energy'];
  }
}

export function decoratorIdSuggestions(): string[] {
  return [
    'prototypemachinery:decorator/background',
    'prototypemachinery:decorator/progress',
    'prototypemachinery:decorator/progress_module',
    'prototypemachinery:decorator/recipe_duration',
  ];
}

function uniqSorted(arr: string[]): string[] {
  return Array.from(new Set(arr.filter(Boolean))).sort((a, b) => a.localeCompare(b));
}

function moduleKeyToVariantKey(moduleKey: string): string {
  // 1_3 -> 1x3
  // 0o5_1 -> 0o5x1
  return moduleKey.replace('_', 'x');
}

export function variantIdSuggestionsForType(pmPaths: string[] | null | undefined, type: string): string[] {
  const base: string[] = [];

  if (type === 'decorator') {
    return decoratorIdSuggestions();
  }

  if (type === 'slot') {
    // slot variants are not strongly standardized in the editor yet.
    base.push('prototypemachinery:slot/1x1');
    base.push('prototypemachinery:slot/18');
  }

  if (!pmPaths || pmPaths.length === 0) {
    if (type === 'energy') base.push('prototypemachinery:energy/1x3');
    if (type === 'tank') base.push('prototypemachinery:tank/1x3');
    return uniqSorted(base);
  }

  if (type === 'energy') {
    base.push('prototypemachinery:energy/default');
    for (const p of pmPaths) {
      const m = p.match(/^gui\/jei_recipeicons\/energy_module\/(.+?)_empty\.png$/);
      if (!m) continue;
      const moduleKey = m[1];
      // skip special default texture naming
      if (moduleKey.includes('default')) continue;
      base.push(`prototypemachinery:energy/${moduleKeyToVariantKey(moduleKey)}`);
    }
  }

  if (type === 'tank') {
    for (const p of pmPaths) {
      const m = p.match(/^gui\/jei_recipeicons\/(?:fluid_module|gas_module)\/(.+?)_base\.png$/);
      if (!m) continue;
      const moduleKey = m[1];
      base.push(`prototypemachinery:tank/${moduleKeyToVariantKey(moduleKey)}`);
    }
  }

  return uniqSorted(base);
}
