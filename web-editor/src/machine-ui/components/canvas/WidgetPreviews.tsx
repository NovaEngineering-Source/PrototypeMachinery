import {Group as KonvaGroup, Image as KonvaImage, Rect, Text as KonvaText} from 'react-konva';
import {pmTextureUrl} from '../../../editor/assets/pmAssets';
import {useCachedImage} from '../../../editor/components/canvas/useCachedImage';
import {OuterBorderRect} from '../../../shared/konva/OuterBorderRect';
import {SeparatedNineSliceImage} from '../../../shared/konva/SeparatedNineSliceImage';
import {MachineUiImageWidget, MachineUiProgressBarWidget, MachineUiSlotGridWidget, MachineUiTextFieldWidget, MachineUiWidget,} from '../../model/ir';
import {
    resolveGuiStatesButtonPreview,
    resolveGuiStatesSliderPreview,
    resolveGuiStatesTextFieldPreview,
    resolveGuiStatesTogglePreview,
} from '../../model/guiStatesPreview';
import {usePreviewFont} from '../../fonts/usePreviewFont';

export function SlotGridPreview({w, showPlaceholders}: {w: MachineUiSlotGridWidget; showPlaceholders: boolean}) {
  const slotSize = Math.max(1, Math.floor(w.slotSize ?? 18));
  const gap = Math.max(0, Math.floor(w.gap ?? 0));
  const texPath = String(w.slotTexturePath ?? '').trim();
  const img = useCachedImage(texPath ? pmTextureUrl(texPath) : undefined);

  const slots: JSX.Element[] = [];
  for (let r = 0; r < w.rows; r++) {
    for (let c = 0; c < w.cols; c++) {
      const sx = c * (slotSize + gap);
      const sy = r * (slotSize + gap);
      if (img) {
        slots.push(
          <KonvaImage
            key={`${w.id}_${r}_${c}`}
            x={sx}
            y={sy}
            width={slotSize}
            height={slotSize}
            image={img}
            listening={false}
          />,
        );
      } else if (showPlaceholders) {
        slots.push(
          <Rect
            key={`${w.id}_${r}_${c}`}
            x={sx}
            y={sy}
            width={slotSize}
            height={slotSize}
            fill={'rgba(0,0,0,0)'}
            stroke={'rgba(255,255,255,0.18)'}
            strokeWidth={1}
            strokeScaleEnabled={false}
            listening={false}
          />,
        );
      }
    }
  }

  return (
    <KonvaGroup x={w.x} y={w.y} listening={false}>
      {slots}
    </KonvaGroup>
  );
}

export function ProgressPreview({w}: {w: MachineUiProgressBarWidget}) {
  const p = Math.max(0, Math.min(1, w.progress ?? 0));
  const dir = w.direction ?? 'right';
  const bgColor = w.bgColor ?? 'rgba(0,0,0,0.25)';
  const fill = w.fillColor ?? '#51cf66';

  const ww = w.w;
  const hh = w.h;

  const baseImg = useCachedImage(w.baseTexturePath ? pmTextureUrl(w.baseTexturePath) : undefined);
  const runImg = useCachedImage(w.runTexturePath ? pmTextureUrl(w.runTexturePath) : undefined);

  // Compute fill rect (local coordinates)
  let fx = 0;
  let fy = 0;
  let fw = ww;
  let fh = hh;
  if (dir === 'right') fw = Math.round(ww * p);
  if (dir === 'left') {
    fw = Math.round(ww * p);
    fx = ww - fw;
  }
  if (dir === 'down') fh = Math.round(hh * p);
  if (dir === 'up') {
    fh = Math.round(hh * p);
    fy = hh - fh;
  }

  const canUseTextures = Boolean(baseImg || runImg);

  return (
    <KonvaGroup x={w.x} y={w.y} listening={false}>
      {/* Background */}
      {baseImg ? (
        <KonvaImage x={0} y={0} width={ww} height={hh} image={baseImg} listening={false} />
      ) : (
        <Rect x={0} y={0} width={ww} height={hh} fill={bgColor} listening={false} />
      )}

      {/* Fill / run layer */}
      {runImg ? (
        <KonvaGroup clipX={fx} clipY={fy} clipWidth={fw} clipHeight={fh} listening={false}>
          <KonvaImage x={0} y={0} width={ww} height={hh} image={runImg} listening={false} />
        </KonvaGroup>
      ) : canUseTextures ? null : (
        <Rect x={fx} y={fy} width={fw} height={fh} fill={fill} listening={false} />
      )}

      <OuterBorderRect width={ww} height={hh} stroke={'rgba(255,255,255,0.25)'} strokeWidth={1} />
    </KonvaGroup>
  );
}

