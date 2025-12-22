import {useEffect, useMemo} from 'react';
import {useMachineUiStore} from '../store/machineUiStore';
import {ensurePreviewFontsLoaded, getPreviewFontFamily} from './previewFonts';

/**
 * Editor-only preview font settings for Machine UI.
 *
 * This hook also best-effort loads font faces (if FontFace API is available).
 */
export function usePreviewFont() {
  const previewFont = useMachineUiStore((s) => s.previewFont);

  useEffect(() => {
    // Fire and forget; fallback fonts will be used if loading fails.
    void ensurePreviewFontsLoaded(previewFont);
  }, [previewFont]);

  return useMemo(() => {
    return {
      fontFamily: getPreviewFontFamily(previewFont),
      fontScale: previewFont.scale,
    };
  }, [previewFont]);
}
