export type PmAssetIndex = {
  /** Paths relative to PM textures root, e.g. "gui/slot.png" */
  paths: string[];
};

export async function fetchPmAssetIndex(): Promise<PmAssetIndex> {
  const base = import.meta.env.BASE_URL || '/';
  const res = await fetch(`${base}pm-asset-index.json`, { cache: 'no-store' });
  if (!res.ok) {
    throw new Error(`Failed to load pm-asset-index.json: ${res.status}`);
  }
  return (await res.json()) as PmAssetIndex;
}

export function pmTextureUrl(texturePath: string): string {
  const base = import.meta.env.BASE_URL || '/';
  const p = texturePath.replace(/^\/+/, '');
  return `${base}pm-textures/${p}`;
}

export function pmLogoUrl(): string {
  const base = import.meta.env.BASE_URL || '/';
  return `${base}pm-logo.png`;
}
