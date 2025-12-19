import {create} from 'zustand';
import {createEmptyMachineUiDoc, MachineUiBackgroundKey, MachineUiDoc, MachineUiDocSchema} from '../model/ir';

type Snapshot = {
  doc: MachineUiDoc;
};

export type ViewState = {
  scale: number;
  offsetX: number;
  offsetY: number;
};

const HISTORY_LIMIT = 100;

type MachineUiState = {
  doc: MachineUiDoc;

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

  setGridSize: (gridSize: number) => void;
};

export const useMachineUiStore = create<MachineUiState>((set, get) => ({
  doc: createEmptyMachineUiDoc(),

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
        },
      },
    }));
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
    const g = Math.max(2, Math.floor(Number(gridSize) || 2));
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
}));

export function importMachineUiDoc(raw: unknown): MachineUiDoc | undefined {
  const parsed = MachineUiDocSchema.safeParse(raw);
  if (!parsed.success) {
    console.error(parsed.error);
    return undefined;
  }
  return parsed.data;
}
