export type CanvasView = {
  scale: number;
  offsetX: number;
  offsetY: number;
};

function snap(n: number, step: number) {
  return Math.round(n / step) * step;
}

// Clamp + snap, but always allow exact edges even if (max % step) != 0.
function snapWithEdges(n: number, step: number, min: number, max: number) {
  const snapped = snap(n, step);
  if (snapped < min) return min;
  if (snapped > max) return max;
  return snapped;
}

function snapSize(n: number, step: number) {
  const s = Math.max(1, Math.floor(step));
  return Math.max(1, snap(n, s));
}

export function createCanvasMath(params: {
  canvasW: number;
  canvasH: number;
  grid: number;
  view: CanvasView;
}) {
  const { canvasW, canvasH, grid, view } = params;

  // Konva dragBoundFunc receives/returns ABSOLUTE position (stage coordinates).
  // Our document stores WORLD coordinates (canvas space). Convert between them using Stage transform.
  const absToWorld = (abs: { x: number; y: number }) => {
    const s = Math.max(1e-6, view.scale);
    return {
      x: (abs.x - view.offsetX) / s,
      y: (abs.y - view.offsetY) / s,
    };
  };

  const worldToAbs = (world: { x: number; y: number }) => {
    const s = Math.max(1e-6, view.scale);
    return {
      x: view.offsetX + world.x * s,
      y: view.offsetY + world.y * s,
    };
  };

  const boundWorldPos = (dims: { w: number; h: number }, pos: { x: number; y: number }) => {
    const step = grid;
    const maxX = Math.max(0, canvasW - dims.w);
    const maxY = Math.max(0, canvasH - dims.h);
    const x = snapWithEdges(pos.x, step, 0, maxX);
    const y = snapWithEdges(pos.y, step, 0, maxY);
    return { x: Math.round(x), y: Math.round(y) };
  };

  const boundAbsPos = (dims: { w: number; h: number }, absPos: { x: number; y: number }) => {
    const world = absToWorld(absPos);
    const boundedWorld = boundWorldPos(dims, world);
    return worldToAbs(boundedWorld);
  };

  return {
    snapWithEdges,
    snapSize,
    absToWorld,
    worldToAbs,
    boundGuideWorldPos: boundWorldPos,
    boundGuideAbsPos: boundAbsPos,
    boundWidgetWorldPos: boundWorldPos,
    boundWidgetAbsPos: boundAbsPos,
  };
}