export function ImagePreview({w, showPlaceholders}: {w: MachineUiImageWidget; showPlaceholders: boolean}) {
  const texPath = String((w as any).texturePath ?? '').trim();
  const img = useCachedImage(texPath ? pmTextureUrl(texPath) : undefined);
  if (img) {
    return <KonvaImage x={w.x} y={w.y} width={w.w} height={w.h} image={img} listening={false} />;
  }
  if (!showPlaceholders) return null;
  return (
    <Rect
      x={w.x}
      y={w.y}
      width={w.w}
      height={w.h}
      fill={'rgba(0,0,0,0)'}
      stroke={'rgba(255,255,255,0.22)'}
      strokeWidth={1}
      strokeScaleEnabled={false}
      listening={false}
    />
  );
}

export function ButtonPreview({w}: {w: any}) {
  const { fontFamily: previewFontFamily, fontScale: previewFontScale } = usePreviewFont();
  const skin = String(w.skin ?? '').trim();
  const text = String(w.text ?? '').trim();

  const d = resolveGuiStatesButtonPreview(skin);
  const url = d ? pmTextureUrl(d.texturePath) : undefined;
  const img = useCachedImage(url);

  const bg = (() => {
    if (!d || !img) return null;
    if (d.kind === 'full') {
      return <KonvaImage x={w.x} y={w.y} width={w.w} height={w.h} image={img} listening={false} />;
    }

    const src = d.sub
      ? {x: d.sub.x, y: d.sub.y, width: d.sub.w, height: d.sub.h}
      : {x: 0, y: 0, width: d.imageW, height: d.imageH};

    return (
      <SeparatedNineSliceImage
        image={img}
        x={w.x}
        y={w.y}
        width={w.w}
        height={w.h}
        src={src}
        borderLeft={d.borderLeft}
        borderTop={d.borderTop}
        borderRight={d.borderRight}
        borderBottom={d.borderBottom}
        separator={typeof (d as any).separatorPx === 'number' ? (d as any).separatorPx : 1}
        listening={false}
      />
    );
  })();

  if (bg) {
    return (
      <KonvaGroup listening={false}>
        {bg}
        {text ? (
          <KonvaText
            x={w.x}
            y={w.y}
            width={w.w}
            height={w.h}
            text={text}
            fontFamily={previewFontFamily}
            fontSize={Math.max(1, Math.floor(10 * (previewFontScale || 1)))}
            fill={'rgba(0,0,0,0.72)'}
            align={'center'}
            verticalAlign={'middle'}
            listening={false}
          />
        ) : null}
      </KonvaGroup>
    );
  }

  return <WidgetPlaceholder w={w} label="Button" />;
}

export function TogglePreview({w}: {w: any}) {
  const { fontFamily: previewFontFamily, fontScale: previewFontScale } = usePreviewFont();
  const skin = String(w.skin ?? '').trim();
  const stateLabel = String(w.textOff ?? '').trim();
  const d = resolveGuiStatesTogglePreview(skin);

  const texOff = String(w.textureOff ?? '').trim();
  const texOn = String(w.textureOn ?? '').trim();
  const fallbackPath = texOff || texOn;

  const url = d ? pmTextureUrl(d.texturePath) : fallbackPath ? pmTextureUrl(fallbackPath) : undefined;
  const img = useCachedImage(url);

  if (img) {
    return (
      <KonvaGroup listening={false}>
        <KonvaImage x={w.x} y={w.y} width={w.w} height={w.h} image={img} listening={false} />
        {stateLabel ? (
          <KonvaText
            x={w.x}
            y={w.y}
            width={w.w}
            height={w.h}
            text={stateLabel}
            fontFamily={previewFontFamily}
            fontSize={Math.max(1, Math.floor(10 * (previewFontScale || 1)))}
            fill={'rgba(0,0,0,0.75)'}
            align={'center'}
            verticalAlign={'middle'}
            listening={false}
          />
        ) : null}
      </KonvaGroup>
    );
  }

  return <WidgetPlaceholder w={w} label="Toggle" />;
}

