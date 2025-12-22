import {describe, expect, it} from 'vitest';
import {computeAlignPatches, computeDistributePatches, type WidgetBox} from './selectionOps';

describe('selectionOps', () => {
  it('alignSelection: respects locked, but includes it in bounds', () => {
    const items: WidgetBox[] = [
      { id: 'a', x: 0, y: 0, w: 10, h: 10, locked: false },
      { id: 'b', x: 100, y: 0, w: 10, h: 10, locked: true },
    ];

    const patches = computeAlignPatches(items, 'right', { canvasW: 200, canvasH: 200, grid: 1, includeLockedInBounds: true });
    expect(patches).toEqual({ a: { x: 100, y: 0 } });
  });

  it('alignSelection: centers within selection bounds', () => {
    const items: WidgetBox[] = [
      { id: 'a', x: 0, y: 0, w: 10, h: 10 },
      { id: 'b', x: 30, y: 0, w: 10, h: 10 },
    ];
    // bounds: minX=0 maxX2=40 => center=20 => x = 20 - 5 = 15
    const patches = computeAlignPatches(items, 'hcenter', { canvasW: 200, canvasH: 200, grid: 1 });
    expect(patches.a?.x).toBe(15);
    expect(patches.b?.x).toBe(15);
  });

  it('distributeSelection: distributes by centers (x)', () => {
    const items: WidgetBox[] = [
      { id: 'a', x: 0, y: 0, w: 10, h: 10 }, // center 5
      { id: 'b', x: 40, y: 0, w: 10, h: 10 }, // center 45
      { id: 'c', x: 90, y: 0, w: 10, h: 10 }, // center 95
    ];

    const patches = computeDistributePatches(items, 'x', { canvasW: 200, canvasH: 200, grid: 1 });
    // centers should become 5, 50, 95 => x should become 0, 45, 90
    expect(patches).toEqual({ b: { x: 45 } });
  });

  it('distributeSelection: snaps to grid', () => {
    const items: WidgetBox[] = [
      { id: 'a', x: 0, y: 0, w: 10, h: 10 }, // center 5
      { id: 'b', x: 41, y: 0, w: 10, h: 10 }, // center 46
      { id: 'c', x: 90, y: 0, w: 10, h: 10 }, // center 95
    ];

    const patches = computeDistributePatches(items, 'x', { canvasW: 200, canvasH: 200, grid: 8 });
    // without snap, b center => 50 => x=45; snap to 8 => 48
    expect(patches.b?.x).toBe(48);
  });
});
