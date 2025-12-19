import {pmTextureUrl} from '../../assets/pmAssets';

export type Rl = { namespace: string; path: string };

export function parseResourceLocation(raw?: string): Rl | undefined {
  if (!raw) return undefined;
  const s = raw.trim();
  if (!s) return undefined;
  const i = s.indexOf(':');
  if (i < 0) return { namespace: 'minecraft', path: s };
  const ns = s.slice(0, i).trim();
  const p = s.slice(i + 1).trim();
  if (!ns || !p) return undefined;
  return { namespace: ns, path: p };
}

export function shortVariantId(raw?: string): string | undefined {
  const rl = parseResourceLocation(raw);
  if (!rl) return raw?.trim() || undefined;
  if (rl.namespace === 'prototypemachinery') return rl.path;
  if (rl.namespace === 'minecraft') return rl.path;
  return `${rl.namespace}:${rl.path}`;
}

export function variantKeyToModuleKey(key: string): string {
  // examples:
  // 1x3 -> 1_3
  // 1o5x3 -> 1o5_3
  // 0o5x1 -> 0o5_1
  return key.replace('x', '_');
}

export function clamp01(n: unknown, fallback: number): number {
  const v = typeof n === 'number' && Number.isFinite(n) ? n : fallback;
  return Math.max(0, Math.min(1, v));
}

export function defaultEnergyFillRatio(role: unknown): number {
  const r = typeof role === 'string' ? role.trim().toUpperCase() : '';
  if (r === 'INPUT' || r === 'INPUT_PER_TICK' || r === 'INPUTPERTICK') return 0.65;
  if (r === 'OUTPUT' || r === 'OUTPUT_PER_TICK' || r === 'OUTPUTPERTICK') return 0.35;
  return 0.5;
}

export function inferEnergyLedPath(role: unknown): string {
  const r = typeof role === 'string' ? role.trim().toUpperCase() : '';
  if (r === 'INPUT' || r === 'INPUT_PER_TICK' || r === 'INPUTPERTICK') return 'gui/jei_recipeicons/energy_module/input_led.png';
  if (r === 'OUTPUT' || r === 'OUTPUT_PER_TICK' || r === 'OUTPUTPERTICK') return 'gui/jei_recipeicons/energy_module/output_led.png';
  return 'gui/jei_recipeicons/energy_module/io_led.png';
}

export function inferEnergyLayerPaths(el: { variantId?: string }): { empty: string; full: string } | undefined {
  const rl = parseResourceLocation(el.variantId);
  const p = rl?.path ?? '';
  if (!p.startsWith('energy/')) return undefined;
  const key = p.slice('energy/'.length);
  if (key === 'default') {
    return {
      empty: 'gui/jei_recipeicons/energy_module/default_in_empty.png',
      full: 'gui/jei_recipeicons/energy_module/default_in_full.png',
    };
  }
  const moduleKey = variantKeyToModuleKey(key);
  return {
    empty: `gui/jei_recipeicons/energy_module/${moduleKey}_empty.png`,
    full: `gui/jei_recipeicons/energy_module/${moduleKey}_full.png`,
  };
}

export function inferTankLayerPaths(el: { variantId?: string }): { base: string; top: string } | undefined {
  const rl = parseResourceLocation(el.variantId);
  const p = rl?.path ?? '';
  let key: string | undefined;
  if (p.startsWith('fluid/')) key = p.slice('fluid/'.length);
  if (p.startsWith('tank/')) key = p.slice('tank/'.length);
  if (!key) return undefined;
  const moduleKey = variantKeyToModuleKey(key);
  const moduleDir = moduleKey === '0o5_1' ? 'gas_module' : 'fluid_module';
  return {
    base: `gui/jei_recipeicons/${moduleDir}/${moduleKey}_base.png`,
    top: `gui/jei_recipeicons/${moduleDir}/${moduleKey}_top.png`,
  };
}

export function inferTexturePathFromElement(el: { type: string; variantId?: string; data?: Record<string, any> }): string | undefined {
  const explicit = typeof el.data?.texturePath === 'string' ? el.data.texturePath.trim() : '';
  if (explicit) return explicit.replace(/^\/+/, '');

  const rl = parseResourceLocation(el.variantId);
  const p = rl?.path ?? '';

  // Item slot frame
  if (p === 'slot/18' || p.startsWith('slot/')) {
    return 'gui/slot.png';
  }

  // Energy bar (use empty texture as preview)
  if (p.startsWith('energy/')) {
    const key = p.slice('energy/'.length);
    if (key === 'default') return 'gui/jei_recipeicons/energy_module/default_in_empty.png';
    const moduleKey = variantKeyToModuleKey(key);
    return `gui/jei_recipeicons/energy_module/${moduleKey}_empty.png`;
  }

  // Fluid/Gas tank frame (use base texture as preview)
  if (p.startsWith('fluid/') || p.startsWith('tank/')) {
    const key = p.slice(p.indexOf('/') + 1);
    const moduleKey = variantKeyToModuleKey(key);
    const moduleDir = moduleKey === '0o5_1' ? 'gas_module' : 'fluid_module';
    return `gui/jei_recipeicons/${moduleDir}/${moduleKey}_base.png`;
  }

  // Fallback by element type (use reasonable defaults so presets show something)
  if (el.type === 'slot') return 'gui/slot.png';
  if (el.type === 'energy') return 'gui/jei_recipeicons/energy_module/1_3_empty.png';
  if (el.type === 'tank') return 'gui/jei_recipeicons/fluid_module/1_3_base.png';

  return undefined;
}