export function SliderPreview({w}: {w: any}) {
  const skin = String(w.skin ?? '').trim();
  const p = resolveGuiStatesSliderPreview(skin);

  const baseUrl = p ? pmTextureUrl(p.base.texturePath) : undefined;
  const baseImg = useCachedImage(baseUrl);

  const handleUrl = p ? pmTextureUrl(p.handleTexturePath) : undefined;
  const handleImg = useCachedImage(handleUrl);

  if (!p || !baseImg) return <WidgetPlaceholder w={w} label="Slider" />;

  const base = (() => {
    if (p.base.kind === 'full') {
      return <KonvaImage x={w.x} y={w.y} width={w.w} height={w.h} image={baseImg} listening={false} />;
    }
    const src = p.base.sub
      ? {x: p.base.sub.x, y: p.base.sub.y, width: p.base.sub.w, height: p.base.sub.h}
      : {x: 0, y: 0, width: p.base.imageW, height: p.base.imageH};

    return (
      <SeparatedNineSliceImage
        image={baseImg}
        x={w.x}
        y={w.y}
        width={w.w}
        height={w.h}
        src={src}
        borderLeft={p.base.borderLeft}
        borderTop={p.base.borderTop}
        borderRight={p.base.borderRight}
        borderBottom={p.base.borderBottom}
        separator={1}
        listening={false}
      />
    );
  })();

  // Put handle at 50% for preview.
  const hx = Math.round(w.x + (w.w - p.handleW) / 2);
  const hy = Math.round(w.y + (w.h - p.handleH) / 2);

  return (
    <KonvaGroup listening={false}>
      {base}
      {handleImg ? <KonvaImage x={hx} y={hy} width={p.handleW} height={p.handleH} image={handleImg} listening={false} /> : null}
    </KonvaGroup>
  );
}

export function TextFieldPreview({w}: {w: MachineUiTextFieldWidget}) {
  const { fontFamily: previewFontFamily, fontScale: previewFontScale } = usePreviewFont();
  const skin = String((w as any).skin ?? '').trim();
  const label = String((w as any).valueKey ?? '').trim() || 'text';

  const d = resolveGuiStatesTextFieldPreview(skin);
  const url = d ? pmTextureUrl(d.texturePath) : undefined;
  const img = useCachedImage(url);

  const bg = (() => {
    if (!d || !img) return null;
    if (d.kind === 'full') {
      return <KonvaImage x={w.x} y={w.y} width={w.w} height={w.h} image={img} listening={false} />;
    }

    const src = d.sub
      ? {x: d.sub.x, y: d.sub.y, width: d.sub.w, height: d.sub.h}
      : {x: 0, y: 0, width: d.imageW, height: d.imageH};

    return (
      <SeparatedNineSliceImage
        image={img}
        x={w.x}
        y={w.y}
        width={w.w}
        height={w.h}
        src={src}
        borderLeft={d.borderLeft}
        borderTop={d.borderTop}
        borderRight={d.borderRight}
        borderBottom={d.borderBottom}
        separator={typeof (d as any).separatorPx === 'number' ? (d as any).separatorPx : 1}
        listening={false}
      />
    );
  })();

  if (!bg) return <WidgetPlaceholder w={w} label="TextField" />;

  return (
    <KonvaGroup listening={false}>
      {bg}
      <KonvaText
        x={w.x}
        y={w.y}
        width={w.w}
        height={w.h}
        padding={3}
        text={label}
        fontFamily={previewFontFamily}
        fontSize={Math.max(1, Math.floor(10 * (previewFontScale || 1)))}
        fill={'rgba(0,0,0,0.78)'}
        align={'left'}
        verticalAlign={'middle'}
        listening={false}
      />
    </KonvaGroup>
  );
}

export function WidgetPlaceholder({w, label}: {w: MachineUiWidget; label: string}) {
  return (
    <KonvaGroup listening={false}>
      <Rect
        x={w.x}
        y={w.y}
        width={w.w}
        height={w.h}
        fill={'rgba(0,0,0,0.12)'}
        stroke={'rgba(255,255,255,0.22)'}
        strokeWidth={1}
        strokeScaleEnabled={false}
      />
      <KonvaText
        x={w.x}
        y={w.y}
        width={w.w}
        height={w.h}
        text={label}
        fontSize={10}
        fill={'rgba(255,255,255,0.75)'}
        align={'center'}
        verticalAlign={'middle'}
        listening={false}
      />
    </KonvaGroup>
  );
}
