import {create} from 'zustand';
import {nanoid} from '../../editor/store/nanoid';
import {loadFromLocalStorage, saveToLocalStorage} from '../../editor/io/storage';
import {
    createEmptyMachineUiDoc,
    MachineUiBackgroundKey,
    MachineUiButtonWidget,
    MachineUiDoc,
    MachineUiDocSchema,
    MachineUiGuide,
    MachineUiImageWidget,
    MachineUiPanelWidget,
    MachineUiPlayerInventoryWidget,
    MachineUiProgressBarWidget,
    MachineUiScrollContainerWidget,
    MachineUiSliderWidget,
    MachineUiSlotGridWidget,
    MachineUiTab,
    MachineUiTextFieldWidget,
    MachineUiTextWidget,
    MachineUiToggleWidget,
    MachineUiWidget,
} from '../model/ir';
import {AlignKind, computeAlignPatches, computeDistributePatches, computeSelectionBounds, normalizeWidgetBox,} from '../model/selectionOps';
import {
    resolveGuiStatesButtonSizing,
    resolveGuiStatesSliderSizing,
    resolveGuiStatesTextFieldSizing,
    resolveGuiStatesToggleSizing,
} from '../model/guiStatesPreview';

type Snapshot = {
  doc: MachineUiDoc;
};

// batch nudge edits into a single undo step
let pendingNudgeSnapshot: Snapshot | null = null;

export type ViewState = {
  scale: number;
  offsetX: number;
  offsetY: number;
};

export type SelectionState = {
  mode: 'widgets' | 'guides';
  selectedGuideId?: string;
  selectedWidgetId?: string;
  selectedWidgetIds: string[];
};

const HISTORY_LIMIT = 100;

const LS_KEY = 'pm_machine_ui_doc_v1';
const LS_PREVIEW_FONT_KEY = 'pm_machine_ui_preview_font_v1';

export type PreviewFontPreset = 'minecraft' | 'system' | 'custom';

export type PreviewFontState = {
  preset: PreviewFontPreset;
  scale: number;
  customDataUrl?: string;
  customUrl?: string;
  customName?: string;
};

function clampPreviewFontScale(v: unknown, fallback = 1): number {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(0.25, Math.min(3, n));
}