export type ProgressDirection = 'RIGHT' | 'LEFT' | 'UP' | 'DOWN' | 'CIRCULAR_CW';

export function normalizeProgressDirection(raw: unknown): ProgressDirection {
  const s = typeof raw === 'string' ? raw.trim().toUpperCase() : '';
  if (s === 'LEFT' || s === 'RIGHT' || s === 'UP' || s === 'DOWN' || s === 'CIRCULAR_CW') return s as ProgressDirection;
  return 'RIGHT';
}

/**
 * JeiBackgroundSpec.parse rules (editor-side approximation):
 * - raw without ':' => relative to `prototypemachinery:textures/gui/jei_recipeicons/`
 * - raw with ':' => try to map `prototypemachinery:textures/...` back to PM textures root
 */
export function normalizeJeiBackgroundTexturePath(raw: unknown): string {
  const s = typeof raw === 'string' ? raw.trim() : '';
  if (!s) return 'gui/jei_recipeicons/jei_base.png';

  // Relative path (no namespace)
  if (!s.includes(':')) {
    const rel = s.replace(/^\/+/, '');
    return `gui/jei_recipeicons/${rel}`;
  }

  const rl = parseResourceLocation(s);
  if (!rl) return 'gui/jei_recipeicons/jei_base.png';
  if (rl.namespace !== 'prototypemachinery') return 'gui/jei_recipeicons/jei_base.png';

  // Typical form is: prototypemachinery:textures/gui/jei_recipeicons/jei_base.png
  const p = rl.path.replace(/^\/+/, '');
  if (p.startsWith('textures/')) return p.slice('textures/'.length);
  return p;
}

/**
 * Normalize a raw user input into the argument expected by LayoutBuilder.setBackgroundNineSlice().
 *
 * Mod-side rules (JeiBackgroundSpec.parse):
 * - if contains ':' => treated as a full resource location string
 * - else => treated as a path relative to `prototypemachinery:textures/gui/jei_recipeicons/`
 *
 * Editor convenience:
 * - if user picked a PM texture path like "gui/jei_recipeicons/jei_base.png",
 *   strip the prefix and export "jei_base.png".
 */
export function normalizeJeiBackgroundTextureArgForExport(raw: unknown): string {
  const s = typeof raw === 'string' ? raw.trim() : '';
  if (!s) return 'jei_base.png';
  if (s.includes(':')) return s;

  const rel = s.replace(/^\/+/, '');
  const prefix1 = 'gui/jei_recipeicons/';
  const prefix2 = 'textures/gui/jei_recipeicons/';
  if (rel.startsWith(prefix2)) return rel.slice(prefix2.length);
  if (rel.startsWith(prefix1)) return rel.slice(prefix1.length);
  return rel;
}

export function inferProgressModuleLayerPaths(data: Record<string, any> | undefined): { base: string; run: string } {
  const typeRaw = typeof data?.type === 'string' ? data.type.trim().toLowerCase() : '';
  const type = typeRaw || 'right';
  const [baseName, runName] = (() => {
    // Keep behavior aligned with ProgressModuleJeiDecorator.
    if (type === 'cool') return ['heat_run_0.png', 'heat_base.png'] as const;
    if (type === 'heat') return ['heat_base.png', 'heat_run_1.png'] as const;
    return [`${type}_base.png`, `${type}_run.png`] as const;
  })();

  return {
    base: `gui/jei_recipeicons/progress_module/${baseName}`,
    run: `gui/jei_recipeicons/progress_module/${runName}`,
  };
}

export function normalizeExplicitTexturePath(el: { data?: Record<string, any> }): string | undefined {
  const explicit = typeof el.data?.texturePath === 'string' ? el.data.texturePath.trim() : '';
  if (!explicit) return undefined;
  return explicit.replace(/^\/+/, '');
}

export function toPmTextureUrl(path?: string): string | undefined {
  return path ? pmTextureUrl(path) : undefined;
}
