import {ActionIcon, Box, Group, Text as MText, Tooltip} from '@mantine/core';
import {useElementSize} from '@mantine/hooks';
import {IconArrowsMaximize, IconRefresh, IconZoomIn, IconZoomOut} from '@tabler/icons-react';
import {useEffect, useMemo, useRef, useState} from 'react';
import {Group as KonvaGroup, Image as KonvaImage, Layer, Rect, Stage, Text as KonvaText, Transformer} from 'react-konva';
import {pmTextureUrl} from '../../editor/assets/pmAssets';
import {useCachedImage} from '../../editor/components/canvas/useCachedImage';
import {GridLayer} from '../../shared/konva/GridLayer';
import {OuterBorderRect} from '../../shared/konva/OuterBorderRect';
import {MachineUiImageWidget, MachineUiProgressBarWidget, MachineUiSlotGridWidget, MachineUiWidget,} from '../model/ir';
import {
    resolveGuiStatesButtonSizing,
    resolveGuiStatesSliderSizing,
    resolveGuiStatesTextFieldSizing,
    resolveGuiStatesToggleSizing,
} from '../model/guiStatesPreview';
import {useMachineUiStore} from '../store/machineUiStore';
import {usePreviewFont} from '../fonts/usePreviewFont';
import {
    ButtonPreview,
    ImagePreview,
    ProgressPreview,
    SliderPreview,
    SlotGridPreview,
    TextFieldPreview,
    TogglePreview,
    WidgetPlaceholder,
} from './canvas/WidgetPreviews';
import {asContainerChildrenIds, isTextWidget} from './canvas/widgetUtils';
import {createCanvasMath} from './canvas/canvasMath';

const FIT_PADDING = 16;
const SCALE_MIN = 0.25;
const SCALE_MAX = 6;
const SCALE_BY = 1.08;

