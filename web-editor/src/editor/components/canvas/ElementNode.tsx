import {Fragment} from 'react';
import {Arc, Group as KonvaGroup, Image as KonvaImage, Rect, Text} from 'react-konva';
import {useCachedImage} from './useCachedImage';
import {
    clamp01,
    defaultEnergyFillRatio,
    inferEnergyLayerPaths,
    inferEnergyLedPath,
    inferProgressModuleLayerPaths,
    inferTankLayerPaths,
    inferTexturePathFromElement,
    normalizeExplicitTexturePath,
    normalizeJeiBackgroundTexturePath,
    normalizeProgressDirection,
    parseResourceLocation,
    shortVariantId,
    toPmTextureUrl,
} from './elementPreview';

export function ElementNode(props: {
  el: any;
  selected: boolean;
  isPanning: boolean;
  viewScale: number;
  grid: number;
  onSelect: () => void;
  onLiveMove: (x: number, y: number) => void;
  onCommitMove: (x: number, y: number) => void;
}) {
  const { el, selected, isPanning, viewScale, grid, onSelect, onLiveMove, onCommitMove } = props;

  const explicitTexturePath = normalizeExplicitTexturePath(el);
  const useExplicit = Boolean(explicitTexturePath);

  // --- Decorator previews (when no explicit override is set) ---
  const decoratorRl = el.type === 'decorator' ? parseResourceLocation(el.variantId) : undefined;
  const decoratorPath = decoratorRl?.path;

  const bgTexPath = !useExplicit && decoratorPath === 'decorator/background'
    ? normalizeJeiBackgroundTexturePath(el.data?.texture)
    : undefined;
  const bgImg = useCachedImage(bgTexPath ? toPmTextureUrl(bgTexPath) : undefined);

  const progressModuleLayers = !useExplicit && decoratorPath === 'decorator/progress_module'
    ? inferProgressModuleLayerPaths(el.data)
    : undefined;
  const progressModuleBaseImg = useCachedImage(progressModuleLayers ? toPmTextureUrl(progressModuleLayers.base) : undefined);
  const progressModuleRunImg = useCachedImage(progressModuleLayers ? toPmTextureUrl(progressModuleLayers.run) : undefined);

  // --- Multi-layer previews (when no explicit override is set) ---
  const energyLayers = !useExplicit ? inferEnergyLayerPaths(el) : undefined;
  const energyEmptyImg = useCachedImage(energyLayers ? toPmTextureUrl(energyLayers.empty) : undefined);
  const energyFullImg = useCachedImage(energyLayers ? toPmTextureUrl(energyLayers.full) : undefined);
  const energyLedImg = useCachedImage(energyLayers ? toPmTextureUrl(inferEnergyLedPath(el.role)) : undefined);

  const tankLayers = !useExplicit ? inferTankLayerPaths(el) : undefined;
  const tankBaseImg = useCachedImage(tankLayers ? toPmTextureUrl(tankLayers.base) : undefined);
  const tankTopImg = useCachedImage(tankLayers ? toPmTextureUrl(tankLayers.top) : undefined);

  // --- Single texture fallback ---
  const texturePath = useExplicit ? explicitTexturePath : inferTexturePathFromElement(el);
  const url = texturePath ? toPmTextureUrl(texturePath) : undefined;
  const img = useCachedImage(url);

  const hasEnergyPreview = Boolean(energyLayers && energyEmptyImg && energyFullImg);
  const hasTankPreview = Boolean(tankLayers && tankBaseImg);

  const hasDecoratorPreview = Boolean(
    (decoratorPath === 'decorator/background' && bgImg) ||
      (decoratorPath === 'decorator/progress_module' && progressModuleBaseImg)
  );

  const showLabel = selected || (!hasEnergyPreview && !hasTankPreview && !img && !hasDecoratorPreview);
  const labelFontSize = Math.max(6, Math.min(12, 10 / Math.max(0.001, viewScale)));
  const labelText = `${el.type}${el.variantId ? `\n${shortVariantId(el.variantId)}` : ''}`;

  const snap = (n: number) => Math.round(n / grid) * grid;

  // Border styles: thin, and rendered as an outer border that won't overlap the texture.
  const strokeW = selected ? 1.25 : 0.75;
  const strokeWWorld = strokeW / Math.max(0.001, viewScale);
  const strokeColor = selected ? '#91a7ff' : '#3b424f';

  return (
    <Fragment>
      {el.type === 'decorator' && !useExplicit ? (
        decoratorPath === 'decorator/background' && bgImg ? (
          <KonvaImage x={el.x} y={el.y} width={el.w} height={el.h} image={bgImg} listening={false} />
        ) : decoratorPath === 'decorator/progress_module' && progressModuleBaseImg ? (
          <KonvaGroup x={el.x} y={el.y} listening={false}>
            <KonvaImage x={0} y={0} width={el.w} height={el.h} image={progressModuleBaseImg} listening={false} />

            {progressModuleRunImg
              ? (() => {
                  const progress = clamp01(el.data?.previewProgress, 0.5);
                  const dir = normalizeProgressDirection(el.data?.direction);
                  if (progress <= 0) return null;
                  if (dir === 'RIGHT') {
                    return (
                      <KonvaGroup clipX={0} clipY={0} clipWidth={Math.max(1, el.w * progress)} clipHeight={el.h} listening={false}>
                        <KonvaImage x={0} y={0} width={el.w} height={el.h} image={progressModuleRunImg} listening={false} />
                      </KonvaGroup>
                    );
                  }
                  if (dir === 'LEFT') {
                    const w = Math.max(1, el.w * progress);
                    return (
                      <KonvaGroup clipX={el.w - w} clipY={0} clipWidth={w} clipHeight={el.h} listening={false}>
                        <KonvaImage x={0} y={0} width={el.w} height={el.h} image={progressModuleRunImg} listening={false} />
                      </KonvaGroup>
                    );
                  }
                  if (dir === 'DOWN') {
                    return (
                      <KonvaGroup clipX={0} clipY={0} clipWidth={el.w} clipHeight={Math.max(1, el.h * progress)} listening={false}>
                        <KonvaImage x={0} y={0} width={el.w} height={el.h} image={progressModuleRunImg} listening={false} />
                      </KonvaGroup>
                    );
                  }
                  if (dir === 'UP') {
                    const h = Math.max(1, el.h * progress);
                    return (
                      <KonvaGroup clipX={0} clipY={el.h - h} clipWidth={el.w} clipHeight={h} listening={false}>
                        <KonvaImage x={0} y={0} width={el.w} height={el.h} image={progressModuleRunImg} listening={false} />
                      </KonvaGroup>
                    );
                  }

                  // CIRCULAR_CW and other modes: just render full run layer.
                  return <KonvaImage x={0} y={0} width={el.w} height={el.h} image={progressModuleRunImg} listening={false} />;
                })()
              : null}
          </KonvaGroup>
        ) : decoratorPath === 'decorator/recipe_duration' ? (
          <Fragment>
            <Rect x={el.x} y={el.y} width={el.w} height={el.h} fill={'rgba(0,0,0,0.25)'} listening={false} />
            <Text
              x={el.x + 4}
              y={el.y + 4}
              width={Math.max(1, el.w - 8)}
              text={'recipe_duration'}
              fontSize={labelFontSize}
              lineHeight={1.2}
              fill={'#dee2e6'}
              listening={false}
            />
          </Fragment>
        ) : decoratorPath === 'decorator/progress' ? (
          (() => {
            const progress = clamp01(el.data?.previewProgress, 0.5);
            const dir = normalizeProgressDirection(el.data?.direction);
            const styleRaw = typeof el.data?.style === 'string' ? el.data.style.trim().toLowerCase() : 'arrow';
            const style = styleRaw === 'cycle' ? 'cycle' : 'arrow';

            // For cycle/circular, draw an arc. For arrow, use a simple filled bar.
            if (style === 'cycle' || dir === 'CIRCULAR_CW') {
              const cx = el.x + el.w / 2;
              const cy = el.y + el.h / 2;
              const r = Math.max(2, Math.min(el.w, el.h) / 2 - 2);
              return (
                <Fragment>
                  <Rect x={el.x} y={el.y} width={el.w} height={el.h} fill={'rgba(0,0,0,0.18)'} listening={false} />
                  <Arc
                    x={cx}
                    y={cy}
                    innerRadius={Math.max(1, r - 3)}
                    outerRadius={r}
                    angle={Math.max(0, Math.min(360, progress * 360))}
                    rotation={-90}
                    fill={'rgba(145,167,255,0.85)'}
                    listening={false}
                  />
                </Fragment>
              );
            }

            // Bar style
            const fillColor = 'rgba(145,167,255,0.85)';
            const baseColor = 'rgba(0,0,0,0.18)';
            const w = el.w;
            const h = el.h;
            const filledW = Math.max(0, Math.min(w, w * progress));
            const filledH = Math.max(0, Math.min(h, h * progress));

            return (
              <Fragment>
                <Rect x={el.x} y={el.y} width={el.w} height={el.h} fill={baseColor} listening={false} />
                {dir === 'RIGHT' ? (
                  <Rect x={el.x} y={el.y} width={filledW} height={h} fill={fillColor} listening={false} />
                ) : dir === 'LEFT' ? (
                  <Rect x={el.x + (w - filledW)} y={el.y} width={filledW} height={h} fill={fillColor} listening={false} />
                ) : dir === 'DOWN' ? (
                  <Rect x={el.x} y={el.y} width={w} height={filledH} fill={fillColor} listening={false} />
                ) : (
                  <Rect x={el.x} y={el.y + (h - filledH)} width={w} height={filledH} fill={fillColor} listening={false} />
                )}
              </Fragment>
            );
          })()
        ) : (
          <Fragment>
            <Rect x={el.x} y={el.y} width={el.w} height={el.h} fill={'rgba(0,0,0,0.2)'} listening={false} />
          </Fragment>
        )
      ) : hasEnergyPreview ? (
        <KonvaGroup x={el.x} y={el.y} listening={false}>
          {/* 1) Empty background */}
          <KonvaImage x={0} y={0} width={el.w} height={el.h} image={energyEmptyImg!} listening={false} />

          {/* 2) Full overlay, clipped by fill mask (spec: inset 3px on each side; fill from bottom to top) */}
          {(() => {
            const inset = 3;
            const fill = clamp01(el.data?.previewFill, defaultEnergyFillRatio(el.role));
            const insetW = Math.max(1, el.w - inset * 2);
            const insetH = Math.max(1, el.h - inset * 2);
            const filledH = Math.max(0, Math.min(insetH, Math.floor(insetH * fill)));
            if (filledH <= 0) return null;

            const clipX = inset;
            const clipY = inset + (insetH - filledH);
            return (
              <KonvaGroup clipX={clipX} clipY={clipY} clipWidth={insetW} clipHeight={filledH} listening={false}>
                <KonvaImage x={0} y={0} width={el.w} height={el.h} image={energyFullImg!} listening={false} />
              </KonvaGroup>
            );
          })()}

          {/* 3) IO led at top, centered (spec: 10x1) */}
          {energyLedImg
            ? (() => {
                const LED_W = 10;
                const LED_H = 1;
                const ledW = Math.min(LED_W, Math.max(1, Math.floor(el.w)));
                const ledH = LED_H;
                const yOff = typeof el.data?.energyLedYOffset === 'number' ? el.data.energyLedYOffset : 0;
                return (
                  <KonvaImage
                    x={(el.w - ledW) / 2}
                    y={yOff}
                    width={ledW}
                    height={ledH}
                    image={energyLedImg}
                    listening={false}
                  />
                );
              })()
            : null}
        </KonvaGroup>
      ) : hasTankPreview ? (
        <Fragment>
          {/* Tank: base + top overlay */}
          <KonvaImage x={el.x} y={el.y} width={el.w} height={el.h} image={tankBaseImg!} listening={false} />
          {tankTopImg ? <KonvaImage x={el.x} y={el.y} width={el.w} height={el.h} image={tankTopImg} listening={false} /> : null}
        </Fragment>
      ) : img ? (
        <KonvaImage x={el.x} y={el.y} width={el.w} height={el.h} image={img} listening={false} />
      ) : null}

      {/* Hit/drag rect: no stroke (so it never covers the texture) */}
      <Rect
        x={el.x}
        y={el.y}
        width={el.w}
        height={el.h}
        fill={'rgba(0,0,0,0)'}
        opacity={1}
        draggable={!isPanning}
        onClick={onSelect}
        onTap={onSelect}
        onDragMove={(evt) => {
          const x = snap(evt.target.x());
          const y = snap(evt.target.y());
          onLiveMove(x, y);
          evt.target.position({ x, y });
        }}
        onDragEnd={(evt) => {
          const x = snap(evt.target.x());
          const y = snap(evt.target.y());
          onCommitMove(x, y);
          evt.target.position({ x, y });
        }}
      />

      {/* Outer border: thin and outside the element bounds, so it doesn't overlap the texture */}
      <Rect
        x={el.x - strokeWWorld / 2}
        y={el.y - strokeWWorld / 2}
        width={el.w + strokeWWorld}
        height={el.h + strokeWWorld}
        stroke={strokeColor}
        strokeWidth={strokeW}
        strokeScaleEnabled={false}
        listening={false}
      />

      {showLabel ? (
        <Text
          x={el.x + 4}
          y={el.y + 4}
          width={Math.max(1, el.w - 8)}
          text={labelText}
          fontSize={labelFontSize}
          lineHeight={1.2}
          fill={selected ? '#dbe4ff' : '#ced4da'}
          listening={false}
        />
      ) : null}
    </Fragment>
  );
}
