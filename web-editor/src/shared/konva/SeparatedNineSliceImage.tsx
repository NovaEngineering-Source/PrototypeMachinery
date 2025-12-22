import {Group as KonvaGroup, Image as KonvaImage} from 'react-konva';

export type SeparatedNineSliceRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type SeparatedNineSliceImageProps = {
  image?: HTMLImageElement | null;

  /** Destination rect (world coords). */
  x: number;
  y: number;
  width: number;
  height: number;

  /** Source rect inside the image (pixels). Defaults to full image. */
  src?: Partial<SeparatedNineSliceRect>;

  /**
   * Borders INCLUDING the 1px separators, matching PrototypeMachinery gui_states.md / runtime code.
   * Example: borderLeft = leftSegment + 1px separator.
   */
  borderLeft: number;
  borderTop: number;
  borderRight: number;
  borderBottom: number;

  /** Separator thickness between 3x3 pieces. gui_states uses 1px. */
  separator?: number;

  listening?: boolean;
};

function clampInt(n: unknown, fallback: number): number {
  const v = typeof n === 'number' ? n : Number(n);
  if (!Number.isFinite(v)) return fallback;
  return Math.trunc(v);
}

function clamp0(n: number): number {
  return Math.max(0, n);
}

export function SeparatedNineSliceImage(props: SeparatedNineSliceImageProps) {
  const {
    image,
    x,
    y,
    width,
    height,
    src,
    borderLeft,
    borderTop,
    borderRight,
    borderBottom,
    separator,
    listening,
  } = props;

  if (!image) return null;

  const sep = Math.max(0, clampInt(separator ?? 1, 1));

  const srcX = clampInt(src?.x ?? 0, 0);
  const srcY = clampInt(src?.y ?? 0, 0);
  const srcW = clampInt(src?.width ?? image.width, image.width);
  const srcH = clampInt(src?.height ?? image.height, image.height);

  // Runtime convention: borders include the 1px separator.
  // Some gui_states assets are effectively 3-slice (only X or only Y stretch):
  // - Horizontal slider bases: borderTop/bottom = 0 (no Y separators)
  // - Vertical slider bases: borderLeft/right = 0 (no X separators)
  const hasX = clampInt(borderLeft, 0) > 0 && clampInt(borderRight, 0) > 0;
  const hasY = clampInt(borderTop, 0) > 0 && clampInt(borderBottom, 0) > 0;
  const sepX = hasX ? sep : 0;
  const sepY = hasY ? sep : 0;

  const left = clamp0(clampInt(borderLeft, 0) - sepX);
  const top = clamp0(clampInt(borderTop, 0) - sepY);
  const right = clamp0(clampInt(borderRight, 0) - sepX);
  const bottom = clamp0(clampInt(borderBottom, 0) - sepY);

  const midSrcW = clamp0(srcW - left - right - sepX * 2);
  const midSrcH = clamp0(srcH - top - bottom - sepY * 2);

  // Destination (avoid negative center).
  const leftDstW = Math.min(left, Math.max(0, width));
  const topDstH = Math.min(top, Math.max(0, height));
  const rightDstW = Math.min(right, Math.max(0, width - leftDstW));
  const bottomDstH = Math.min(bottom, Math.max(0, height - topDstH));
  const midDstW = Math.max(0, width - leftDstW - rightDstW);
  const midDstH = Math.max(0, height - topDstH - bottomDstH);

  // Source anchors
  const sx0 = srcX;
  const sx1 = srcX + left + sepX;
  const sx2 = srcX + srcW - right;
  const sy0 = srcY;
  const sy1 = srcY + top + sepY;
  const sy2 = srcY + srcH - bottom;

  // Dest anchors
  const dx0 = x;
  const dx1 = x + leftDstW;
  const dx2 = x + width - rightDstW;
  const dy0 = y;
  const dy1 = y + topDstH;
  const dy2 = y + height - bottomDstH;

  const pieces: Array<{
    dx: number;
    dy: number;
    dw: number;
    dh: number;
    sx: number;
    sy: number;
    sw: number;
    sh: number;
  }> = [
    // top row
    { dx: dx0, dy: dy0, dw: leftDstW, dh: topDstH, sx: sx0, sy: sy0, sw: left, sh: top },
    { dx: dx1, dy: dy0, dw: midDstW, dh: topDstH, sx: sx1, sy: sy0, sw: midSrcW, sh: top },
    { dx: dx2, dy: dy0, dw: rightDstW, dh: topDstH, sx: sx2, sy: sy0, sw: right, sh: top },

    // mid row
    { dx: dx0, dy: dy1, dw: leftDstW, dh: midDstH, sx: sx0, sy: sy1, sw: left, sh: midSrcH },
    { dx: dx1, dy: dy1, dw: midDstW, dh: midDstH, sx: sx1, sy: sy1, sw: midSrcW, sh: midSrcH },
    { dx: dx2, dy: dy1, dw: rightDstW, dh: midDstH, sx: sx2, sy: sy1, sw: right, sh: midSrcH },

    // bottom row
    { dx: dx0, dy: dy2, dw: leftDstW, dh: bottomDstH, sx: sx0, sy: sy2, sw: left, sh: bottom },
    { dx: dx1, dy: dy2, dw: midDstW, dh: bottomDstH, sx: sx1, sy: sy2, sw: midSrcW, sh: bottom },
    { dx: dx2, dy: dy2, dw: rightDstW, dh: bottomDstH, sx: sx2, sy: sy2, sw: right, sh: bottom },
  ];

  return (
    <KonvaGroup listening={Boolean(listening)}>
      {pieces
        .filter((p) => p.dw > 0 && p.dh > 0 && p.sw > 0 && p.sh > 0)
        .map((p, idx) => (
          <KonvaImage
            key={idx}
            x={p.dx}
            y={p.dy}
            width={p.dw}
            height={p.dh}
            image={image}
            crop={{ x: p.sx, y: p.sy, width: p.sw, height: p.sh }}
            listening={false}
          />
        ))}
    </KonvaGroup>
  );
}
