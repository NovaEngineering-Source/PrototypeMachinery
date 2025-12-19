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

  const cols = Math.floor(Math.max(0, width) / Math.max(1, grid));
  const rows = Math.floor(Math.max(0, height) / Math.max(1, grid));

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
            x={i * grid}
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
            y={i * grid}
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
