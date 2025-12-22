import {useMemo} from 'react';
import {pmTextureUrl} from '../../../editor/assets/pmAssets';
import {useCachedImage} from '../../../editor/components/canvas/useCachedImage';

export function computeSlotGridSize(cols: number, rows: number, slotSize: number, gap: number) {
  const c = Math.max(1, Math.floor(cols || 1));
  const r = Math.max(1, Math.floor(rows || 1));
  const s = Math.max(1, Math.floor(slotSize || 1));
  const g = Math.max(0, Math.floor(gap || 0));
  const w = c * s + Math.max(0, c - 1) * g;
  const h = r * s + Math.max(0, r - 1) * g;
  return { cols: c, rows: r, slotSize: s, gap: g, w, h };
}

export function usePmTextureSize(texturePath?: string) {
  const cleaned = typeof texturePath === 'string' ? texturePath.trim() : '';
  const url = cleaned ? pmTextureUrl(cleaned) : undefined;
  const img = useCachedImage(url);
  return useMemo(() => {
    if (!img) return undefined;
    const w = Number((img as any).naturalWidth ?? (img as any).width ?? 0);
    const h = Number((img as any).naturalHeight ?? (img as any).height ?? 0);
    if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) return undefined;
    return { w: Math.floor(w), h: Math.floor(h) };
  }, [img]);
}
