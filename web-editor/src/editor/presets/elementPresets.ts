import type {EditorElement} from '../model/ir';

export type ElementPreset = {
  key: string;
  label: string;
  // Element fields without id (id is generated at insertion time)
  element: Omit<EditorElement, 'id'>;
};

// Keep this intentionally small and boring: presets are just defaults.
// If/when we add richer asset configs, we can extend `data` per renderer.
export const ELEMENT_PRESETS: ElementPreset[] = [
  {
    key: 'slot',
    label: '物品槽 (slot)',
    element: {
      type: 'slot',
      typeId: 'prototypemachinery:item',
      role: 'INPUT',
      x: 10,
      y: 10,
      w: 18,
      h: 18,
      variantId: 'prototypemachinery:slot/1x1',
      data: {},
    },
  },
  {
    key: 'tank',
    label: '流体槽 (tank)',
    element: {
      type: 'tank',
      typeId: 'prototypemachinery:fluid',
      role: 'INPUT',
      x: 10,
      y: 10,
      w: 18,
      h: 54,
      variantId: 'prototypemachinery:tank/1x3',
      data: {},
    },
  },
  {
    key: 'energy',
    label: '能量条 (energy)',
    element: {
      type: 'energy',
      typeId: 'prototypemachinery:energy',
      role: 'INPUT',
      x: 10,
      y: 10,
      w: 6,
      h: 54,
      variantId: 'prototypemachinery:energy/1x3',
      data: {
        // Example placement data; renderer may consume this.
        energyLedYOffset: 0,
      },
    },
  },
  {
    key: 'text',
    label: '文本 (text)',
    element: {
      type: 'text',
      x: 10,
      y: 10,
      w: 80,
      h: 12,
      variantId: undefined,
      data: {
        text: 'Hello',
      },
    },
  },
  {
    key: 'decorator',
    label: '装饰层 (decorator)',
    element: {
      type: 'decorator',
      x: 10,
      y: 10,
      w: 16,
      h: 16,
      variantId: undefined,
      data: {},
    },
  },
  {
    key: 'custom',
    label: '自定义 (custom)',
    element: {
      type: 'custom',
      x: 10,
      y: 10,
      w: 24,
      h: 24,
      variantId: undefined,
      data: {},
    },
  },
];

export function getPreset(key: string): ElementPreset | undefined {
  return ELEMENT_PRESETS.find((p) => p.key === key);
}
