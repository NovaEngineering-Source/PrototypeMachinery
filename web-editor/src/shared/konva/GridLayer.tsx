import {Rect} from 'react-konva';

export function GridLayer(props: {
  width: number;
  height: number;
  grid: number;
  color: string;
  opacity?: number;
  thickness?: number;
}) {
  const {
    width,
    height,
    grid,
    color,
    opacity = 0.16,
    thickness = 0.5,
  } = props;

  // grid=1 is useful for snapping, but drawing a 1px grid would create hundreds/thousands of Konva nodes.
  // To keep the editor responsive, auto-coarsen the *rendered* grid while preserving snapping semantics elsewhere.
  const maxLinesPerAxis = 200;
  const baseGrid = Math.max(1, Math.floor(Number(grid) || 1));
  let renderGrid = baseGrid;
  while (
    renderGrid > 0 &&
    (Math.floor(Math.max(0, width) / renderGrid) > maxLinesPerAxis || Math.floor(Math.max(0, height) / renderGrid) > maxLinesPerAxis)
  ) {
    renderGrid *= 2;
  }

  const cols = Math.floor(Math.max(0, width) / Math.max(1, renderGrid));
  const rows = Math.floor(Math.max(0, height) / Math.max(1, renderGrid));

  // Avoid drawing on the edges so we don't cover outer borders / frames.
  const vCount = Math.max(0, cols - 1);
  const hCount = Math.max(0, rows - 1);

  return (
    <>
      {Array.from({ length: vCount }).map((_, idx) => {
        const i = idx + 1;
        return (
          <Rect
            key={`v-${i}`}
            x={i * renderGrid}
            y={0}
            width={thickness}
            height={height}
            fill={color}
            opacity={opacity}
            listening={false}
          />
        );
      })}
      {Array.from({ length: hCount }).map((_, idx) => {
        const i = idx + 1;
        return (
          <Rect
            key={`h-${i}`}
            x={0}
            y={i * renderGrid}
            width={width}
            height={thickness}
            fill={color}
            opacity={opacity}
            listening={false}
          />
        );
      })}
    </>
  );
}