export function MachineUiCanvasStage() {
  const doc = useMachineUiStore((s) => s.doc);
  const { fontFamily: previewFontFamily, fontScale: previewFontScale } = usePreviewFont();
  const view = useMachineUiStore((s) => s.view);
  const setView = useMachineUiStore((s) => s.setView);
  const setActiveBackground = useMachineUiStore((s) => s.setActiveBackground);
  const setActiveTabId = useMachineUiStore((s) => s.setActiveTabId);

  const mode = useMachineUiStore((s) => s.selection.mode);
  const selectedGuideId = useMachineUiStore((s) => s.selection.selectedGuideId);
  const setSelectedGuideId = useMachineUiStore((s) => s.setSelectedGuideId);
  const selectedWidgetId = useMachineUiStore((s) => s.selection.selectedWidgetId);
  const selectedWidgetIds = useMachineUiStore((s) => s.selection.selectedWidgetIds);
  const setSelectedWidgetId = useMachineUiStore((s) => s.setSelectedWidgetId);
  const setSelectedWidgetIds = useMachineUiStore((s) => s.setSelectedWidgetIds);
  const toggleSelectedWidgetId = useMachineUiStore((s) => s.toggleSelectedWidgetId);
  const clearWidgetSelection = useMachineUiStore((s) => s.clearWidgetSelection);
  const updateGuideLive = useMachineUiStore((s) => s.updateGuideLive);
  const updateGuide = useMachineUiStore((s) => s.updateGuide);
  const updateWidgetLive = useMachineUiStore((s) => s.updateWidgetLive);
  const updateWidget = useMachineUiStore((s) => s.updateWidget);
  const updateWidgetsLive = useMachineUiStore((s) => s.updateWidgetsLive);
  const updateWidgets = useMachineUiStore((s) => s.updateWidgets);

  const stageRef = useRef<import('konva/lib/Stage').Stage | null>(null);
  const didInitView = useRef(false);

  const transformerRef = useRef<any>(null);
  const selectedWidgetNodeRef = useRef<any>(null);

  const [isPanning, setIsPanning] = useState(false);
  const [isStageDragging, setIsStageDragging] = useState(false);
  const [isMousePanning, setIsMousePanning] = useState(false);

  const [selectionRect, setSelectionRect] = useState<null | { x: number; y: number; w: number; h: number }>(null);
  const boxSelectActiveRef = useRef(false);
  const boxSelectStartAbsRef = useRef<{ x: number; y: number } | null>(null);
  const boxSelectAdditiveRef = useRef(false);

  const multiDragRef = useRef<null | {
    anchorId: string;
    baseById: Record<string, { x: number; y: number; w: number; h: number }>;
  }>(null);

  // 手动平移（Space + 拖拽）：使用 ref 避免高频事件里 setState 同步问题。
  const panActiveRef = useRef(false);
  const lastPointerRef = useRef<{ x: number; y: number } | null>(null);

  const { ref: contentRef, width: viewportW, height: viewportH } = useElementSize();

  const { width: canvasW, height: canvasH } = doc.canvas;
  const grid = Math.max(1, Math.floor(doc.options?.gridSize ?? 8));

  const activeTabId = doc.options?.activeTabId ?? doc.options?.activeBackground ?? 'A';
  const tabs = doc.options?.tabs;

  const texPath = (() => {
    if (tabs && tabs.length > 0) {
      const t = tabs.find((x) => x.id === activeTabId) ?? tabs[0];
      return t?.texturePath;
    }
    const legacy = doc.options?.activeBackground ?? 'A';
    return legacy === 'A' ? doc.options?.backgroundA?.texturePath : doc.options?.backgroundB?.texturePath;
  })();

  const url = texPath ? pmTextureUrl(texPath) : undefined;
  const bgImg = useCachedImage(url);

  const clamp = (n: number, min: number, max: number) => Math.max(min, Math.min(max, n));

  const applyPixelRendering = () => {
    const stage = stageRef.current;
    if (!stage) return;
    const content = stage.getContent();
    if (!content) return;
    (content.style as any).imageRendering = 'pixelated';

    const canvases = content.querySelectorAll('canvas');
    canvases.forEach((c) => {
      const canvas = c as HTMLCanvasElement;
      (canvas.style as any).imageRendering = 'pixelated';
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.imageSmoothingEnabled = false;
        (ctx as any).mozImageSmoothingEnabled = false;
        (ctx as any).webkitImageSmoothingEnabled = false;
        (ctx as any).msImageSmoothingEnabled = false;
      }
    });
  };

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
    const availW = Math.max(1, w - FIT_PADDING * 2);
    const availH = Math.max(1, h - FIT_PADDING * 2);
    const scale = clamp(Math.min(availW / canvasW, availH / canvasH), SCALE_MIN, SCALE_MAX);
    const offsetX = Math.floor((w - canvasW * scale) / 2);
    const offsetY = Math.floor((h - canvasH * scale) / 2);
    setView({ scale, offsetX, offsetY });
  };

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

  useEffect(() => {
    if (didInitView.current) return;
    if (viewportW <= 0 || viewportH <= 0) return;
    resetViewToCenter();
    didInitView.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewportW, viewportH]);

  // Space 按住进入平移模式。
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
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
      // 避免切换窗口导致 keyup 丢失，从而“卡住在平移模式”。
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

  useEffect(() => {
    const id = window.requestAnimationFrame(() => applyPixelRendering());
    return () => window.cancelAnimationFrame(id);
  }, [viewportW, viewportH, view.scale]);

  const bg = useMemo(() => ({ fill: '#1f232b', grid: '#2b313c' }), []);
  const showGuides = Boolean(doc.options?.showGuides);
  const guides = doc.guides;
  const widgets = doc.widgets;

  const widgetsForTab = useMemo(() => {
    const base = (widgets ?? []) as MachineUiWidget[];
    return base.filter((w) => {
      const tid = String((w as any).tabId ?? '').trim();
      return !tid || tid === activeTabId;
    });
  }, [widgets, activeTabId]);

  const canEditGuides = mode === 'guides';
  const canEditWidgets = mode === 'widgets';
  const isMultiWidgetSelection = (selectedWidgetIds ?? []).length > 1;
  const isSingleWidgetSelection = (selectedWidgetIds ?? []).length === 1 && Boolean(selectedWidgetId);

  const canvasMath = useMemo(() => createCanvasMath({ canvasW, canvasH, grid, view }), [canvasW, canvasH, grid, view]);
  const {
    snapWithEdges,
    snapSize,
    absToWorld,
    boundGuideWorldPos,
    boundGuideAbsPos,
    boundWidgetWorldPos,
    boundWidgetAbsPos,
  } = canvasMath;

  // Render large guides first (behind), small guides last (on top), so "content" won't eat clicks.
  const guidesForRender = useMemo(() => {
    const base = guides ?? [];
    const sorted = [...base].sort((a, b) => b.w * b.h - a.w * a.h);
    if (!selectedGuideId) return sorted;
    const idx = sorted.findIndex((g) => g.id === selectedGuideId);
    if (idx < 0) return sorted;
    const [sel] = sorted.splice(idx, 1);
    sorted.push(sel);
    return sorted;
  }, [guides, selectedGuideId]);

  const applyResizePatch = (w: MachineUiWidget, next: { x: number; y: number; w: number; h: number }) => {
    const x = snapWithEdges(next.x, grid, 0, Math.max(0, canvasW - 1));
    const y = snapWithEdges(next.y, grid, 0, Math.max(0, canvasH - 1));
    let ww = snapSize(next.w, grid);
    let hh = snapSize(next.h, grid);

    // Respect gui_states skin sizing policies for interactive resizing.
    // - If skin is not stretchable, keep default size.
    // - If skin is stretchable on only one axis, lock the other axis to default.
    const skin = String((w as any).skin ?? '').trim();
    if (skin) {
      if (w.type === 'button') {
        const sizing = resolveGuiStatesButtonSizing(skin);
        if (sizing && (!sizing.stretchX || !sizing.stretchY)) {
          ww = sizing.defaultW;
          hh = sizing.defaultH;
        }
      }
      if (w.type === 'toggle') {
        const sizing = resolveGuiStatesToggleSizing(skin);
        if (sizing && (!sizing.stretchX || !sizing.stretchY)) {
          ww = sizing.defaultW;
          hh = sizing.defaultH;
        }
      }
      if (w.type === 'slider') {
        const sizing = resolveGuiStatesSliderSizing(skin);
        if (sizing) {
          if (!sizing.stretchX) ww = sizing.defaultW;
          if (!sizing.stretchY) hh = sizing.defaultH;
        }
      }

      if (w.type === 'textField') {
        const sizing = resolveGuiStatesTextFieldSizing(skin);
        if (sizing) {
          if (!sizing.stretchX) ww = sizing.defaultW;
          if (!sizing.stretchY) hh = sizing.defaultH;
        }
      }
    }

    // clamp size to canvas (after snapping)
    ww = Math.max(1, Math.min(ww, canvasW));
    hh = Math.max(1, Math.min(hh, canvasH));

    // clamp position to keep fully inside canvas
    const cx = snapWithEdges(x, grid, 0, Math.max(0, canvasW - ww));
    const cy = snapWithEdges(y, grid, 0, Math.max(0, canvasH - hh));

    if (w.type === 'slotGrid') {
      const sw = w as MachineUiSlotGridWidget;
      const slotSize = Math.max(1, Math.floor(sw.slotSize ?? 18));
      const gap = Math.max(0, Math.floor(sw.gap ?? 0));
      const cell = slotSize + gap;
      const cols = Math.max(1, Math.floor((ww + gap) / cell));
      const rows = Math.max(1, Math.floor((hh + gap) / cell));
      const quantW = cols * slotSize + Math.max(0, cols - 1) * gap;
      const quantH = rows * slotSize + Math.max(0, rows - 1) * gap;
      const qx = snapWithEdges(cx, grid, 0, Math.max(0, canvasW - quantW));
      const qy = snapWithEdges(cy, grid, 0, Math.max(0, canvasH - quantH));
      return { x: Math.round(qx), y: Math.round(qy), w: quantW, h: quantH, cols, rows };
    }

    return { x: Math.round(cx), y: Math.round(cy), w: ww, h: hh };
  };

  useEffect(() => {
    const tr = transformerRef.current;
    if (!tr) return;
    const node = canEditWidgets && isSingleWidgetSelection ? selectedWidgetNodeRef.current : null;
    if (node) {
      tr.nodes([node]);
    } else {
      tr.nodes([]);
    }
    tr.getLayer()?.batchDraw();
  }, [canEditWidgets, isSingleWidgetSelection, selectedWidgetId]);

  const widgetsForRender = useMemo(() => {
    const base = widgetsForTab as MachineUiWidget[];
    const sel = selectedWidgetIds ?? [];
    if (sel.length === 0) return base;
    const selSet = new Set(sel);
    const out = base.filter((w) => !selSet.has(w.id));
    // keep selected order stable, but always draw primary last
    const selectedInOrder = base.filter((w) => selSet.has(w.id));
    if (selectedWidgetId && selSet.has(selectedWidgetId)) {
      const idx = selectedInOrder.findIndex((w) => w.id === selectedWidgetId);
      if (idx >= 0) {
        const [primary] = selectedInOrder.splice(idx, 1);
        selectedInOrder.push(primary);
      }
    }
    return [...out, ...selectedInOrder];
  }, [widgetsForTab, selectedWidgetIds, selectedWidgetId]);

  const rectIntersects = (a: { x: number; y: number; w: number; h: number }, b: { x: number; y: number; w: number; h: number }) => {
    const ax2 = a.x + a.w;
    const ay2 = a.y + a.h;
    const bx2 = b.x + b.w;
    const by2 = b.y + b.h;
    return a.x <= bx2 && ax2 >= b.x && a.y <= by2 && ay2 >= b.y;
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
      <Box ref={contentRef} style={{ width: '100%', height: '100%', overflow: 'hidden' }}>
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
          draggable={false}
          style={{
            background: bg.fill,
            cursor: isPanning || isMousePanning ? (isStageDragging ? 'grabbing' : 'grab') : 'default',
          }}
          onWheel={(e) => {
            e.evt.preventDefault();
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
            // Middle mouse button drag: pan (common editor behavior)
            if (e.evt.button === 1) {
              e.evt.preventDefault();
              panActiveRef.current = true;
              setIsMousePanning(true);
              setIsStageDragging(true);
              const stage = e.target.getStage();
              const p = stage?.getPointerPosition();
              if (p) lastPointerRef.current = { x: p.x, y: p.y };
              return;
            }

            // Click empty area: clear selection
            if (!isPanning && (e.target === e.target.getStage() || e.target.hasName('background'))) {
              // widgets mode: drag-to-select box; click clears
              if (mode === 'widgets' && e.evt.button === 0) {
                const additive = Boolean(e.evt.shiftKey || e.evt.ctrlKey || e.evt.metaKey);
                const stage = e.target.getStage();
                const p = stage?.getPointerPosition();
                if (p) {
                  boxSelectActiveRef.current = true;
                  boxSelectStartAbsRef.current = { x: p.x, y: p.y };
                  boxSelectAdditiveRef.current = additive;
                  const ws = absToWorld(p);
                  setSelectionRect({ x: ws.x, y: ws.y, w: 0, h: 0 });
                  // don't clear immediately; treat as click if no drag
                  return;
                }
              }

              if (mode === 'guides') setSelectedGuideId(undefined);
              if (mode === 'widgets') clearWidgetSelection();
            }

            if (!isPanning) return;
            e.evt.preventDefault();
            panActiveRef.current = true;
            setIsStageDragging(true);
            const stage = e.target.getStage();
            const p = stage?.getPointerPosition();
            if (p) lastPointerRef.current = { x: p.x, y: p.y };
          }}
          onMouseMove={(e) => {
            if (panActiveRef.current) {
              const stage = e.target.getStage();
              const p = stage?.getPointerPosition();
              const last = lastPointerRef.current;
              if (!p || !last) return;
              const dx = p.x - last.x;
              const dy = p.y - last.y;
              lastPointerRef.current = { x: p.x, y: p.y };
              setView((prev) => ({ ...prev, offsetX: prev.offsetX + dx, offsetY: prev.offsetY + dy }));
              return;
            }

            if (!boxSelectActiveRef.current) return;
            const stage = e.target.getStage();
            const p = stage?.getPointerPosition();
            const startAbs = boxSelectStartAbsRef.current;
            if (!p || !startAbs) return;

            const a = absToWorld(startAbs);
            const b = absToWorld(p);
            const x1 = clamp(Math.min(a.x, b.x), 0, canvasW);
            const y1 = clamp(Math.min(a.y, b.y), 0, canvasH);
            const x2 = clamp(Math.max(a.x, b.x), 0, canvasW);
            const y2 = clamp(Math.max(a.y, b.y), 0, canvasH);
            setSelectionRect({ x: x1, y: y1, w: x2 - x1, h: y2 - y1 });
          }}
          onMouseUp={() => {
            if (panActiveRef.current) {
              panActiveRef.current = false;
              lastPointerRef.current = null;
              setIsStageDragging(false);
              setIsMousePanning(false);
              return;
            }

            if (!boxSelectActiveRef.current) return;
            boxSelectActiveRef.current = false;
            boxSelectStartAbsRef.current = null;

            const rect = selectionRect;
            const additive = boxSelectAdditiveRef.current;
            setSelectionRect(null);
            if (!rect) {
              if (!additive) clearWidgetSelection();
              return;
            }

            const minDrag = 3;
            if (rect.w < minDrag && rect.h < minDrag) {
              // treat as a click on background
              if (!additive) clearWidgetSelection();
              return;
            }

            const hitIds = (widgetsForTab as MachineUiWidget[])
              .filter((w) => rectIntersects(rect, { x: (w as any).x ?? 0, y: (w as any).y ?? 0, w: (w as any).w ?? 1, h: (w as any).h ?? 1 }))
              .map((w) => w.id);

            if (additive) {
              const cur = selectedWidgetIds ?? [];
              const merged = Array.from(new Set([...cur, ...hitIds]));
              setSelectedWidgetIds(merged, hitIds[hitIds.length - 1] ?? selectedWidgetId);
            } else {
              setSelectedWidgetIds(hitIds, hitIds[hitIds.length - 1]);
            }
          }}
          onMouseLeave={() => {
            if (panActiveRef.current) {
              panActiveRef.current = false;
              lastPointerRef.current = null;
              setIsStageDragging(false);
              setIsMousePanning(false);
            }
            if (boxSelectActiveRef.current) {
              boxSelectActiveRef.current = false;
              boxSelectStartAbsRef.current = null;
              setSelectionRect(null);
            }
          }}
        >
          <Layer>
            <Rect name="background" x={0} y={0} width={canvasW} height={canvasH} fill={bg.fill} opacity={1} />

            {bgImg ? (
              <KonvaImage x={0} y={0} width={canvasW} height={canvasH} image={bgImg} listening={false} />
            ) : null}

            <GridLayer width={canvasW} height={canvasH} grid={grid} color={bg.grid} opacity={0.14} thickness={0.5} />
            <OuterBorderRect width={canvasW} height={canvasH} stroke={'#3b424f'} strokeWidth={1} />

            {/* widgets */}
            {widgetsForRender.map((w) => {
              const selected = (selectedWidgetIds ?? []).includes(w.id);
              const isPrimary = w.id === selectedWidgetId;
              const locked = Boolean((w as any).locked);
              const dims = { w: (w as any).w ?? 1, h: (w as any).h ?? 1 };

              const commonHitbox = (
                <Rect
                  ref={(node) => {
                    if (isPrimary && isSingleWidgetSelection) selectedWidgetNodeRef.current = node;
                  }}
                  x={(w as any).x}
                  y={(w as any).y}
                  width={dims.w}
                  height={dims.h}
                  fill={'rgba(0,0,0,0.001)'}
                  stroke={isPrimary ? '#ffd43b' : selected ? 'rgba(255,212,59,0.55)' : 'rgba(0,0,0,0)'}
                  strokeWidth={selected ? 1.5 : 1}
                  strokeScaleEnabled={false}
                  listening={canEditWidgets}
                  draggable={canEditWidgets && !locked && !isPanning && !isMousePanning}
                  dragBoundFunc={(pos) => boundWidgetAbsPos(dims, pos)}
                  onTransform={(e) => {
                    if (!canEditWidgets) return;
                    if (locked) return;
                    const node = e.target as any;
                    const scaleX = node.scaleX?.() ?? 1;
                    const scaleY = node.scaleY?.() ?? 1;
                    const rawW = Math.max(1, Math.round((node.width?.() ?? dims.w) * scaleX));
                    const rawH = Math.max(1, Math.round((node.height?.() ?? dims.h) * scaleY));
                    const rawX = Math.round(node.x?.() ?? (w as any).x);
                    const rawY = Math.round(node.y?.() ?? (w as any).y);
                    const patch = applyResizePatch(w, { x: rawX, y: rawY, w: rawW, h: rawH });
                    updateWidgetLive(w.id, patch as any);
                  }}
                  onTransformEnd={(e) => {
                    if (!canEditWidgets) return;
                    if (locked) return;
                    const node = e.target as any;
                    const scaleX = node.scaleX?.() ?? 1;
                    const scaleY = node.scaleY?.() ?? 1;
                    const rawW = Math.max(1, Math.round((node.width?.() ?? dims.w) * scaleX));
                    const rawH = Math.max(1, Math.round((node.height?.() ?? dims.h) * scaleY));
                    const rawX = Math.round(node.x?.() ?? (w as any).x);
                    const rawY = Math.round(node.y?.() ?? (w as any).y);

                    // reset scale to avoid compounding transforms (react-konva will re-apply size via props)
                    if (node.scaleX) node.scaleX(1);
                    if (node.scaleY) node.scaleY(1);

                    const patch = applyResizePatch(w, { x: rawX, y: rawY, w: rawW, h: rawH });
                    updateWidget(w.id, patch as any);
                  }}
                  onDragStart={(e) => {
                    e.cancelBubble = true;
                    // If dragging a non-selected widget, select it first.
                    if (!(selectedWidgetIds ?? []).includes(w.id)) setSelectedWidgetId(w.id);
                    // Container (Panel/ScrollContainer): always drag with its childrenIds as a group (even if not multi-selected).
                    const kids = asContainerChildrenIds(w);
                    if (kids.length > 0) {
                      const baseById: Record<string, { x: number; y: number; w: number; h: number }> = {};
                      for (const ww of widgetsForTab as MachineUiWidget[]) {
                        if (ww.id === w.id || kids.includes(ww.id)) {
                          baseById[ww.id] = {
                            x: (ww as any).x,
                            y: (ww as any).y,
                            w: (ww as any).w,
                            h: (ww as any).h,
                          };
                        }
                      }
                      if (Object.keys(baseById).length > 1) {
                        multiDragRef.current = {
                          anchorId: w.id,
                          baseById,
                        };
                        return;
                      }
                    }

                    const sel = selectedWidgetIds ?? [];
                    if (sel.length > 1 && sel.includes(w.id)) {
                      const baseById: Record<string, { x: number; y: number; w: number; h: number }> = {};
                      for (const ww of widgetsForTab as MachineUiWidget[]) {
                        if (sel.includes(ww.id)) {
                          baseById[ww.id] = {
                            x: Math.trunc((ww as any).x ?? 0),
                            y: Math.trunc((ww as any).y ?? 0),
                            w: Math.max(1, Math.trunc((ww as any).w ?? 1)),
                            h: Math.max(1, Math.trunc((ww as any).h ?? 1)),
                          };
                        }
                      }
                      multiDragRef.current = { anchorId: w.id, baseById };
                    } else {
                      multiDragRef.current = null;
                    }
                  }}
                  onDragMove={(e) => {
                    const node = e.target;
                    const abs = node.getAbsolutePosition();

                    const multi = multiDragRef.current;
                    if (multi && multi.anchorId === w.id) {
                      const baseAnchor = multi.baseById[w.id];
                      if (!baseAnchor) return;
                      const anchorWorld = boundWidgetWorldPos(dims, absToWorld(abs));
                      const dx = anchorWorld.x - baseAnchor.x;
                      const dy = anchorWorld.y - baseAnchor.y;

                      const patchesById: Record<string, any> = {};
                      for (const [id, base] of Object.entries(multi.baseById)) {
                        const targetDims = { w: base.w, h: base.h };
                        const nextWorld = boundWidgetWorldPos(targetDims, { x: base.x + dx, y: base.y + dy });
                        patchesById[id] = { x: nextWorld.x, y: nextWorld.y };
                      }
                      updateWidgetsLive(patchesById);
                      return;
                    }

                    const world = boundWidgetWorldPos(dims, absToWorld(abs));
                    updateWidgetLive(w.id, { x: world.x, y: world.y } as any);
                  }}
                  onDragEnd={(e) => {
                    const node = e.target;
                    const abs = node.getAbsolutePosition();

                    const multi = multiDragRef.current;
                    if (multi && multi.anchorId === w.id) {
                      const baseAnchor = multi.baseById[w.id];
                      multiDragRef.current = null;
                      if (!baseAnchor) return;
                      const anchorWorld = boundWidgetWorldPos(dims, absToWorld(abs));
                      const dx = anchorWorld.x - baseAnchor.x;
                      const dy = anchorWorld.y - baseAnchor.y;

                      const patchesById: Record<string, any> = {};
                      for (const [id, base] of Object.entries(multi.baseById)) {
                        const targetDims = { w: base.w, h: base.h };
                        const nextWorld = boundWidgetWorldPos(targetDims, { x: base.x + dx, y: base.y + dy });
                        patchesById[id] = { x: nextWorld.x, y: nextWorld.y };
                      }
                      updateWidgets(patchesById);
                      return;
                    }

                    const world = boundWidgetWorldPos(dims, absToWorld(abs));
                    updateWidget(w.id, { x: world.x, y: world.y } as any);
                  }}
                />
              );

              return (
                <KonvaGroup
                  key={w.id}
                  listening={canEditWidgets}
                  onMouseDown={(e) => {
                    if (!canEditWidgets) return;
                    if (isPanning || isMousePanning) return;
                    e.cancelBubble = true;
                    const additive = Boolean(e.evt.shiftKey || e.evt.ctrlKey || e.evt.metaKey);
                    if (additive) toggleSelectedWidgetId(w.id);
                    else setSelectedWidgetId(w.id);
                  }}
                >
                  {commonHitbox}

                  {isTextWidget(w) ? (
                    <KonvaText
                      x={w.x}
                      y={w.y}
                      width={w.w}
                      height={w.h}
                      text={w.text}
                      fontFamily={previewFontFamily}
                      fontSize={Math.max(1, Math.floor((w.fontSize ?? 12) * (previewFontScale || 1)))}
                      fill={w.color ?? '#e9ecef'}
                      align={w.align ?? 'left'}
                      verticalAlign="middle"
                      listening={false}
                    />
                  ) : null}

                  {w.type === 'slotGrid' ? (
                    <SlotGridPreview w={w as MachineUiSlotGridWidget} showPlaceholders={selected} />
                  ) : null}

                  {w.type === 'progress' ? <ProgressPreview w={w as MachineUiProgressBarWidget} /> : null}

                  {w.type === 'image' ? <ImagePreview w={w as MachineUiImageWidget} showPlaceholders={selected} /> : null}

                  {w.type === 'button' ? <ButtonPreview w={w as any} /> : null}
                  {w.type === 'toggle' ? <TogglePreview w={w as any} /> : null}
                  {w.type === 'slider' ? <SliderPreview w={w as any} /> : null}
                  {w.type === 'textField' ? <TextFieldPreview w={w as any} /> : null}
                  {w.type === 'panel' ? (
                    <KonvaGroup listening={false}>
                      <Rect
                        x={w.x}
                        y={w.y}
                        width={w.w}
                        height={w.h}
                        fill={'rgba(0,0,0,0.06)'}
                        stroke={'rgba(255,255,255,0.28)'}
                        strokeWidth={1}
                        dash={[6, 4]}
                        strokeScaleEnabled={false}
                      />
                      <KonvaText
                        x={w.x}
                        y={w.y}
                        width={w.w}
                        height={Math.min(14, w.h)}
                        text={'Panel'}
                        fontSize={10}
                        fill={'rgba(255,255,255,0.7)'}
                        align={'left'}
                        verticalAlign={'top'}
                        padding={2}
                        listening={false}
                      />
                    </KonvaGroup>
                  ) : null}
                  {w.type === 'scroll_container' ? (
                    <KonvaGroup listening={false}>
                      <Rect
                        x={w.x}
                        y={w.y}
                        width={w.w}
                        height={w.h}
                        fill={'rgba(0,0,0,0.04)'}
                        stroke={'rgba(99, 102, 241, 0.55)'}
                        strokeWidth={1}
                        dash={[6, 4]}
                        strokeScaleEnabled={false}
                      />
                      <KonvaText
                        x={w.x}
                        y={w.y}
                        width={w.w}
                        height={Math.min(14, w.h)}
                        text={'Scroll'}
                        fontSize={10}
                        fill={'rgba(99, 102, 241, 0.9)'}
                        align={'left'}
                        verticalAlign={'top'}
                        padding={2}
                        listening={false}
                      />
                    </KonvaGroup>
                  ) : null}
                  {w.type === 'playerInventory' ? <WidgetPlaceholder w={w} label="PlayerInv" /> : null}
                </KonvaGroup>
              );
            })}

            {/* box selection rectangle */}
            {canEditWidgets && selectionRect && selectionRect.w > 0 && selectionRect.h > 0 ? (
              <Rect
                x={selectionRect.x}
                y={selectionRect.y}
                width={selectionRect.w}
                height={selectionRect.h}
                fill={'rgba(255,212,59,0.08)'}
                stroke={'rgba(255,212,59,0.75)'}
                strokeWidth={1}
                dash={[4, 4]}
                strokeScaleEnabled={false}
                listening={false}
              />
            ) : null}

            {/* selection transformer (resize handles) */}
            <Transformer
              ref={transformerRef}
              rotateEnabled={false}
              enabledAnchors={['top-left', 'top-right', 'bottom-left', 'bottom-right']}
              anchorSize={7}
              borderStroke={'rgba(255,212,59,0.85)'}
              anchorStroke={'rgba(255,212,59,0.95)'}
              anchorFill={'rgba(0,0,0,0.35)'}
              keepRatio={false}
              ignoreStroke={true}
              listening={canEditWidgets && !isMultiWidgetSelection && !isPanning && !isMousePanning}
            />

            {/* tab bar hitboxes */}
            <KonvaGroup listening={!isPanning && !isMousePanning}>
              {(() => {
                const tabW = 21;
                const tabH = 22;
                const list = (doc.options?.tabs && doc.options.tabs.length > 0
                  ? doc.options.tabs
                  : [
                      { id: 'A', label: 'A' },
                      { id: 'B', label: 'B' },
                    ]) as Array<{ id: string; label?: string }>;

                return list.map((t, i) => {
                  const isActive = activeTabId === t.id;
                  const label = (t.label ?? t.id).slice(0, 2);
                  return (
                    <KonvaGroup
                      key={t.id}
                      x={0}
                      y={i * tabH}
                      onMouseDown={(e) => {
                        if (isPanning || isMousePanning) return;
                        e.cancelBubble = true;
                        // keep legacy A/B helper for quick switching
                        if (t.id === 'A' || t.id === 'B') setActiveBackground(t.id as any);
                        else setActiveTabId(t.id);
                      }}
                    >
                      <Rect
                        x={0}
                        y={0}
                        width={tabW}
                        height={tabH}
                        fill={isActive ? 'rgba(255,212,59,0.16)' : 'rgba(255,255,255,0.06)'}
                        stroke={isActive ? 'rgba(255,212,59,0.9)' : 'rgba(255,255,255,0.16)'}
                        strokeWidth={1}
                        strokeScaleEnabled={false}
                      />
                      <KonvaText
                        x={0}
                        y={0}
                        width={tabW}
                        height={tabH}
                        text={label}
                        align="center"
                        verticalAlign="middle"
                        fontSize={11}
                        fill={isActive ? 'rgba(255,212,59,0.95)' : 'rgba(233,236,239,0.85)'}
                        listening={false}
                      />
                    </KonvaGroup>
                  );
                });
              })()}
            </KonvaGroup>

            {showGuides
              ? guidesForRender.map((g) => {
                  const color = g.color ?? '#91a7ff';
                  const selected = g.id === selectedGuideId;
                  const locked = Boolean(g.locked);
                  return (
                    <KonvaGroup
                      key={g.id}
                      listening={canEditGuides}
                      onMouseDown={(e) => {
                        if (!canEditGuides) return;
                        if (isPanning || isMousePanning) return;
                        e.cancelBubble = true;
                        setSelectedGuideId(g.id);
                      }}
                    >
                      <Rect
                        x={g.x}
                        y={g.y}
                        width={g.w}
                        height={g.h}
                        fill={selected ? 'rgba(255,255,255,0.06)' : 'rgba(255,255,255,0.03)'}
                        stroke={color}
                        strokeWidth={selected ? 2 : 1}
                        dash={[4, 4]}
                        strokeScaleEnabled={false}
                        listening={canEditGuides}
                        draggable={canEditGuides && !locked && !isPanning && !isMousePanning}
                        dragBoundFunc={(pos) => boundGuideAbsPos(g, pos)}
                        onDragStart={(e) => {
                          e.cancelBubble = true;
                          setSelectedGuideId(g.id);
                        }}
                        onDragMove={(e) => {
                          const node = e.target;
                          const abs = node.getAbsolutePosition();
                          const world = boundGuideWorldPos(g, absToWorld(abs));
                          updateGuideLive(g.id, { x: world.x, y: world.y });
                        }}
                        onDragEnd={(e) => {
                          const node = e.target;
                          const abs = node.getAbsolutePosition();
                          const world = boundGuideWorldPos(g, absToWorld(abs));
                          updateGuide(g.id, { x: world.x, y: world.y });
                        }}
                      />
                      {g.label ? (
                        <KonvaText
                          x={g.x + 4}
                          y={g.y + 3}
                          text={g.label}
                          fontSize={10}
                          fill={color}
                          listening={false}
                        />
                      ) : null}
                    </KonvaGroup>
                  );
                })
              : null}

            {/* TODO: widgets layer */}
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
