import {ActionIcon, Box, Group, Text as MText, Tooltip} from '@mantine/core';
import {useElementSize} from '@mantine/hooks';
import {IconArrowsMaximize, IconFocus2, IconRefresh, IconZoomIn, IconZoomOut} from '@tabler/icons-react';
import {useEffect, useMemo, useRef, useState} from 'react';
import {Image as KonvaImage, Layer, Rect, Stage} from 'react-konva';
import {useEditorStore} from '../store/editorStore';
import {ElementNode} from './canvas/ElementNode';
import {GridLayer} from '../../shared/konva/GridLayer';
import {OuterBorderRect} from '../../shared/konva/OuterBorderRect';
import {useCachedImage} from './canvas/useCachedImage';
import {normalizeJeiBackgroundTexturePath, toPmTextureUrl} from './canvas/elementPreview';

const SCALE_MIN = 0.25;
const SCALE_MAX = 6;
const SCALE_BY = 1.08;
const FIT_PADDING = 16;

export function CanvasStage() {
  const doc = useEditorStore((s) => s.doc);
  const selectionId = useEditorStore((s) => s.selectionId);
  const select = useEditorStore((s) => s.select);
  const updateElement = useEditorStore((s) => s.updateElement);
  const updateElementLive = useEditorStore((s) => s.updateElementLive);

  const view = useEditorStore((s) => s.view);
  const setView = useEditorStore((s) => s.setView);

  const stageRef = useRef<import('konva/lib/Stage').Stage | null>(null);
  // 注意：不能对带 border 的容器做 size 测量再把该值回写为 Stage 的 width/height，
  // 否则会形成“测到的尺寸包含 border -> Stage 按该尺寸渲染 -> 内容撑大 -> 再测更大”的反馈循环。
  // 这里使用一个内部 content layer（无 border）作为测量目标。
  const { ref: contentRef, width: viewportW, height: viewportH } = useElementSize();

  const [isPanning, setIsPanning] = useState(false);
  const [isStageDragging, setIsStageDragging] = useState(false);
  const didInitView = useRef(false);

  const applyPixelRendering = () => {
    const stage = stageRef.current;
    if (!stage) return;
    const content = stage.getContent();
    if (!content) return;

    // CSS hint: affects how the canvas bitmap is scaled by the browser.
    // (Konva does its own transforms, but this still helps in some cases, e.g. HiDPI scaling.)
    (content.style as any).imageRendering = 'pixelated';

    const canvases = content.querySelectorAll('canvas');
    canvases.forEach((c) => {
      const canvas = c as HTMLCanvasElement;
      (canvas.style as any).imageRendering = 'pixelated';
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.imageSmoothingEnabled = false;
        // Some browsers use vendor-prefixed flags.
        (ctx as any).mozImageSmoothingEnabled = false;
        (ctx as any).webkitImageSmoothingEnabled = false;
        (ctx as any).msImageSmoothingEnabled = false;
      }
    });
  };

  // 手动平移（Space + 拖拽）：使用 ref 避免高频事件里 setState 同步问题。
  const panActiveRef = useRef(false);
  const lastPointerRef = useRef<{ x: number; y: number } | null>(null);

  const { width: canvasW, height: canvasH } = doc.canvas;
  const grid = Math.max(2, Math.floor(doc.options?.gridSize ?? 8));

  const bgTexPath = doc.options?.backgroundNineSlice
    ? normalizeJeiBackgroundTexturePath(doc.options.backgroundNineSlice.texture)
    : undefined;
  const bgImg = useCachedImage(bgTexPath ? toPmTextureUrl(bgTexPath) : undefined);

  const bg = useMemo(() => {
    // simple checkerboard background
    return { fill: '#1f232b', grid: '#2b313c' };
  }, []);

  const clamp = (n: number, min: number, max: number) => Math.max(min, Math.min(max, n));

  const resetViewToCenter = () => {
    const w = Math.max(1, viewportW);
    const h = Math.max(1, viewportH);
    const x = Math.floor((w - canvasW) / 2);
    const y = Math.floor((h - canvasH) / 2);
    setView({ scale: 1, offsetX: x, offsetY: y });
  };

  const fitViewToCanvas = () => {
    const w = Math.max(1, viewportW);
    const h = Math.max(1, viewportH);

    // 可见区域留一点边距，避免边框贴边不舒服。
    const availW = Math.max(1, w - FIT_PADDING * 2);
    const availH = Math.max(1, h - FIT_PADDING * 2);

    const scale = clamp(Math.min(availW / canvasW, availH / canvasH), SCALE_MIN, SCALE_MAX);
    const offsetX = Math.floor((w - canvasW * scale) / 2);
    const offsetY = Math.floor((h - canvasH * scale) / 2);
    setView({ scale, offsetX, offsetY });
  };

  const focusSelected = () => {
    if (!selectionId) return;
    const el = doc.elements.find((e) => e.id === selectionId);
    if (!el) return;
    const cx = el.x + el.w / 2;
    const cy = el.y + el.h / 2;
    const w = Math.max(1, viewportW);
    const h = Math.max(1, viewportH);
    setView((prev) => ({
      ...prev,
      offsetX: w / 2 - cx * prev.scale,
      offsetY: h / 2 - cy * prev.scale,
    }));
  };

  // 初次渲染：把画布居中（只做一次，避免抢用户操作）。
  useEffect(() => {
    if (didInitView.current) return;
    if (viewportW <= 0 || viewportH <= 0) return;
    resetViewToCenter();
    didInitView.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewportW, viewportH]);

  // Space 按住进入平移模式（非常基础但够用）。
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
        // 防止页面滚动
        e.preventDefault();
        setIsPanning(true);
      }
    };
    const onKeyUp = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
        e.preventDefault();
        setIsPanning(false);
      }
    };
    window.addEventListener('keydown', onKeyDown, { passive: false });
    window.addEventListener('keyup', onKeyUp, { passive: false });
    const onBlur = () => {
      // 避免用户切换窗口/焦点导致 keyup 丢失，从而“卡住在平移模式”。
      setIsPanning(false);
      setIsStageDragging(false);
      panActiveRef.current = false;
      lastPointerRef.current = null;
    };
    window.addEventListener('blur', onBlur);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      window.removeEventListener('blur', onBlur);
    };
  }, []);

  // Re-apply pixel rendering hints when the stage canvas is recreated or resized.
  useEffect(() => {
    // Konva may recreate internal canvases on resize; defer to next frame.
    const id = window.requestAnimationFrame(() => applyPixelRendering());
    return () => window.cancelAnimationFrame(id);
  }, [viewportW, viewportH, view.scale]);

  const applyWheelZoom = (deltaY: number) => {
    const stage = stageRef.current;
    if (!stage) return;
    const pointer = stage.getPointerPosition();
    if (!pointer) return;

    setView((prev) => {
      const oldScale = prev.scale;
      const nextScale = clamp(deltaY > 0 ? oldScale / SCALE_BY : oldScale * SCALE_BY, SCALE_MIN, SCALE_MAX);

      // zoom to pointer: keep the world point under cursor stable
      const mousePointTo = {
        x: (pointer.x - prev.offsetX) / oldScale,
        y: (pointer.y - prev.offsetY) / oldScale,
      };
      const nextOffsetX = pointer.x - mousePointTo.x * nextScale;
      const nextOffsetY = pointer.y - mousePointTo.y * nextScale;
      return { scale: nextScale, offsetX: nextOffsetX, offsetY: nextOffsetY };
    });
  };

  const zoomBy = (factor: number) => {
    const stage = stageRef.current;
    const pointer = stage?.getPointerPosition();
    setView((prev) => {
      const oldScale = prev.scale;
      const nextScale = clamp(oldScale * factor, SCALE_MIN, SCALE_MAX);
      // fall back to viewport center
      const cx = pointer?.x ?? viewportW / 2;
      const cy = pointer?.y ?? viewportH / 2;
      const world = { x: (cx - prev.offsetX) / oldScale, y: (cy - prev.offsetY) / oldScale };
      return { scale: nextScale, offsetX: cx - world.x * nextScale, offsetY: cy - world.y * nextScale };
    });
  };

  return (
    <Box
      style={{
        border: '1px solid var(--mantine-color-dark-4)',
        borderRadius: 8,
        overflow: 'hidden',
        flex: 1,
        position: 'relative',
      }}
    >
      <Box
        ref={contentRef}
        style={{
          width: '100%',
          height: '100%',
          overflow: 'hidden',
        }}
      >
      <Stage
        ref={(node) => {
          stageRef.current = node;
        }}
        width={Math.max(1, viewportW)}
        height={Math.max(1, viewportH)}
        x={view.offsetX}
        y={view.offsetY}
        scaleX={view.scale}
        scaleY={view.scale}
        // 不使用 Konva Stage 的 draggable：它可能与元素拖拽竞争，造成“拖元素时视图也被拖动/抖动”。
        draggable={false}
        style={{
          background: bg.fill,
          cursor: isPanning ? (isStageDragging ? 'grabbing' : 'grab') : 'default',
        }}
        onWheel={(e) => {
          e.evt.preventDefault();
          // when panning mode is active, we still allow wheel zoom (simple and predictable)
          applyWheelZoom(e.evt.deltaY);
        }}
        onDblClick={(e) => {
          // 双击空白区域：快速 Fit-to-View
          if (isPanning) return;
          if (e.target === e.target.getStage() || e.target.hasName('background')) {
            fitViewToCanvas();
          }
        }}
        onMouseDown={(e) => {
          if (isPanning) {
            // Space+拖拽：手动平移（不影响元素拖拽，因为 panning 时元素 draggable 被禁用）
            e.evt.preventDefault();
            panActiveRef.current = true;
            setIsStageDragging(true);
            const stage = e.target.getStage();
            const p = stage?.getPointerPosition();
            if (p) lastPointerRef.current = { x: p.x, y: p.y };
            return;
          }
          // Click empty area: clear selection
          if (e.target === e.target.getStage() || e.target.hasName('background')) {
            select(undefined);
          }
        }}
        onMouseMove={(e) => {
          if (!panActiveRef.current) return;
          const stage = e.target.getStage();
          const p = stage?.getPointerPosition();
          const last = lastPointerRef.current;
          if (!p || !last) return;
          const dx = p.x - last.x;
          const dy = p.y - last.y;
          lastPointerRef.current = { x: p.x, y: p.y };
          setView((prev) => ({ ...prev, offsetX: prev.offsetX + dx, offsetY: prev.offsetY + dy }));
        }}
        onMouseUp={() => {
          if (!panActiveRef.current) return;
          panActiveRef.current = false;
          lastPointerRef.current = null;
          setIsStageDragging(false);
        }}
        onMouseLeave={() => {
          if (!panActiveRef.current) return;
          panActiveRef.current = false;
          lastPointerRef.current = null;
          setIsStageDragging(false);
        }}
      >
        <Layer>
          {/* canvas background */}
          <Rect
            name="background"
            x={0}
            y={0}
            width={canvasW}
            height={canvasH}
            fill={bg.fill}
            opacity={1}
          />

          {/* JEI background override (preview) */}
          {bgImg ? (
            <KonvaImage
              x={0}
              y={0}
              width={canvasW}
              height={canvasH}
              image={bgImg}
              opacity={0.9}
              listening={false}
            />
          ) : null}

          {/* grid */}
          <GridLayer width={canvasW} height={canvasH} grid={grid} color={bg.grid} opacity={0.16} thickness={0.5} />

          {/* canvas border (outer stroke, so it doesn't overlap the background's own frame) */}
          <OuterBorderRect width={canvasW} height={canvasH} stroke={'#3b424f'} strokeWidth={1} />

          {/* elements */}
          {doc.elements.map((el) => {
            const selectedEl = el.id === selectionId;
            return (
              <ElementNode
                key={el.id}
                el={el}
                selected={selectedEl}
                isPanning={isPanning}
                viewScale={view.scale}
                grid={grid}
                onSelect={() => select(el.id)}
                onLiveMove={(x, y) => updateElementLive(el.id, { x, y })}
                onCommitMove={(x, y) => updateElement(el.id, { x, y })}
              />
            );
          })}
        </Layer>
      </Stage>
      </Box>

      {/* overlay controls */}
      <Group
        gap={6}
        style={{
          position: 'absolute',
          top: 8,
          right: 8,
          padding: 6,
          borderRadius: 8,
          background: 'rgba(0,0,0,0.25)',
          backdropFilter: 'blur(6px)',
        }}
      >
        <Tooltip label="放大" withinPortal>
          <ActionIcon variant="subtle" color="gray" onClick={() => zoomBy(SCALE_BY)}>
            <IconZoomIn size={18} />
          </ActionIcon>
        </Tooltip>
        <Tooltip label="缩小" withinPortal>
          <ActionIcon variant="subtle" color="gray" onClick={() => zoomBy(1 / SCALE_BY)}>
            <IconZoomOut size={18} />
          </ActionIcon>
        </Tooltip>
        <Tooltip label="定位到选中元素" withinPortal>
          <ActionIcon
            variant="subtle"
            color="gray"
            disabled={!selectionId}
            onClick={() => focusSelected()}
          >
            <IconFocus2 size={18} />
          </ActionIcon>
        </Tooltip>
        <Tooltip label="适配画布到视口" withinPortal>
          <ActionIcon variant="subtle" color="gray" onClick={() => fitViewToCanvas()}>
            <IconArrowsMaximize size={18} />
          </ActionIcon>
        </Tooltip>
        <Tooltip label="重置视图（居中 + 100%）" withinPortal>
          <ActionIcon variant="subtle" color="gray" onClick={() => resetViewToCenter()}>
            <IconRefresh size={18} />
          </ActionIcon>
        </Tooltip>
        <MText size="xs" c="dimmed" style={{ userSelect: 'none' }}>
          {Math.round(view.scale * 100)}%
        </MText>
      </Group>

      {/* hint */}
      <MText
        size="xs"
        c="dimmed"
        style={{
          position: 'absolute',
          left: 10,
          bottom: 8,
          userSelect: 'none',
          background: 'rgba(0,0,0,0.18)',
          padding: '4px 6px',
          borderRadius: 6,
        }}
      >
        滚轮缩放；按住 Space 拖拽平移
      </MText>
    </Box>
  );
}