function normalizePreviewFontState(raw: unknown): PreviewFontState {
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

type MachineUiState = {
  doc: MachineUiDoc;

  // editor-only font preview (not undoable; not exported to runtime)
  previewFont: PreviewFontState;
  setPreviewFontPreset: (preset: PreviewFontPreset) => void;
  setPreviewFontScale: (scale: number) => void;
  setCustomPreviewFont: (dataUrl?: string, name?: string) => void;
  setCustomPreviewFontUrl: (url?: string, name?: string) => void;

  // selection (not undoable)
  selection: SelectionState;
  setMode: (mode: SelectionState['mode']) => void;
  setSelectedGuideId: (id?: string) => void;
  setSelectedWidgetId: (id?: string) => void;

  setSelectedWidgetIds: (ids: string[], primaryId?: string) => void;
  toggleSelectedWidgetId: (id: string) => void;
  clearWidgetSelection: () => void;

  deleteSelected: () => void;
  duplicateSelectedWidget: () => void;

  nudgeSelectionLive: (dx: number, dy: number) => void;
  commitNudgeBatch: () => void;

  alignSelectedWidget: (kind: 'left' | 'hcenter' | 'right' | 'top' | 'vcenter' | 'bottom' | 'center') => void;

  /** Align current multi-selection to selection bounds (moves unlocked widgets only). */
  alignSelection: (kind: AlignKind) => void;
  /** Distribute current multi-selection evenly by centers (moves unlocked widgets only). */
  distributeSelection: (axis: 'x' | 'y') => void;

  // view (not undoable)
  view: ViewState;
  setView: (next: ViewState | ((prev: ViewState) => ViewState)) => void;

  historyPast: Snapshot[];
  historyFuture: Snapshot[];
  canUndo: () => boolean;
  canRedo: () => boolean;
  undo: () => void;
  redo: () => void;

  setDocName: (name: string) => void;
  setCanvasSize: (width: number, height: number) => void;

  setActiveBackground: (key: MachineUiBackgroundKey) => void;
  setBackgroundTexturePath: (key: MachineUiBackgroundKey, texturePath?: string) => void;

  // tabs
  setActiveTabId: (tabId: string) => void;
  addTab: (preset?: Partial<Omit<MachineUiTab, 'id'>> & { id?: string }) => void;
  removeTab: (tabId: string) => void;
  updateTab: (tabId: string, patch: Partial<Pick<MachineUiTab, 'label' | 'texturePath'>>) => void;

  setGridSize: (gridSize: number) => void;

  setShowGuides: (show: boolean) => void;

  updateGuideLive: (
    id: string,
    patch: Partial<Pick<MachineUiGuide, 'x' | 'y' | 'w' | 'h' | 'label' | 'color' | 'locked'>>,
  ) => void;
  updateGuide: (
    id: string,
    patch: Partial<Pick<MachineUiGuide, 'x' | 'y' | 'w' | 'h' | 'label' | 'color' | 'locked'>>,
  ) => void;

  removeGuide: (id: string) => void;

  addTextWidget: (preset?: Partial<Omit<MachineUiTextWidget, 'type' | 'id'>>) => void;
  addSlotGridWidget: (preset?: Partial<Omit<MachineUiSlotGridWidget, 'type' | 'id'>>) => void;
  addProgressBarWidget: (preset?: Partial<Omit<MachineUiProgressBarWidget, 'type' | 'id'>>) => void;
  addImageWidget: (preset?: Partial<Omit<MachineUiImageWidget, 'type' | 'id'>>) => void;
  addButtonWidget: (preset?: Partial<Omit<MachineUiButtonWidget, 'type' | 'id'>>) => void;
  addToggleWidget: (preset?: Partial<Omit<MachineUiToggleWidget, 'type' | 'id'>>) => void;
  addSliderWidget: (preset?: Partial<Omit<MachineUiSliderWidget, 'type' | 'id'>>) => void;
  addTextFieldWidget: (preset?: Partial<Omit<MachineUiTextFieldWidget, 'type' | 'id'>>) => void;
  addPlayerInventoryWidget: (preset?: Partial<Omit<MachineUiPlayerInventoryWidget, 'type' | 'id'>>) => void;

  addScrollContainerWidget: (preset?: Partial<Omit<MachineUiScrollContainerWidget, 'type' | 'id'>>) => void;

  /** Create a Panel container and assign current selection as childrenIds. */
  groupSelectionIntoPanel: () => void;
  /** If selected widget is a Panel container, remove it (children remain as normal widgets). */
  ungroupSelectedPanel: () => void;

  /** Create a ScrollContainer container and assign current selection as childrenIds. */
  groupSelectionIntoScrollContainer: () => void;
  /** If selected widget is a ScrollContainer container, remove it (children remain as normal widgets). */
  ungroupSelectedScrollContainer: () => void;

  updateWidgetLive: (id: string, patch: Partial<MachineUiWidget>) => void;
  updateWidget: (id: string, patch: Partial<MachineUiWidget>) => void;

  /** Batch update widgets without pushing an undo snapshot (high-frequency / drag). */
  updateWidgetsLive: (patchesById: Record<string, Partial<MachineUiWidget>>) => void;
  /** Batch update widgets and push a single undo snapshot. */
  updateWidgets: (patchesById: Record<string, Partial<MachineUiWidget>>) => void;

  removeWidget: (id: string) => void;

  // import/export helpers
  importFromJson: (raw: unknown) => void;
  saveToLocal: () => void;
  loadFromLocal: () => void;
};

export const useMachineUiStore = create<MachineUiState>((set, get) => ({
  doc: createEmptyMachineUiDoc(),

  previewFont: normalizePreviewFontState(loadFromLocalStorage(LS_PREVIEW_FONT_KEY)) ?? { preset: 'minecraft', scale: 1 },
  setPreviewFontPreset: (preset) =>
    set((s) => {
      const next: PreviewFontState = { ...s.previewFont, preset: preset ?? 'minecraft' };
      saveToLocalStorage(LS_PREVIEW_FONT_KEY, next);
      return { previewFont: next };
    }),
  setPreviewFontScale: (scale) =>
    set((s) => {
      const next: PreviewFontState = { ...s.previewFont, scale: clampPreviewFontScale(scale, 1) };
      saveToLocalStorage(LS_PREVIEW_FONT_KEY, next);
      return { previewFont: next };
    }),
  setCustomPreviewFont: (dataUrl, name) =>
    set((s) => {
      const cleaned = typeof dataUrl === 'string' && dataUrl.startsWith('data:') ? dataUrl : undefined;
      const next: PreviewFontState = {
        ...s.previewFont,
        customDataUrl: cleaned,
        customUrl: cleaned ? undefined : s.previewFont.customUrl,
        customName: typeof name === 'string' ? name : s.previewFont.customName,
      };
      saveToLocalStorage(LS_PREVIEW_FONT_KEY, next);
      return { previewFont: next };
    }),

  setCustomPreviewFontUrl: (url, name) =>
    set((s) => {
      const raw = typeof url === 'string' ? url.trim() : '';
      const cleaned =
        raw && (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('/') || raw.startsWith('./')) ? raw : undefined;
      const next: PreviewFontState = {
        ...s.previewFont,
        customUrl: cleaned,
        customDataUrl: cleaned ? undefined : s.previewFont.customDataUrl,
        customName: typeof name === 'string' ? name : s.previewFont.customName,
      };
      saveToLocalStorage(LS_PREVIEW_FONT_KEY, next);
      return { previewFont: next };
    }),

  selection: {
    mode: 'widgets',
    selectedGuideId: undefined,
    selectedWidgetId: undefined,
    selectedWidgetIds: [],
  },
  setMode: (mode) =>
    set((s) => ({
      selection: {
        ...s.selection,
        mode,
      },
    })),
  setSelectedGuideId: (id) =>
    set((s) => ({
      selection: {
        ...s.selection,
        selectedGuideId: id,
        // don't keep widget selected at the same time (simple)
        selectedWidgetId: undefined,
        selectedWidgetIds: [],
      },
    })),
  setSelectedWidgetId: (id) =>
    set((s) => ({
      selection: {
        ...s.selection,
        selectedWidgetId: id,
        selectedGuideId: undefined,
        selectedWidgetIds: id ? [id] : [],
      },
    })),

  setSelectedWidgetIds: (ids, primaryId) => {
    const uniq = Array.from(new Set((ids ?? []).map((x) => String(x).trim()).filter(Boolean)));
    const primary = String(primaryId ?? '').trim();
    const nextPrimary = primary && uniq.includes(primary) ? primary : uniq[0];
    set((s) => ({
      selection: {
        ...s.selection,
        selectedGuideId: undefined,
        selectedWidgetIds: uniq,
        selectedWidgetId: nextPrimary,
      },
    }));
  },

  toggleSelectedWidgetId: (id) => {
    const wid = String(id ?? '').trim();
    if (!wid) return;
    set((s) => {
      const cur = s.selection.selectedWidgetIds ?? [];
      const has = cur.includes(wid);
      const next = has ? cur.filter((x) => x !== wid) : [...cur, wid];
      const nextPrimary = !has ? wid : s.selection.selectedWidgetId === wid ? next[0] : s.selection.selectedWidgetId;
      return {
        selection: {
          ...s.selection,
          selectedGuideId: undefined,
          selectedWidgetIds: next,
          selectedWidgetId: nextPrimary,
        },
      };
    });
  },

  clearWidgetSelection: () =>
    set((s) => ({
      selection: {
        ...s.selection,
        selectedWidgetId: undefined,
        selectedWidgetIds: [],
      },
    })),

  deleteSelected: () => {
    const { selection } = get();
    if (selection.mode === 'guides' && selection.selectedGuideId) {
      get().removeGuide(selection.selectedGuideId);
      return;
    }
    if (selection.mode === 'widgets') {
      const ids = (selection.selectedWidgetIds ?? []).length > 0 ? selection.selectedWidgetIds : selection.selectedWidgetId ? [selection.selectedWidgetId] : [];
      ids.forEach((id) => get().removeWidget(id));
    }
  },

  duplicateSelectedWidget: () => {
    const { selection, doc } = get();
    const id = selection.selectedWidgetId;
    if (!id) return;
    // if multi-selected, keep it simple: duplicate only the primary widget
    const widgets = doc.widgets ?? [];
    const src = widgets.find((w) => w.id === id);
    if (!src) return;

    const prev: Snapshot = { doc: get().doc };
    const newId = `w_${nanoid(10)}`;
    const grid = Math.max(1, Math.floor(doc.options?.gridSize ?? 8));

    const dx = grid;
    const dy = grid;
    const maxX = Math.max(0, doc.canvas.width - Math.max(1, Math.floor((src as any).w ?? 1)));
    const maxY = Math.max(0, doc.canvas.height - Math.max(1, Math.floor((src as any).h ?? 1)));
    const nextX = Math.max(0, Math.min(maxX, Math.floor((src as any).x ?? 0) + dx));
    const nextY = Math.max(0, Math.min(maxY, Math.floor((src as any).y ?? 0) + dy));

    const clone: any = {
      ...src,
      id: newId,
      x: nextX,
      y: nextY,
    };

    // Avoid creating ambiguous parentage (one child -> multiple containers) by default.
    if ((clone as any).type === 'panel' || (clone as any).type === 'scroll_container') {
      (clone as any).childrenIds = [];
    }

    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        widgets: [...(s.doc.widgets ?? []), clone],
      },
      selection: {
        ...s.selection,
        mode: 'widgets',
        selectedWidgetId: newId,
        selectedWidgetIds: [newId],
        selectedGuideId: undefined,
      },
    }));
  },

  groupSelectionIntoPanel: () => {
    const { selection, doc } = get();
    if (selection.mode !== 'widgets') return;

    const ids = (selection.selectedWidgetIds ?? []).length > 0 ? selection.selectedWidgetIds : selection.selectedWidgetId ? [selection.selectedWidgetId] : [];
    const uniqIds = Array.from(new Set(ids.map((x) => String(x ?? '').trim()).filter(Boolean)));
    if (uniqIds.length === 0) return;

    const widgets = doc.widgets ?? [];
    const byId = new Map(widgets.map((w) => [w.id, w] as const));
    const selected = uniqIds.map((id) => byId.get(id)).filter(Boolean) as MachineUiWidget[];
    if (selected.length !== uniqIds.length) return;

    // For MVP, require all selected widgets on same tab (including "global" = empty/undefined).
    const tab0 = String((selected[0] as any).tabId ?? '').trim();
    for (const w of selected) {
      const t = String((w as any).tabId ?? '').trim();
      if (t !== tab0) return;
    }

    const boxes = selected.map((w) => normalizeWidgetBox(w));
    const bounds = computeSelectionBounds(boxes);
    if (!bounds) return;

    const grid = Math.max(1, Math.floor(doc.options?.gridSize ?? 1));
    const pad = Math.max(2, Math.min(16, grid));

    const rawX = Math.floor(bounds.minX - pad);
    const rawY = Math.floor(bounds.minY - pad);
    const rawW = Math.ceil(bounds.maxX2 - bounds.minX + pad * 2);
    const rawH = Math.ceil(bounds.maxY2 - bounds.minY + pad * 2);

    const x = Math.max(0, Math.min(Math.max(0, doc.canvas.width - 1), rawX));
    const y = Math.max(0, Math.min(Math.max(0, doc.canvas.height - 1), rawY));
    const w = Math.max(1, Math.min(doc.canvas.width - x, rawW));
    const h = Math.max(1, Math.min(doc.canvas.height - y, rawH));

    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;

    const panel: MachineUiPanelWidget = {
      type: 'panel',
      id,
      tabId: tab0 || undefined,
      x,
      y,
      w,
      h,
      childrenIds: uniqIds,
      locked: false,
    };

    set((s) => {
      const nextWidgets = [...(s.doc.widgets ?? []), panel as any];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedGuideId: undefined,
          selectedWidgetId: id,
          selectedWidgetIds: [id],
        },
      };
    });
  },

  ungroupSelectedPanel: () => {
    const { selection, doc } = get();
    if (selection.mode !== 'widgets') return;
    const ids = selection.selectedWidgetIds ?? [];
    if (ids.length !== 1) return;
    const id = String(ids[0] ?? '').trim();
    if (!id) return;

    const widgets = doc.widgets ?? [];
    const cur = widgets.find((w) => w.id === id) as any;
    if (!cur || cur.type !== 'panel') return;
    const childrenIds: string[] = Array.from(
      new Set(
        ((cur as any).childrenIds ?? [])
          .map((x: any) => String(x ?? '').trim())
          .filter((x: string) => x.length > 0),
      ),
    );

    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const nextWidgets = (s.doc.widgets ?? []).filter((w) => w.id !== id);
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedGuideId: undefined,
          selectedWidgetId: childrenIds[0] ?? undefined,
          selectedWidgetIds: childrenIds,
        },
      };
    });
  },

  groupSelectionIntoScrollContainer: () => {
    const { selection, doc } = get();
    if (selection.mode !== 'widgets') return;

    const ids = (selection.selectedWidgetIds ?? []).length > 0 ? selection.selectedWidgetIds : selection.selectedWidgetId ? [selection.selectedWidgetId] : [];
    const uniqIds = Array.from(new Set(ids.map((x) => String(x ?? '').trim()).filter(Boolean)));
    if (uniqIds.length === 0) return;

    const widgets = doc.widgets ?? [];
    const byId = new Map(widgets.map((w) => [w.id, w] as const));
    const selected = uniqIds.map((id) => byId.get(id)).filter(Boolean) as MachineUiWidget[];
    if (selected.length !== uniqIds.length) return;

    // For MVP, require all selected widgets on same tab (including "global" = empty/undefined).
    const tab0 = String((selected[0] as any).tabId ?? '').trim();
    for (const w of selected) {
      const t = String((w as any).tabId ?? '').trim();
      if (t !== tab0) return;
    }

    const boxes = selected.map((w) => normalizeWidgetBox(w));
    const bounds = computeSelectionBounds(boxes);
    if (!bounds) return;

    const grid = Math.max(1, Math.floor(doc.options?.gridSize ?? 1));
    const pad = Math.max(2, Math.min(16, grid));

    const rawX = Math.floor(bounds.minX - pad);
    const rawY = Math.floor(bounds.minY - pad);
    const rawW = Math.ceil(bounds.maxX2 - bounds.minX + pad * 2);
    const rawH = Math.ceil(bounds.maxY2 - bounds.minY + pad * 2);

    const x = Math.max(0, Math.min(Math.max(0, doc.canvas.width - 1), rawX));
    const y = Math.max(0, Math.min(Math.max(0, doc.canvas.height - 1), rawY));
    const w = Math.max(1, Math.min(doc.canvas.width - x, rawW));
    const h = Math.max(1, Math.min(doc.canvas.height - y, rawH));

    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;

    const container: MachineUiScrollContainerWidget = {
      type: 'scroll_container',
      id,
      tabId: tab0 || undefined,
      x,
      y,
      w,
      h,
      scrollX: false,
      scrollY: true,
      scrollSpeed: 30,
      cancelScrollEdge: true,
      childrenIds: uniqIds,
      locked: false,
    };

    set((s) => {
      const nextWidgets = [...(s.doc.widgets ?? []), container as any];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedGuideId: undefined,
          selectedWidgetId: id,
          selectedWidgetIds: [id],
        },
      };
    });
  },

  ungroupSelectedScrollContainer: () => {
    const { selection, doc } = get();
    if (selection.mode !== 'widgets') return;
    const ids = selection.selectedWidgetIds ?? [];
    if (ids.length !== 1) return;
    const id = String(ids[0] ?? '').trim();
    if (!id) return;

    const widgets = doc.widgets ?? [];
    const cur = widgets.find((w) => w.id === id) as any;
    if (!cur || cur.type !== 'scroll_container') return;
    const childrenIds: string[] = Array.from(
      new Set(
        ((cur as any).childrenIds ?? [])
          .map((x: any) => String(x ?? '').trim())
          .filter((x: string) => x.length > 0),
      ),
    );

    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const nextWidgets = (s.doc.widgets ?? []).filter((w) => w.id !== id);
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedGuideId: undefined,
          selectedWidgetId: childrenIds[0] ?? undefined,
          selectedWidgetIds: childrenIds,
        },
      };
    });
  },

  nudgeSelectionLive: (dx, dy) => {
    const deltaX = Math.trunc(Number(dx) || 0);
    const deltaY = Math.trunc(Number(dy) || 0);
    if (deltaX === 0 && deltaY === 0) return;

    const { selection, doc } = get();
    if (selection.mode === 'widgets') {
      const ids = (selection.selectedWidgetIds ?? []).length > 0 ? selection.selectedWidgetIds : selection.selectedWidgetId ? [selection.selectedWidgetId] : [];
      if (ids.length === 0) return;
      const widgets = doc.widgets ?? [];
      const idSet = new Set(ids);
      const indices = widgets
        .map((w, idx) => ({ w, idx }))
        .filter(({ w }) => idSet.has(w.id))
        .map(({ idx }) => idx);
      if (indices.length === 0) return;

      if (!pendingNudgeSnapshot) pendingNudgeSnapshot = { doc };

      set((s) => {
        const nextWidgets = (s.doc.widgets ?? []).slice();
        for (const idx of indices) {
          const cur = nextWidgets[idx] as any;
          if (!cur) continue;
          const w = Math.max(1, Math.trunc(Number(cur.w) || 1));
          const h = Math.max(1, Math.trunc(Number(cur.h) || 1));
          const maxX = Math.max(0, Math.trunc(s.doc.canvas.width) - w);
          const maxY = Math.max(0, Math.trunc(s.doc.canvas.height) - h);
          const nextX = Math.max(0, Math.min(maxX, Math.trunc(Number(cur.x) || 0) + deltaX));
          const nextY = Math.max(0, Math.min(maxY, Math.trunc(Number(cur.y) || 0) + deltaY));
          nextWidgets[idx] = { ...cur, x: nextX, y: nextY };
        }
        return {
          ...s,
          doc: {
            ...s.doc,
            widgets: nextWidgets,
          },
        };
      });
      return;
    }

    // guides
    const gid = selection.selectedGuideId;
    if (!gid) return;
    const guides = doc.guides ?? [];
    const gidx = guides.findIndex((g) => g.id === gid);
    if (gidx < 0) return;

    if (!pendingNudgeSnapshot) pendingNudgeSnapshot = { doc };

    set((s) => {
      const cur = (s.doc.guides ?? [])[gidx] as any;
      if (!cur) return s;
      if (cur.locked) return s;
      const w = Math.max(1, Math.trunc(Number(cur.w) || 1));
      const h = Math.max(1, Math.trunc(Number(cur.h) || 1));
      const maxX = Math.max(0, Math.trunc(s.doc.canvas.width) - w);
      const maxY = Math.max(0, Math.trunc(s.doc.canvas.height) - h);
      const nextX = Math.max(0, Math.min(maxX, Math.trunc(Number(cur.x) || 0) + deltaX));
      const nextY = Math.max(0, Math.min(maxY, Math.trunc(Number(cur.y) || 0) + deltaY));

      const nextGuides = (s.doc.guides ?? []).slice();
      nextGuides[gidx] = { ...cur, x: nextX, y: nextY };
      return {
        ...s,
        doc: {
          ...s.doc,
          guides: nextGuides,
        },
      };
    });
  },

  commitNudgeBatch: () => {
    if (!pendingNudgeSnapshot) return;
    const prev = pendingNudgeSnapshot;
    pendingNudgeSnapshot = null;
    set((s) => ({
      ...s,
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
    }));
  },

  alignSelectedWidget: (kind) => {
    const { selection, doc } = get();
    const id = selection.selectedWidgetId;
    if (!id) return;
    const widgets = doc.widgets ?? [];
    const idx = widgets.findIndex((w) => w.id === id);
    if (idx < 0) return;
    const wdg: any = widgets[idx];
    const w = Math.max(1, Math.trunc(Number(wdg.w) || 1));
    const h = Math.max(1, Math.trunc(Number(wdg.h) || 1));

    const maxX = Math.max(0, Math.trunc(doc.canvas.width) - w);
    const maxY = Math.max(0, Math.trunc(doc.canvas.height) - h);

    let x = Math.trunc(Number(wdg.x) || 0);
    let y = Math.trunc(Number(wdg.y) || 0);

    if (kind === 'left') x = 0;
    if (kind === 'right') x = maxX;
    if (kind === 'hcenter' || kind === 'center') x = Math.round(maxX / 2);

    if (kind === 'top') y = 0;
    if (kind === 'bottom') y = maxY;
    if (kind === 'vcenter' || kind === 'center') y = Math.round(maxY / 2);

    x = Math.max(0, Math.min(maxX, x));
    y = Math.max(0, Math.min(maxY, y));

    get().updateWidget(id, { x, y } as any);
  },

  alignSelection: (kind) => {
    const { selection, doc } = get();
    const ids = (selection.selectedWidgetIds ?? []).length > 0 ? selection.selectedWidgetIds : selection.selectedWidgetId ? [selection.selectedWidgetId] : [];
    if (ids.length === 0) return;
    const widgets = doc.widgets ?? [];
    const boxes = widgets
      .filter((w) => ids.includes(w.id))
      .map((w) => normalizeWidgetBox(w));
    if (boxes.length === 0) return;

    const grid = Math.max(1, Math.floor(doc.options?.gridSize ?? 1));
    const patches = computeAlignPatches(boxes, kind, {
      canvasW: doc.canvas.width,
      canvasH: doc.canvas.height,
      grid,
      includeLockedInBounds: true,
    });
    if (Object.keys(patches).length === 0) return;
    get().updateWidgets(patches as any);
  },

  distributeSelection: (axis) => {
    const { selection, doc } = get();
    const ids = (selection.selectedWidgetIds ?? []).length > 0 ? selection.selectedWidgetIds : selection.selectedWidgetId ? [selection.selectedWidgetId] : [];
    if (ids.length === 0) return;
    const widgets = doc.widgets ?? [];
    const boxes = widgets
      .filter((w) => ids.includes(w.id))
      .map((w) => normalizeWidgetBox(w));
    if (boxes.length === 0) return;

    const grid = Math.max(1, Math.floor(doc.options?.gridSize ?? 1));
    const patches = computeDistributePatches(boxes, axis, {
      canvasW: doc.canvas.width,
      canvasH: doc.canvas.height,
      grid,
    });
    if (Object.keys(patches).length === 0) return;
    get().updateWidgets(patches as any);
  },

  view: {
    scale: 1,
    offsetX: 0,
    offsetY: 0,
  },
  setView: (next) =>
    set((s) => ({
      view: typeof next === 'function' ? (next as (p: ViewState) => ViewState)(s.view) : next,
    })),

  historyPast: [],
  historyFuture: [],
  canUndo: () => get().historyPast.length > 0,
  canRedo: () => get().historyFuture.length > 0,

  undo: () => {
    set((s) => {
      if (s.historyPast.length === 0) return s;
      const prev = s.historyPast[s.historyPast.length - 1];
      const newPast = s.historyPast.slice(0, -1);
      const cur: Snapshot = { doc: s.doc };
      return {
        ...s,
        doc: prev.doc,
        historyPast: newPast,
        historyFuture: [cur, ...s.historyFuture].slice(0, HISTORY_LIMIT),
      };
    });
  },

  redo: () => {
    set((s) => {
      if (s.historyFuture.length === 0) return s;
      const next = s.historyFuture[0];
      const newFuture = s.historyFuture.slice(1);
      const cur: Snapshot = { doc: s.doc };
      return {
        ...s,
        doc: next.doc,
        historyPast: [...s.historyPast, cur].slice(-HISTORY_LIMIT),
        historyFuture: newFuture,
      };
    });
  },

  setDocName: (name) => {
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: { ...s.doc, name },
    }));
  },

  setCanvasSize: (width, height) => {
    const w = Math.max(1, Math.floor(width));
    const h = Math.max(1, Math.floor(height));
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: { ...s.doc, canvas: { width: w, height: h } },
    }));
  },

  setActiveBackground: (key) => {
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        options: {
          ...s.doc.options,
          activeBackground: key,
          // keep activeTabId in sync for legacy A/B switching
          activeTabId: key,
        },
      },
    }));
  },

  setActiveTabId: (tabId) => {
    const cleaned = String(tabId ?? '').trim();
    if (!cleaned) return;
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        options: {
          ...s.doc.options,
          activeTabId: cleaned,
          // if switching to A/B, also keep activeBackground updated
          activeBackground: cleaned === 'B' ? 'B' : cleaned === 'A' ? 'A' : s.doc.options?.activeBackground,
        },
      },
    }));
  },

  addTab: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = String((preset as any)?.id ?? '').trim() || `tab_${nanoid(6)}`;
    set((s) => {
      const existing = s.doc.options?.tabs ?? [];
      if (existing.some((t) => t.id === id)) return s;
      const nextTab: MachineUiTab = {
        id,
        label: (preset as any)?.label,
        texturePath: (preset as any)?.texturePath,
      };
      const tabs = [...existing, nextTab];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          options: {
            ...s.doc.options,
            tabs,
            activeTabId: id,
          },
        },
      };
    });
  },

  removeTab: (tabId) => {
    const id = String(tabId ?? '').trim();
    if (!id) return;
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const tabs = s.doc.options?.tabs ?? [];
      if (tabs.length <= 1) return s;
      if (!tabs.some((t) => t.id === id)) return s;
      const nextTabs = tabs.filter((t) => t.id !== id);
      const nextActive = (s.doc.options?.activeTabId ?? 'A') === id ? nextTabs[0]?.id : s.doc.options?.activeTabId;
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          options: {
            ...s.doc.options,
            tabs: nextTabs,
            activeTabId: nextActive,
            activeBackground:
              nextActive === 'B' ? 'B' : nextActive === 'A' ? 'A' : s.doc.options?.activeBackground,
          },
        },
      };
    });
  },

  updateTab: (tabId, patch) => {
    const id = String(tabId ?? '').trim();
    if (!id) return;
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const tabs = s.doc.options?.tabs ?? [];
      const idx = tabs.findIndex((t) => t.id === id);
      if (idx < 0) return s;
      const next = tabs.slice();
      next[idx] = { ...next[idx], ...patch } as any;
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          options: {
            ...s.doc.options,
            tabs: next,
          },
        },
      };
    });
  },

  setBackgroundTexturePath: (key, texturePath) => {
    const prev: Snapshot = { doc: get().doc };
    const cleaned = typeof texturePath === 'string' ? texturePath.trim().replace(/^\/+/, '') : '';
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        options: {
          ...s.doc.options,
          tabs:
            s.doc.options?.tabs?.map((t) =>
              t.id === key
                ? {
                    ...t,
                    texturePath: cleaned || undefined,
                  }
                : t,
            ) ?? s.doc.options?.tabs,
          backgroundA:
            key === 'A'
              ? { ...(s.doc.options?.backgroundA ?? {}), texturePath: cleaned || undefined }
              : s.doc.options?.backgroundA,
          backgroundB:
            key === 'B'
              ? { ...(s.doc.options?.backgroundB ?? {}), texturePath: cleaned || undefined }
              : s.doc.options?.backgroundB,
        },
      },
    }));
  },

  setGridSize: (gridSize) => {
    const g = Math.max(1, Math.floor(Number(gridSize) || 1));
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        options: {
          ...s.doc.options,
          gridSize: g,
        },
      },
    }));
  },

  setShowGuides: (show) => {
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        options: {
          ...s.doc.options,
          showGuides: Boolean(show),
        },
      },
    }));
  },

  updateGuideLive: (id, patch) => {
    set((s) => {
      const guides = s.doc.guides ?? [];
      const idx = guides.findIndex((g) => g.id === id);
      if (idx < 0) return s;
      const nextGuides = guides.slice();
      nextGuides[idx] = { ...nextGuides[idx], ...patch } as any;
      return {
        ...s,
        doc: {
          ...s.doc,
          guides: nextGuides,
        },
      };
    });
  },

  updateGuide: (id, patch) => {
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const guides = s.doc.guides ?? [];
      const idx = guides.findIndex((g) => g.id === id);
      if (idx < 0) return s;
      const nextGuides = guides.slice();
      nextGuides[idx] = { ...nextGuides[idx], ...patch } as any;
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          guides: nextGuides,
        },
      };
    });
  },

  removeGuide: (id) => {
    const guideId = String(id ?? '').trim();
    if (!guideId) return;
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const guides = s.doc.guides ?? [];
      if (!guides.some((g) => g.id === guideId)) return s;
      const nextGuides = guides.filter((g) => g.id !== guideId);
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          guides: nextGuides,
        },
        selection: {
          ...s.selection,
          selectedGuideId: s.selection.selectedGuideId === guideId ? undefined : s.selection.selectedGuideId,
        },
      };
    });
  },

  addTextWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';
    const base: MachineUiTextWidget = {
      type: 'text',
      id,
      tabId: activeTabId,
      x: 32,
      y: 32,
      w: 120,
      h: 18,
      text: 'Text',
      color: '#e9ecef',
      fontSize: 12,
      align: 'left',
      shadow: false,
      locked: false,
    };
    const nextWidget: MachineUiTextWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'text',
      id,
    };
    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addSlotGridWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';
    const cols = Math.max(1, Math.floor((preset as any)?.cols ?? 3));
    const rows = Math.max(1, Math.floor((preset as any)?.rows ?? 3));
    const slotSize = Math.max(1, Math.floor((preset as any)?.slotSize ?? 18));
    const gap = Math.max(0, Math.floor((preset as any)?.gap ?? 0));
    const w = cols * slotSize + Math.max(0, cols - 1) * gap;
    const h = rows * slotSize + Math.max(0, rows - 1) * gap;

    const base: MachineUiSlotGridWidget = {
      type: 'slotGrid',
      id,
      tabId: activeTabId,
      x: 32,
      y: 64,
      w,
      h,
      cols,
      rows,
      slotSize,
      gap,
      slotKey: 'default',
      startIndex: 0,
      canInsert: true,
      canExtract: true,
      locked: false,
    };

    const nextWidget: MachineUiSlotGridWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'slotGrid',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addProgressBarWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';
    const base: MachineUiProgressBarWidget = {
      type: 'progress',
      id,
      tabId: activeTabId,
      x: 140,
      y: 64,
      w: 80,
      h: 14,
      progress: 0.5,
      direction: 'right',
      fillColor: '#51cf66',
      bgColor: 'rgba(0,0,0,0.25)',
      locked: false,
    };

    const nextWidget: MachineUiProgressBarWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'progress',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addImageWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiImageWidget = {
      type: 'image',
      id,
      tabId: activeTabId,
      x: 32,
      y: 32,
      w: 16,
      h: 16,
      texturePath: 'gui/slot.png',
      locked: false,
    };

    const nextWidget: MachineUiImageWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'image',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addButtonWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiButtonWidget = {
      type: 'button',
      id,
      tabId: activeTabId,
      x: 200,
      y: 32,
      w: 60,
      h: 19,
      skin: 'gui_states/expand_button/normal',
      text: 'Button',
      actionKey: 'action',
      locked: false,
    };

    const nextWidget: MachineUiButtonWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'button',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addToggleWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiToggleWidget = {
      type: 'toggle',
      id,
      tabId: activeTabId,
      x: 200,
      y: 56,
      w: 28,
      h: 14,
      skin: 'gui_states/switch/normal',
      stateKey: 'state',
      textOn: 'ON',
      textOff: 'OFF',
      locked: false,
    };

    const nextWidget: MachineUiToggleWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'toggle',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addSliderWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiSliderWidget = {
      type: 'slider',
      id,
      tabId: activeTabId,
      x: 32,
      y: 120,
      w: 120,
      h: 13,
      skin: 'gui_states/slider/m/x_expand/normal',
      min: 0,
      max: 100,
      step: 1,
      valueKey: 'value',
      horizontal: true,
      locked: false,
    };

    const nextWidget: MachineUiSliderWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'slider',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addTextFieldWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiTextFieldWidget = {
      type: 'textField',
      id,
      tabId: activeTabId,
      x: 32,
      y: 96,
      w: 56,
      h: 13,
      skin: 'gui_states/input_box/normal',
      valueKey: 'text',
      inputType: 'string',
      locked: false,
    };

    const nextWidget: MachineUiTextFieldWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'textField',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addPlayerInventoryWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiPlayerInventoryWidget = {
      type: 'playerInventory',
      id,
      tabId: activeTabId,
      x: 32,
      y: 140,
      w: 162,
      h: 76,
      locked: false,
    };

    const nextWidget: MachineUiPlayerInventoryWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'playerInventory',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  addScrollContainerWidget: (preset) => {
    const prev: Snapshot = { doc: get().doc };
    const id = `w_${nanoid(10)}`;
    const activeTabId = get().doc.options?.activeTabId ?? get().doc.options?.activeBackground ?? 'A';

    const base: MachineUiScrollContainerWidget = {
      type: 'scroll_container',
      id,
      tabId: activeTabId,
      x: 32,
      y: 32,
      w: 160,
      h: 96,
      scrollX: false,
      scrollY: true,
      scrollSpeed: 30,
      cancelScrollEdge: true,
      locked: false,
      childrenIds: [],
    };

    const nextWidget: MachineUiScrollContainerWidget = {
      ...base,
      ...(preset ?? {}),
      type: 'scroll_container',
      id,
    };

    set((s) => {
      const widgets = s.doc.widgets ?? [];
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: [...widgets, nextWidget],
        },
        selection: {
          ...s.selection,
          mode: 'widgets',
          selectedWidgetId: id,
          selectedWidgetIds: [id],
          selectedGuideId: undefined,
        },
      };
    });
  },

  updateWidgetLive: (id, patch) => {
    set((s) => {
      const widgets = s.doc.widgets ?? [];
      const idx = widgets.findIndex((w) => w.id === id);
      if (idx < 0) return s;
      const nextWidgets = widgets.slice();

      const prevW = nextWidgets[idx] as any;
      const rawPatch = patch as any;
      const type = String(prevW?.type ?? '');

      const nextSkin = typeof rawPatch.skin === 'string' ? String(rawPatch.skin).trim() : undefined;
      const wantsSkinChange = Object.prototype.hasOwnProperty.call(rawPatch, 'skin');
      const hasExplicitSize = Object.prototype.hasOwnProperty.call(rawPatch, 'w') || Object.prototype.hasOwnProperty.call(rawPatch, 'h');

      let autoSizePatch: any = {};
      if (wantsSkinChange && !hasExplicitSize && nextSkin) {
        if (type === 'button') {
          const sizing = resolveGuiStatesButtonSizing(nextSkin);
          if (sizing && (!sizing.stretchX || !sizing.stretchY)) {
            autoSizePatch = { w: sizing.defaultW, h: sizing.defaultH };
          }
        } else if (type === 'toggle') {
          const sizing = resolveGuiStatesToggleSizing(nextSkin);
          if (sizing && (!sizing.stretchX || !sizing.stretchY)) {
            autoSizePatch = { w: sizing.defaultW, h: sizing.defaultH };
          }
        } else if (type === 'slider') {
          const sizing = resolveGuiStatesSliderSizing(nextSkin);
          if (sizing) {
            // Lock non-stretch axis to default.
            if (!sizing.stretchX) autoSizePatch.w = sizing.defaultW;
            if (!sizing.stretchY) autoSizePatch.h = sizing.defaultH;
          }
        } else if (type === 'textField') {
          const sizing = resolveGuiStatesTextFieldSizing(nextSkin);
          if (sizing) {
            if (!sizing.stretchX) autoSizePatch.w = sizing.defaultW;
            if (!sizing.stretchY) autoSizePatch.h = sizing.defaultH;
          }
        }
      }

      nextWidgets[idx] = { ...prevW, ...rawPatch, ...autoSizePatch } as any;
      return {
        ...s,
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
      };
    });
  },

  updateWidget: (id, patch) => {
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const widgets = s.doc.widgets ?? [];
      const idx = widgets.findIndex((w) => w.id === id);
      if (idx < 0) return s;
      const nextWidgets = widgets.slice();

      const prevW = nextWidgets[idx] as any;
      const rawPatch = patch as any;
      const type = String(prevW?.type ?? '');

      const nextSkin = typeof rawPatch.skin === 'string' ? String(rawPatch.skin).trim() : undefined;
      const wantsSkinChange = Object.prototype.hasOwnProperty.call(rawPatch, 'skin');
      const hasExplicitSize = Object.prototype.hasOwnProperty.call(rawPatch, 'w') || Object.prototype.hasOwnProperty.call(rawPatch, 'h');

      let autoSizePatch: any = {};
      if (wantsSkinChange && !hasExplicitSize && nextSkin) {
        if (type === 'button') {
          const sizing = resolveGuiStatesButtonSizing(nextSkin);
          if (sizing && (!sizing.stretchX || !sizing.stretchY)) {
            autoSizePatch = { w: sizing.defaultW, h: sizing.defaultH };
          }
        } else if (type === 'toggle') {
          const sizing = resolveGuiStatesToggleSizing(nextSkin);
          if (sizing && (!sizing.stretchX || !sizing.stretchY)) {
            autoSizePatch = { w: sizing.defaultW, h: sizing.defaultH };
          }
        } else if (type === 'slider') {
          const sizing = resolveGuiStatesSliderSizing(nextSkin);
          if (sizing) {
            if (!sizing.stretchX) autoSizePatch.w = sizing.defaultW;
            if (!sizing.stretchY) autoSizePatch.h = sizing.defaultH;
          }
        } else if (type === 'textField') {
          const sizing = resolveGuiStatesTextFieldSizing(nextSkin);
          if (sizing) {
            if (!sizing.stretchX) autoSizePatch.w = sizing.defaultW;
            if (!sizing.stretchY) autoSizePatch.h = sizing.defaultH;
          }
        }
      }

      nextWidgets[idx] = { ...prevW, ...rawPatch, ...autoSizePatch } as any;
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
      };
    });
  },

  updateWidgetsLive: (patchesById) => {
    const patches = patchesById ?? {};
    const ids = Object.keys(patches);
    if (ids.length === 0) return;
    set((s) => {
      const nextWidgets = (s.doc.widgets ?? []).map((w) => {
        const p = (patches as any)[w.id] as any;
        return p ? ({ ...w, ...p } as any) : w;
      });
      return {
        ...s,
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
      };
    });
  },

  updateWidgets: (patchesById) => {
    const patches = patchesById ?? {};
    const ids = Object.keys(patches);
    if (ids.length === 0) return;
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const nextWidgets = (s.doc.widgets ?? []).map((w) => {
        const p = (patches as any)[w.id] as any;
        return p ? ({ ...w, ...p } as any) : w;
      });
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
      };
    });
  },

  removeWidget: (id) => {
    const widgetId = String(id ?? '').trim();
    if (!widgetId) return;
    const prev: Snapshot = { doc: get().doc };
    set((s) => {
      const widgets = s.doc.widgets ?? [];
      if (!widgets.some((w) => w.id === widgetId)) return s;

      // Remove the widget itself.
      let nextWidgets = widgets.filter((w) => w.id !== widgetId);

      // If it was referenced by any container (Panel / ScrollContainer), clean up childrenIds.
      nextWidgets = nextWidgets
        .map((w) => {
          const t = (w as any).type;
          if (t !== 'panel' && t !== 'scroll_container') return w;
          const cid = Array.isArray((w as any).childrenIds) ? ((w as any).childrenIds as any[]) : [];
          const filtered = cid
            .map((x) => String(x ?? '').trim())
            .filter((x) => x.length > 0 && x !== widgetId);
          // If no change, keep as-is to reduce churn.
          if (filtered.length === cid.length) return w;
          return { ...(w as any), childrenIds: filtered } as any;
        })
        // Optional: drop now-empty panels to avoid confusing invisible groups.
        .filter((w) => {
          const t = (w as any).type;
          if (t !== 'panel' && t !== 'scroll_container') return true;
          const cid = Array.isArray((w as any).childrenIds) ? ((w as any).childrenIds as any[]) : [];
          return cid.length > 0;
        });

      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          widgets: nextWidgets,
        },
        selection: {
          ...s.selection,
          selectedWidgetIds: (s.selection.selectedWidgetIds ?? []).filter((x) => x !== widgetId),
          selectedWidgetId: (() => {
            if (s.selection.selectedWidgetId !== widgetId) return s.selection.selectedWidgetId;
            const remaining = (s.selection.selectedWidgetIds ?? []).filter((x) => x !== widgetId);
            return remaining[0];
          })(),
        },
      };
    });
  },

  importFromJson: (raw) => {
    const parsed = MachineUiDocSchema.safeParse(raw);
    if (!parsed.success) {
      // keep it simple for now; UI layer can show toast later
      console.error(parsed.error);
      return;
    }
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: parsed.data,
      selection: {
        ...s.selection,
        selectedGuideId: undefined,
        selectedWidgetId: undefined,
        selectedWidgetIds: [],
      },
    }));
  },

  saveToLocal: () => {
    const { doc } = get();
    saveToLocalStorage(LS_KEY, doc);
  },

  loadFromLocal: () => {
    const raw = loadFromLocalStorage(LS_KEY);
    if (!raw) return;
    const parsed = MachineUiDocSchema.safeParse(raw);
    if (!parsed.success) {
      console.error(parsed.error);
      return;
    }
    const prev: Snapshot = { doc: get().doc };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: parsed.data,
      selection: {
        ...s.selection,
        selectedGuideId: undefined,
        selectedWidgetId: undefined,
        selectedWidgetIds: [],
      },
    }));
  },
}));

export function importMachineUiDoc(raw: unknown): MachineUiDoc | undefined {
  const parsed = MachineUiDocSchema.safeParse(raw);
  if (!parsed.success) {
    console.error(parsed.error);
    return undefined;
  }
  return parsed.data;
}
