import {MachineUiDoc} from '../model/ir';

type AnyWidget = any;

function asStringId(v: unknown): string {
  return typeof v === 'string' ? v.trim() : '';
}

function buildExpandedWidgetsFlatToTree(widgets: AnyWidget[] | undefined): AnyWidget[] {
  const list = (widgets ?? []) as AnyWidget[];
  if (list.length === 0) return [];

  const byId = new Map<string, AnyWidget>();
  for (const w of list) {
    const id = asStringId(w?.id);
    if (!id) continue;
    byId.set(id, w);
  }

  // Containers supported by editor (flattened via childrenIds).
  const parentByChild = new Map<string, string>();
  for (const w of list) {
    const t = String(w?.type ?? '');
    if (t !== 'panel' && t !== 'scroll_container') continue;
    const cid = Array.isArray(w?.childrenIds) ? (w.childrenIds as any[]) : [];
    for (const raw of cid) {
      const childId = asStringId(raw);
      if (!childId) continue;
      if (!byId.has(childId)) continue;
      // first parent wins
      if (!parentByChild.has(childId)) parentByChild.set(childId, String(w.id));
    }
  }

  const roots = list.filter((w) => {
    const id = asStringId(w?.id);
    if (!id) return false;
    return !parentByChild.has(id);
  });

  const visit = (w: AnyWidget, origin?: { x: number; y: number }): AnyWidget | null => {
    if (!w) return null;

    const out: AnyWidget = { ...w };
    if (origin) {
      out.x = Math.trunc(Number(out.x ?? 0)) - Math.trunc(Number(origin.x ?? 0));
      out.y = Math.trunc(Number(out.y ?? 0)) - Math.trunc(Number(origin.y ?? 0));
      // Nested children do not participate in top-level tab partitioning.
      delete out.tabId;
    }

    const outType = String(out.type ?? '');

    if (outType === 'panel' || outType === 'scroll_container') {
      const cid = Array.isArray(out.childrenIds) ? (out.childrenIds as any[]) : [];
      const childWidgets: AnyWidget[] = [];
      for (const raw of cid) {
        const childId = asStringId(raw);
        if (!childId) continue;
        const child = byId.get(childId);
        if (!child) continue;
        const built = visit(child, { x: Number(w.x ?? 0), y: Number(w.y ?? 0) });
        if (built) childWidgets.push(built);
      }
      delete out.childrenIds;
      out.children = childWidgets;
    }

    return out;
  };

  return roots.map((w) => visit(w, undefined)).filter(Boolean) as AnyWidget[];
}

/**
 * Export a runtime-oriented JSON for Machine UI.
 *
 * This strips editor-only fields (guides + editor view options), but keeps
 * schemaVersion/canvas/options.tabs/backgrounds/widgets.
 */
export function exportMachineUiRuntimeJson(doc: MachineUiDoc): MachineUiDoc {
  const { options } = doc;
  const cleanedOptions = options
    ? {
        // runtime does not need these editor-only fields
        activeBackground: options.activeBackground,
        activeTabId: options.activeTabId,
        tabs: options.tabs,
        backgroundA: options.backgroundA,
        backgroundB: options.backgroundB,
      }
    : undefined;

  return {
    schemaVersion: doc.schemaVersion,
    name: doc.name,
    canvas: doc.canvas,
    options: cleanedOptions,
    // Expand editor containers (childrenIds) into runtime children arrays.
    widgets: buildExpandedWidgetsFlatToTree(doc.widgets as any) as any,
  } as any;
}
