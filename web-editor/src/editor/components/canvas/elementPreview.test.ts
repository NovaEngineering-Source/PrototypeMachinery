import {describe, expect, it} from 'vitest';
import {
    inferEnergyLayerPaths,
    inferProgressModuleLayerPaths,
    normalizeJeiBackgroundTextureArgForExport,
    normalizeJeiBackgroundTexturePath,
    normalizeProgressDirection,
    parseResourceLocation,
} from './elementPreview';

describe('elementPreview', () => {
  it('parseResourceLocation', () => {
    expect(parseResourceLocation(undefined)).toBeUndefined();
    expect(parseResourceLocation('')).toBeUndefined();
    expect(parseResourceLocation('  minecraft:stone  ')).toEqual({ namespace: 'minecraft', path: 'stone' });
    expect(parseResourceLocation('foo/bar')).toEqual({ namespace: 'minecraft', path: 'foo/bar' });
    expect(parseResourceLocation('mod:foo/bar')).toEqual({ namespace: 'mod', path: 'foo/bar' });
  });

  it('inferEnergyLayerPaths from variantId', () => {
    expect(inferEnergyLayerPaths({ variantId: 'prototypemachinery:energy/1x3' })).toEqual({
      empty: 'gui/jei_recipeicons/energy_module/1_3_empty.png',
      full: 'gui/jei_recipeicons/energy_module/1_3_full.png',
    });

    expect(inferEnergyLayerPaths({ variantId: 'prototypemachinery:energy/default' })).toEqual({
      empty: 'gui/jei_recipeicons/energy_module/default_in_empty.png',
      full: 'gui/jei_recipeicons/energy_module/default_in_full.png',
    });
  });

  it('normalizeJeiBackgroundTexturePath follows JeiBackgroundSpec rules', () => {
    expect(normalizeJeiBackgroundTexturePath(undefined)).toBe('gui/jei_recipeicons/jei_base.png');
    expect(normalizeJeiBackgroundTexturePath('')).toBe('gui/jei_recipeicons/jei_base.png');
    expect(normalizeJeiBackgroundTexturePath('jei_base.png')).toBe('gui/jei_recipeicons/jei_base.png');
    expect(
      normalizeJeiBackgroundTexturePath('prototypemachinery:textures/gui/jei_recipeicons/jei_base.png'),
    ).toBe('gui/jei_recipeicons/jei_base.png');
  });

  it('normalizeJeiBackgroundTextureArgForExport strips jei_recipeicons prefix', () => {
    expect(normalizeJeiBackgroundTextureArgForExport(undefined)).toBe('jei_base.png');
    expect(normalizeJeiBackgroundTextureArgForExport('')).toBe('jei_base.png');
    expect(normalizeJeiBackgroundTextureArgForExport('jei_base.png')).toBe('jei_base.png');
    expect(normalizeJeiBackgroundTextureArgForExport('gui/jei_recipeicons/jei_base.png')).toBe('jei_base.png');
    expect(normalizeJeiBackgroundTextureArgForExport('textures/gui/jei_recipeicons/jei_base.png')).toBe('jei_base.png');
    expect(normalizeJeiBackgroundTextureArgForExport('mymod:textures/gui/foo.png')).toBe('mymod:textures/gui/foo.png');
  });

  it('inferProgressModuleLayerPaths matches mod-side naming', () => {
    expect(inferProgressModuleLayerPaths({ type: 'right' })).toEqual({
      base: 'gui/jei_recipeicons/progress_module/right_base.png',
      run: 'gui/jei_recipeicons/progress_module/right_run.png',
    });

    expect(inferProgressModuleLayerPaths({ type: 'heat' })).toEqual({
      base: 'gui/jei_recipeicons/progress_module/heat_base.png',
      run: 'gui/jei_recipeicons/progress_module/heat_run_1.png',
    });

    expect(inferProgressModuleLayerPaths({ type: 'cool' })).toEqual({
      base: 'gui/jei_recipeicons/progress_module/heat_run_0.png',
      run: 'gui/jei_recipeicons/progress_module/heat_base.png',
    });
  });

  it('normalizeProgressDirection', () => {
    expect(normalizeProgressDirection(undefined)).toBe('RIGHT');
    expect(normalizeProgressDirection('left')).toBe('LEFT');
    expect(normalizeProgressDirection('UP')).toBe('UP');
    expect(normalizeProgressDirection('something')).toBe('RIGHT');
  });
});
