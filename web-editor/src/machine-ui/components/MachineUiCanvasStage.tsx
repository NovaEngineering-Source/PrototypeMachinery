import {Box} from '@mantine/core';
import {useElementSize} from '@mantine/hooks';
import {useEffect, useMemo, useRef} from 'react';
import {Image as KonvaImage, Layer, Rect, Stage} from 'react-konva';
import {pmTextureUrl} from '../../editor/assets/pmAssets';
import {useCachedImage} from '../../editor/components/canvas/useCachedImage';
import {GridLayer} from '../../shared/konva/GridLayer';
import {OuterBorderRect} from '../../shared/konva/OuterBorderRect';
import {useMachineUiStore} from '../store/machineUiStore';

const FIT_PADDING = 16;
const SCALE_MIN = 0.25;
const SCALE_MAX = 6;

export function MachineUiCanvasStage() {
  const doc = useMachineUiStore((s) => s.doc);
  const view = useMachineUiStore((s) => s.view);
  const setView = useMachineUiStore((s) => s.setView);

  const stageRef = useRef<import('konva/lib/Stage').Stage | null>(null);
  const didInitView = useRef(false);

  const { ref: contentRef, width: viewportW, height: viewportH } = useElementSize();

  const { width: canvasW, height: canvasH } = doc.canvas;
  const grid = Math.max(2, Math.floor(doc.options?.gridSize ?? 8));

  const active = doc.options?.activeBackground ?? 'A';
  const texPath = active === 'A' ? doc.options?.backgroundA?.texturePath : doc.options?.backgroundB?.texturePath;
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

  useEffect(() => {
    if (didInitView.current) return;
    if (viewportW <= 0 || viewportH <= 0) return;
    resetViewToCenter();
    didInitView.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewportW, viewportH]);

  useEffect(() => {
    const id = window.requestAnimationFrame(() => applyPixelRendering());
    return () => window.cancelAnimationFrame(id);
  }, [viewportW, viewportH, view.scale]);

  const bg = useMemo(() => ({ fill: '#1f232b', grid: '#2b313c' }), []);

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
          style={{ background: bg.fill, cursor: 'default' }}
          onDblClick={() => fitViewToCanvas()}
        >
          <Layer>
            <Rect name="background" x={0} y={0} width={canvasW} height={canvasH} fill={bg.fill} opacity={1} />

            {bgImg ? (
              <KonvaImage x={0} y={0} width={canvasW} height={canvasH} image={bgImg} listening={false} />
            ) : null}

            <GridLayer width={canvasW} height={canvasH} grid={grid} color={bg.grid} opacity={0.14} thickness={0.5} />
            <OuterBorderRect width={canvasW} height={canvasH} stroke={'#3b424f'} strokeWidth={1} />

            {/* TODO: widgets layer */}
          </Layer>
        </Stage>
      </Box>
    </Box>
  );
}
