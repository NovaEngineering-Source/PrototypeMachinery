import {Rect} from 'react-konva';

/**
 * A border drawn *outside* the bounds so it won't cover
 * background textures that have their own frame/border pixels.
 */
export function OuterBorderRect(props: {
  width: number;
  height: number;
  stroke: string;
  strokeWidth?: number;
}) {
  const { width, height, stroke, strokeWidth = 1 } = props;

  const half = strokeWidth / 2;
  return (
    <Rect
      x={-half}
      y={-half}
      width={width + strokeWidth}
      height={height + strokeWidth}
      stroke={stroke}
      strokeWidth={strokeWidth}
      strokeScaleEnabled={false}
      listening={false}
    />
  );
}
