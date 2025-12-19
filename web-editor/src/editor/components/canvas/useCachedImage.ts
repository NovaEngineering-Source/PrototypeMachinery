import {useEffect, useState} from 'react';

const imageCache = new Map<string, HTMLImageElement>();
const brokenUrls = new Set<string>();

function isUsable(img: HTMLImageElement): boolean {
  // A "broken" HTMLImageElement can be `complete===true` but has naturalWidth=0.
  return Boolean(img.complete && img.naturalWidth > 0 && img.naturalHeight > 0);
}

export function useCachedImage(url?: string): HTMLImageElement | undefined {
  const [img, setImg] = useState<HTMLImageElement | undefined>(() => {
    if (!url) return undefined;
    if (brokenUrls.has(url)) return undefined;
    const cached = imageCache.get(url);
    return cached && isUsable(cached) ? cached : undefined;
  });

  useEffect(() => {
    if (!url) {
      setImg(undefined);
      return;
    }

    if (brokenUrls.has(url)) {
      setImg(undefined);
      return;
    }

    const cached = imageCache.get(url);
    if (cached) {
      if (isUsable(cached)) {
        setImg(cached);
        return;
      }

      const onLoad = () => {
        if (isUsable(cached)) {
          setImg(cached);
        } else {
          brokenUrls.add(url);
          setImg(undefined);
        }
      };
      const onError = () => {
        brokenUrls.add(url);
        setImg(undefined);
      };

      cached.addEventListener('load', onLoad);
      cached.addEventListener('error', onError);
      return () => {
        cached.removeEventListener('load', onLoad);
        cached.removeEventListener('error', onError);
      };
    }

    const next = new window.Image();
    imageCache.set(url, next);
    const onLoad = () => {
      if (isUsable(next)) {
        setImg(next);
      } else {
        brokenUrls.add(url);
        setImg(undefined);
      }
    };
    const onError = () => {
      brokenUrls.add(url);
      setImg(undefined);
    };

    next.addEventListener('load', onLoad);
    next.addEventListener('error', onError);
    next.src = url;
    return () => {
      next.removeEventListener('load', onLoad);
      next.removeEventListener('error', onError);
    };
  }, [url]);

  return img;
}
