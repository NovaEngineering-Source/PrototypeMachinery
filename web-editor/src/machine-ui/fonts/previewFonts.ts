export type PreviewFontPreset = 'minecraft' | 'system' | 'custom';

export type PreviewFontState = {
  preset: PreviewFontPreset;
  /** Multiply all widget font sizes by this factor (editor preview only). */
  scale: number;
  /** Optional custom font data URL (ttf/otf). Stored in localStorage; may fail if too large. */
  customDataUrl?: string;
  /** Optional custom font URL (ttf/otf/woff/woff2). Needs proper CORS headers if cross-origin. */
  customUrl?: string;
  /** Friendly name (e.g. file name) for display. */
  customName?: string;
};

export const PREVIEW_FONT_MOJANGLES_FAMILY = 'PM_Mojangles';
export const PREVIEW_FONT_UNIFONT_FAMILY = 'PM_Unifont';
export const PREVIEW_FONT_CUSTOM_FAMILY = 'PM_Custom';

export function clampPreviewFontScale(v: unknown, fallback = 1): number {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(0.25, Math.min(3, n));
}

export function normalizePreviewFontState(raw: unknown): PreviewFontState {
  const obj = (raw ?? {}) as any;
  const preset = ((): PreviewFontPreset => {
    const p = String(obj.preset ?? '').trim();
    if (p === 'minecraft' || p === 'system' || p === 'custom') return p;
    return 'minecraft';
  })();

  const scale = clampPreviewFontScale(obj.scale, 1);

  const customDataUrl = typeof obj.customDataUrl === 'string' && obj.customDataUrl.startsWith('data:') ? obj.customDataUrl : undefined;
  const customUrl = (() => {
    const u = typeof obj.customUrl === 'string' ? obj.customUrl.trim() : '';
    if (!u) return undefined;
    if (u.startsWith('http://') || u.startsWith('https://')) return u;
    if (u.startsWith('/') || u.startsWith('./')) return u;
    return undefined;
  })();
  const customName = typeof obj.customName === 'string' ? obj.customName : undefined;

  return { preset, scale, customDataUrl, customUrl, customName };
}

export function getPreviewFontFamily(state: PreviewFontState): string {
  if (state.preset === 'system') {
    return 'system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif';
  }

  if (state.preset === 'custom') {
    // Custom font first, then Unifont as a coverage fallback.
    return `${PREVIEW_FONT_CUSTOM_FAMILY}, ${PREVIEW_FONT_UNIFONT_FAMILY}, monospace`;
  }

  // Minecraft preset: Mojangles with Unifont fallback.
  return `${PREVIEW_FONT_MOJANGLES_FAMILY}, ${PREVIEW_FONT_UNIFONT_FAMILY}, monospace`;
}

async function tryLoadFontFace(family: string, source: string): Promise<boolean> {
  // FontFace may not exist in some environments (SSR/tests).
  const FontFaceCtor = (globalThis as any).FontFace as typeof FontFace | undefined;
  const fonts = typeof document !== 'undefined' ? ((document as any).fonts as FontFaceSet | undefined) : undefined;
  if (!FontFaceCtor || !fonts) return false;

  // Avoid repeated loads if already present.
  try {
    const existing = Array.from(fonts).some((ff) => ff.family === family);
    if (existing) return true;
  } catch {
    // ignore
  }

  try {
    const ff = new FontFaceCtor(family, source, { display: 'swap' } as any);
    const loaded = await ff.load();
    fonts.add(loaded);
    return true;
  } catch {
    return false;
  }
}

function resolveViteBaseUrl(): string {
  try {
    const base = (import.meta as any)?.env?.BASE_URL;
    if (typeof base === 'string' && base.length > 0) return base;
  } catch {
    // ignore
  }
  return '/';
}

function joinBaseUrl(base: string, rel: string): string {
  const b = base.endsWith('/') ? base : `${base}/`;
  const r = rel.replace(/^\/+/, '');
  return `${b}${r}`;
}

function cssUrl(u: string): string {
  // Quote URL to avoid issues with special characters.
  return `url(${JSON.stringify(u)})`;
}

/**
 * Best-effort load preview fonts.
 *
 * Notes:
 * - We intentionally do not ship Mojangles/Unifont binaries in-repo.
 * - If the corresponding files are missing (404), loading fails silently and browser fallback applies.
 */
export async function ensurePreviewFontsLoaded(state: PreviewFontState): Promise<void> {
  // Minecraft-like fonts, expected to be placed in web-editor/public/fonts/
  // (paths are relative to Vite public root)
  const base = resolveViteBaseUrl();
  await tryLoadFontFace(PREVIEW_FONT_MOJANGLES_FAMILY, cssUrl(joinBaseUrl(base, 'fonts/mojangles.ttf')));
  await tryLoadFontFace(PREVIEW_FONT_UNIFONT_FAMILY, cssUrl(joinBaseUrl(base, 'fonts/unifont.ttf')));

  if (state.preset === 'custom') {
    if (state.customDataUrl) {
      await tryLoadFontFace(PREVIEW_FONT_CUSTOM_FAMILY, cssUrl(state.customDataUrl));
    } else if (state.customUrl) {
      await tryLoadFontFace(PREVIEW_FONT_CUSTOM_FAMILY, cssUrl(state.customUrl));
    }
  }
}
