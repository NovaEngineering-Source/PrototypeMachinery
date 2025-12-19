import {create} from 'zustand';
import {nanoid} from './nanoid';
import {createEmptyDoc, EditorDoc, EditorDocSchema, EditorElement} from '../model/ir';
import {loadFromLocalStorage, saveToLocalStorage} from '../io/storage';
import {getPreset} from '../presets/elementPresets';

type EditorSnapshot = {
  doc: EditorDoc;
  selectionId?: string;
};

export type ViewState = {
  scale: number;
  offsetX: number;
  offsetY: number;
};

const HISTORY_LIMIT = 100;

type EditorState = {
  doc: EditorDoc;
  selectionId?: string;

  // 视图状态（不进入 undo/redo）：缩放/平移只影响编辑体验，不应成为“可撤销内容”。
  view: ViewState;
  setView: (next: ViewState | ((prev: ViewState) => ViewState)) => void;

  historyPast: EditorSnapshot[];
  historyFuture: EditorSnapshot[];
  canUndo: () => boolean;
  canRedo: () => boolean;
  undo: () => void;
  redo: () => void;

  setDocName: (name: string) => void;
  setCanvasSize: (width: number, height: number) => void;
  setMergePerTickRoles: (enabled: boolean) => void;
  setGridSize: (gridSize: number) => void;
  setBackgroundNineSliceEnabled: (enabled: boolean) => void;
  patchBackgroundNineSlice: (patch: { texture?: string; borderPx?: number; fillCenter?: boolean }) => void;

  select: (id?: string) => void;
  addElement: (presetKey?: string) => void;
  removeSelected: () => void;
  updateElement: (id: string, patch: Partial<EditorElement>) => void;
  updateElementLive: (id: string, patch: Partial<EditorElement>) => void;

  importFromJson: (raw: unknown) => void;
  saveToLocal: () => void;
  loadFromLocal: () => void;
};

const LS_KEY = 'pm.webEditor.doc.v1';

export const useEditorStore = create<EditorState>((set, get) => ({
  doc: createEmptyDoc(),
  selectionId: undefined,

  view: {
    scale: 1,
    offsetX: 0,
    offsetY: 0,
  },
  setView: (next) =>
    set((s) => ({
      view: typeof next === 'function' ? (next as (prev: ViewState) => ViewState)(s.view) : next,
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
      const cur: EditorSnapshot = { doc: s.doc, selectionId: s.selectionId };
      return {
        ...s,
        doc: prev.doc,
        selectionId: prev.selectionId,
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
      const cur: EditorSnapshot = { doc: s.doc, selectionId: s.selectionId };
      return {
        ...s,
        doc: next.doc,
        selectionId: next.selectionId,
        historyPast: [...s.historyPast, cur].slice(-HISTORY_LIMIT),
        historyFuture: newFuture,
      };
    });
  },

  setDocName: (name) => {
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: { ...s.doc, name },
    }));
  },

  setCanvasSize: (width, height) => {
    const w = Math.max(1, Math.floor(width));
    const h = Math.max(1, Math.floor(height));
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: { ...s.doc, canvas: { width: w, height: h } },
    }));
  },

  setMergePerTickRoles: (enabled) => {
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        options: {
          ...s.doc.options,
          mergePerTickRoles: Boolean(enabled),
        },
      },
    }));
  },

  setGridSize: (gridSize) => {
    const g = Math.max(2, Math.floor(Number(gridSize) || 2));
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
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

  setBackgroundNineSliceEnabled: (enabled) => {
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => {
      const on = Boolean(enabled);
      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          options: {
            ...s.doc.options,
            backgroundNineSlice: on
              ? {
                  texture: s.doc.options?.backgroundNineSlice?.texture,
                  borderPx: typeof s.doc.options?.backgroundNineSlice?.borderPx === 'number' ? s.doc.options!.backgroundNineSlice!.borderPx : 2,
                  fillCenter: Boolean(s.doc.options?.backgroundNineSlice?.fillCenter),
                }
              : undefined,
          },
        },
      };
    });
  },

  patchBackgroundNineSlice: (patch) => {
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => {
      const cur = s.doc.options?.backgroundNineSlice ?? {};
      const next: { texture?: string; borderPx?: number; fillCenter?: boolean } = {
        ...cur,
        borderPx: typeof cur.borderPx === 'number' ? cur.borderPx : 2,
        fillCenter: Boolean(cur.fillCenter),
      };

      if ('texture' in patch) {
        const t = patch.texture;
        if (typeof t === 'string') {
          const trimmed = t.trim();
          if (trimmed) next.texture = trimmed;
          else delete next.texture;
        } else {
          delete next.texture;
        }
      }

      if ('borderPx' in patch) {
        const n = patch.borderPx;
        if (typeof n === 'number' && Number.isFinite(n)) next.borderPx = Math.max(0, Math.floor(n));
      }

      if ('fillCenter' in patch) {
        next.fillCenter = Boolean(patch.fillCenter);
      }

      return {
        historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
        historyFuture: [],
        doc: {
          ...s.doc,
          options: {
            ...s.doc.options,
            backgroundNineSlice: next,
          },
        },
      };
    });
  },

  select: (id) => set(() => ({ selectionId: id })),

  addElement: (presetKey) => {
    const id = nanoid();
    const preset = presetKey ? getPreset(presetKey) : undefined;
    const base = preset?.element ?? {
      type: 'custom',
      x: 10,
      y: 10,
      w: 24,
      h: 24,
      variantId: undefined,
      data: {},
    };

    const el: EditorElement = {
      id,
      ...base,
      // defensive: ensure min size even if preset is edited badly
      w: Math.max(1, Math.floor(base.w)),
      h: Math.max(1, Math.floor(base.h)),
      x: Math.floor(base.x),
      y: Math.floor(base.y),
    };
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: { ...s.doc, elements: [...s.doc.elements, el] },
      selectionId: id,
    }));
  },

  removeSelected: () => {
    const id = get().selectionId;
    if (!id) return;
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: { ...s.doc, elements: s.doc.elements.filter((e) => e.id !== id) },
      selectionId: undefined,
    }));
  },

  updateElement: (id, patch) => {
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: {
        ...s.doc,
        elements: s.doc.elements.map((e) => (e.id === id ? { ...e, ...patch } : e)),
      },
    }));
  },

  // 实时更新（不写入历史）：用于拖拽过程中避免生成大量 undo step。
  updateElementLive: (id, patch) => {
    set((s) => ({
      doc: {
        ...s.doc,
        elements: s.doc.elements.map((e) => (e.id === id ? { ...e, ...patch } : e)),
      },
    }));
  },

  importFromJson: (raw) => {
    const parsed = EditorDocSchema.safeParse(raw);
    if (!parsed.success) {
      // keep it simple for now; UI layer can show toast later
      console.error(parsed.error);
      return;
    }
    const prev: EditorSnapshot = { doc: get().doc, selectionId: get().selectionId };
    set((s) => ({
      historyPast: [...s.historyPast, prev].slice(-HISTORY_LIMIT),
      historyFuture: [],
      doc: parsed.data,
      selectionId: undefined,
    }));
  },

  saveToLocal: () => {
    const { doc } = get();
    saveToLocalStorage(LS_KEY, doc);
  },

  loadFromLocal: () => {
    const raw = loadFromLocalStorage(LS_KEY);
    if (!raw) return;
    get().importFromJson(raw);
  },
}));
