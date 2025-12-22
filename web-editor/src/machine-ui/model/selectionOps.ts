export type Rect = { x: number; y: number; w: number; h: number };

export type WidgetBox = Rect & { id: string; locked?: boolean };

export type SelectionBounds = {
  minX: number;
  minY: number;
  maxX2: number;
  maxY2: number;
};

function asInt(n: unknown, fallback: number) {
  const v = Number(n);
  if (!Number.isFinite(v)) return fallback;
  return Math.trunc(v);
}

export function normalizeWidgetBox(w: any): WidgetBox {
  const ww = Math.max(1, asInt(w?.w, 1));
  const hh = Math.max(1, asInt(w?.h, 1));
  return {
    id: String(w?.id ?? ''),
    x: asInt(w?.x, 0),
    y: asInt(w?.y, 0),
    w: ww,
    h: hh,
    locked: Boolean(w?.locked),
  };
}

export function computeSelectionBounds(items: WidgetBox[]): SelectionBounds | undefined {
  if (!items || items.length === 0) return undefined;
  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX2 = Number.NEGATIVE_INFINITY;
  let maxY2 = Number.NEGATIVE_INFINITY;

  for (const it of items) {
    if (!it) continue;
    minX = Math.min(minX, it.x);
    minY = Math.min(minY, it.y);
    maxX2 = Math.max(maxX2, it.x + it.w);
    maxY2 = Math.max(maxY2, it.y + it.h);
  }

  if (!Number.isFinite(minX) || !Number.isFinite(minY) || !Number.isFinite(maxX2) || !Number.isFinite(maxY2)) return undefined;
  return { minX, minY, maxX2, maxY2 };
}

export function snapToGrid(n: number, grid: number) {
  const g = Math.max(1, Math.floor(Number(grid) || 1));
  return Math.round(n / g) * g;
}

export function clampWidgetPos(box: Rect, canvasW: number, canvasH: number) {
  const cw = Math.max(1, Math.floor(Number(canvasW) || 1));
  const ch = Math.max(1, Math.floor(Number(canvasH) || 1));
  const w = Math.max(1, Math.floor(Number(box.w) || 1));
  const h = Math.max(1, Math.floor(Number(box.h) || 1));
  const maxX = Math.max(0, cw - w);
  const maxY = Math.max(0, ch - h);
  return {
    x: Math.max(0, Math.min(maxX, Math.trunc(Number(box.x) || 0))),
    y: Math.max(0, Math.min(maxY, Math.trunc(Number(box.y) || 0))),
  };
}

export type AlignKind = 'left' | 'hcenter' | 'right' | 'top' | 'vcenter' | 'bottom' | 'center';

export function computeAlignPatches(
  items: WidgetBox[],
  kind: AlignKind,
  opts: { canvasW: number; canvasH: number; grid: number; includeLockedInBounds?: boolean },
): Record<string, { x?: number; y?: number }> {
  const includeLockedInBounds = opts.includeLockedInBounds ?? true;
  const itemsForBounds = includeLockedInBounds ? items : items.filter((it) => !it.locked);
  const bounds = computeSelectionBounds(itemsForBounds);
  if (!bounds) return {};

  const cx = (bounds.minX + bounds.maxX2) / 2;
  const cy = (bounds.minY + bounds.maxY2) / 2;

  const patches: Record<string, { x?: number; y?: number }> = {};

  for (const it of items) {
    if (it.locked) continue;

    let x = it.x;
    let y = it.y;

    if (kind === 'left') x = bounds.minX;
    if (kind === 'right') x = bounds.maxX2 - it.w;
    if (kind === 'hcenter' || kind === 'center') x = cx - it.w / 2;

    if (kind === 'top') y = bounds.minY;
    if (kind === 'bottom') y = bounds.maxY2 - it.h;
    if (kind === 'vcenter' || kind === 'center') y = cy - it.h / 2;

    x = snapToGrid(x, opts.grid);
    y = snapToGrid(y, opts.grid);

    const clamped = clampWidgetPos({ x, y, w: it.w, h: it.h }, opts.canvasW, opts.canvasH);

    const px = clamped.x;
    const py = clamped.y;
    if (px !== it.x || py !== it.y) patches[it.id] = { x: px, y: py };
  }

  return patches;
}

export type DistributeAxis = 'x' | 'y';

export function computeDistributePatches(
  items: WidgetBox[],
  axis: DistributeAxis,
  opts: { canvasW: number; canvasH: number; grid: number },
): Record<string, { x?: number; y?: number }> {
  const movable = items.filter((it) => !it.locked);
  if (movable.length <= 2) return {};

  const along = (it: WidgetBox) => (axis === 'x' ? it.x + it.w / 2 : it.y + it.h / 2);
  const sorted = [...movable].sort((a, b) => along(a) - along(b));

  const first = along(sorted[0]);
  const last = along(sorted[sorted.length - 1]);
  const step = sorted.length > 1 ? (last - first) / (sorted.length - 1) : 0;

  const patches: Record<string, { x?: number; y?: number }> = {};

  for (let i = 0; i < sorted.length; i++) {
    const it = sorted[i];
    const targetCenter = first + step * i;

    if (axis === 'x') {
      let x = targetCenter - it.w / 2;
      x = snapToGrid(x, opts.grid);
      const clamped = clampWidgetPos({ x, y: it.y, w: it.w, h: it.h }, opts.canvasW, opts.canvasH);
      if (clamped.x !== it.x) patches[it.id] = { x: clamped.x };
    } else {
      let y = targetCenter - it.h / 2;
      y = snapToGrid(y, opts.grid);
      const clamped = clampWidgetPos({ x: it.x, y, w: it.w, h: it.h }, opts.canvasW, opts.canvasH);
      if (clamped.y !== it.y) patches[it.id] = { y: clamped.y };
    }
  }

  return patches;
}
