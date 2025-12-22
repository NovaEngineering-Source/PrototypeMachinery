import {z} from 'zod';

export type MachineUiBackgroundKey = 'A' | 'B';

export const MachineUiTabSchema = z.object({
  /** Stable identifier. Use short ids like "A", "B", "extra". */
  id: z.string().min(1),
  /** Optional display label for editor UI. */
  label: z.string().optional(),
  /** Background texture path relative to PM textures root (e.g. "gui/gui_controller_a.png"). */
  texturePath: z.string().optional(),
});

export type MachineUiTab = z.infer<typeof MachineUiTabSchema>;

export const MachineUiTextAlignSchema = z.enum(['left', 'center', 'right']);
export type MachineUiTextAlign = z.infer<typeof MachineUiTextAlignSchema>;

export const MachineUiTextWidgetSchema = z.object({
  type: z.literal('text'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  text: z.string(),
  color: z.string().optional(),
  fontSize: z.number().int().min(1).optional(),
  align: MachineUiTextAlignSchema.optional(),
  /** Runtime: draw shadow behind text. */
  shadow: z.boolean().optional(),
  /** If true, render but do not allow interaction (future). */
  locked: z.boolean().optional(),
});

export type MachineUiTextWidget = z.infer<typeof MachineUiTextWidgetSchema>;

export const MachineUiSlotGridWidgetSchema = z.object({
  type: z.literal('slotGrid'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  // Derived from rows/cols/slotSize/gap but stored for simpler drag bounds.
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  cols: z.number().int().min(1),
  rows: z.number().int().min(1),
  slotSize: z.number().int().min(1).optional(),
  gap: z.number().int().min(0).optional(),
  /** Texture path relative to PM textures root (e.g. "gui/slot.png"). */
  slotTexturePath: z.string().optional(),

  /** Runtime: item slot group key. */
  slotKey: z.string().optional(),
  /** Runtime: starting slot index in the group. */
  startIndex: z.number().int().min(0).optional(),
  /** Runtime: whether items can be inserted. */
  canInsert: z.boolean().optional(),
  /** Runtime: whether items can be extracted. */
  canExtract: z.boolean().optional(),

  locked: z.boolean().optional(),
});

export type MachineUiSlotGridWidget = z.infer<typeof MachineUiSlotGridWidgetSchema>;

export const MachineUiProgressDirectionSchema = z.enum(['right', 'left', 'down', 'up']);
export type MachineUiProgressDirection = z.infer<typeof MachineUiProgressDirectionSchema>;

export const MachineUiProgressBarWidgetSchema = z.object({
  type: z.literal('progress'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  /** Editor preview only: 0..1 */
  progress: z.number().min(0).max(1).optional(),
  direction: MachineUiProgressDirectionSchema.optional(),
  /** Optional base texture rendered behind the progress fill. */
  baseTexturePath: z.string().optional(),
  /** Optional run/overlay texture clipped by progress. */
  runTexturePath: z.string().optional(),

  /** Runtime: binding key or expression (expects 0..1). */
  progressKey: z.string().optional(),
  /** Runtime: tooltip template (String.format style). */
  tooltipTemplate: z.string().optional(),

  fillColor: z.string().optional(),
  bgColor: z.string().optional(),
  locked: z.boolean().optional(),
});

export type MachineUiProgressBarWidget = z.infer<typeof MachineUiProgressBarWidgetSchema>;

export const MachineUiImageWidgetSchema = z.object({
  type: z.literal('image'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  /** Texture path relative to PM textures root (e.g. "gui/foo.png"), or a namespaced form. */
  texturePath: z.string().optional(),
  locked: z.boolean().optional(),
});

export type MachineUiImageWidget = z.infer<typeof MachineUiImageWidgetSchema>;

export const MachineUiButtonWidgetSchema = z.object({
  type: z.literal('button'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  /** Runtime: visual skin id (e.g. gui_states templates). */
  skin: z.string().optional(),
  /** Optional label text. */
  text: z.string().optional(),
  /** Runtime: action key for PacketMachineAction / UIActionRegistry. */
  actionKey: z.string().optional(),
  locked: z.boolean().optional(),
});

export type MachineUiButtonWidget = z.infer<typeof MachineUiButtonWidgetSchema>;

export const MachineUiToggleWidgetSchema = z.object({
  type: z.literal('toggle'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  /** Runtime: visual skin id (e.g. gui_states/switch/{normal|shadow}). */
  skin: z.string().optional(),
  /** Runtime: bool binding key. */
  stateKey: z.string().optional(),
  textOn: z.string().optional(),
  textOff: z.string().optional(),
  /** Optional textures; may be editor-form paths ("gui/foo.png") or namespaced form. */
  textureOn: z.string().optional(),
  textureOff: z.string().optional(),
  locked: z.boolean().optional(),
});

export type MachineUiToggleWidget = z.infer<typeof MachineUiToggleWidgetSchema>;

export const MachineUiSliderWidgetSchema = z.object({
  type: z.literal('slider'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  /** Runtime: visual skin id (e.g. gui_states/slider/...). */
  skin: z.string().optional(),
  min: z.number().optional(),
  max: z.number().optional(),
  step: z.number().optional(),
  /** Runtime: number binding key. */
  valueKey: z.string().optional(),
  horizontal: z.boolean().optional(),
  locked: z.boolean().optional(),
});

export type MachineUiSliderWidget = z.infer<typeof MachineUiSliderWidgetSchema>;

export const MachineUiTextFieldInputTypeSchema = z.enum(['string', 'long']);
export type MachineUiTextFieldInputType = z.infer<typeof MachineUiTextFieldInputTypeSchema>;

export const MachineUiTextFieldWidgetSchema = z.object({
  type: z.literal('textField'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),

  /** Runtime: string binding key for text value. */
  valueKey: z.string().optional(),
  /** Runtime: "string" (default) | "long" (numbers only). */
  inputType: MachineUiTextFieldInputTypeSchema.optional(),
  /** Runtime: clamp range for numeric input (only when inputType == "long"). */
  minLong: z.number().int().optional(),
  maxLong: z.number().int().optional(),
  /** Runtime: visual skin id (e.g. gui_states/input_box/...). */
  skin: z.string().optional(),

  locked: z.boolean().optional(),
});

export type MachineUiTextFieldWidget = z.infer<typeof MachineUiTextFieldWidgetSchema>;

/**
 * Panel container (editor grouping).
 *
 * Notes:
 * - WebEditor 内部仍保持 widgets 列表扁平化；children 用 childrenIds 引用。
 * - 导出 runtime JSON / builders 时会把 childrenIds 展开为真正的 children 数组，并把子组件坐标转换为相对坐标。
 */
export const MachineUiPanelWidgetSchema = z.object({
  type: z.literal('panel'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),

  /** Editor-only: child widget ids (stored in doc.widgets separately). */
  childrenIds: z.array(z.string()).optional(),

  /** If true, render but do not allow interaction (future). */
  locked: z.boolean().optional(),
});

export type MachineUiPanelWidget = z.infer<typeof MachineUiPanelWidgetSchema>;

/**
 * Scrollable container.
 * 可滚动容器：提供裁剪（viewport）+ 滚动条，并支持嵌套 children。
 *
 * Notes:
 * - WebEditor 内部仍保持 widgets 列表扁平化；children 用 childrenIds 引用。
 * - 导出 runtime JSON / builders 时会把 childrenIds 展开为真正的 children 数组，并把子组件坐标转换为相对坐标。
 */
export const MachineUiScrollContainerWidgetSchema = z.object({
  type: z.literal('scroll_container'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),

  /** Enable horizontal scrolling. */
  scrollX: z.boolean().optional(),
  /** Enable vertical scrolling. */
  scrollY: z.boolean().optional(),

  scrollBarOnStartX: z.boolean().optional(),
  scrollBarOnStartY: z.boolean().optional(),

  scrollBarThicknessX: z.number().int().optional(),
  scrollBarThicknessY: z.number().int().optional(),

  scrollSpeed: z.number().int().min(1).optional(),
  cancelScrollEdge: z.boolean().optional(),

  /** Editor-only: child widget ids (stored in doc.widgets separately). */
  childrenIds: z.array(z.string()).optional(),

  /** If true, render but do not allow interaction (future). */
  locked: z.boolean().optional(),
});

export type MachineUiScrollContainerWidget = z.infer<typeof MachineUiScrollContainerWidgetSchema>;

export const MachineUiPlayerInventoryWidgetSchema = z.object({
  type: z.literal('playerInventory'),
  id: z.string(),
  /** If omitted, the widget is considered global (visible on all tabs). */
  tabId: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control visibility. */
  visibleIf: z.string().optional(),
  /** Runtime: condition (bool binding key or expression) to control interactivity. */
  enabledIf: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  locked: z.boolean().optional(),
});

export type MachineUiPlayerInventoryWidget = z.infer<typeof MachineUiPlayerInventoryWidgetSchema>;

export const MachineUiWidgetSchema = z.discriminatedUnion('type', [
  MachineUiTextWidgetSchema,
  MachineUiSlotGridWidgetSchema,
  MachineUiProgressBarWidgetSchema,
  MachineUiImageWidgetSchema,
  MachineUiButtonWidgetSchema,
  MachineUiToggleWidgetSchema,
  MachineUiSliderWidgetSchema,
  MachineUiTextFieldWidgetSchema,
  MachineUiPanelWidgetSchema,
  MachineUiScrollContainerWidgetSchema,
  MachineUiPlayerInventoryWidgetSchema,
]);
export type MachineUiWidget = z.infer<typeof MachineUiWidgetSchema>;

export const MachineUiGuideSchema = z.object({
  id: z.string(),
  label: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  /** Optional stroke color (CSS hex). */
  color: z.string().optional(),
  /** If true, render but do not allow interaction (future). */
  locked: z.boolean().optional(),
});

export type MachineUiGuide = z.infer<typeof MachineUiGuideSchema>;

export const MachineUiDocSchema = z.object({
  schemaVersion: z.literal(1),
  name: z.string(),
  canvas: z.object({
    width: z.number().int().min(1),
    height: z.number().int().min(1),
  }),
  /** Editor-only layout guides (not exported). */
  guides: z.array(MachineUiGuideSchema).optional(),
  options: z
    .object({
      /** Which background to preview (matches DefaultMachineUI Tab A/B). */
      activeBackground: z.enum(['A', 'B']).optional(),

      /** Preferred tab id (supports custom tabs). If omitted, falls back to activeBackground (A/B). */
      activeTabId: z.string().optional(),

      /** Custom tabs. If omitted, the editor will assume A/B tabs from backgroundA/backgroundB. */
      tabs: z.array(MachineUiTabSchema).optional(),

      /** Texture paths are relative to PM textures root (e.g. "gui/gui_controller_a.png"). */
      backgroundA: z
        .object({
          texturePath: z.string().optional(),
        })
        .optional(),
      backgroundB: z
        .object({
          texturePath: z.string().optional(),
        })
        .optional(),

      /** Editor-only: grid size in pixels for snapping and grid rendering. */
      gridSize: z.number().int().min(1).optional(),

      /** Editor-only: render layout guides overlay. */
      showGuides: z.boolean().optional(),
    })
    .optional(),

  widgets: z.array(MachineUiWidgetSchema).optional(),
});

export type MachineUiDoc = z.infer<typeof MachineUiDocSchema>;

export function createEmptyMachineUiDoc(): MachineUiDoc {
  return {
    schemaVersion: 1,
    name: 'Untitled Machine UI',
    canvas: {
      width: 384,
      height: 256,
    },
    guides: [
      // Based on DefaultMachineUI.kt (rough alignment helpers)
      { id: 'tab_bar', label: 'Tab Bar', x: 0, y: 0, w: 22, h: 80, color: '#74c0fc', locked: true },
      { id: 'recipe_list', label: 'Recipe List', x: 27, y: 8, w: 89, h: 238, color: '#ffd43b', locked: true },
      // Content area excluding left tab bar (approx)
      { id: 'content_area', label: 'Content', x: 22, y: 0, w: 384 - 22, h: 256, color: '#69db7c', locked: true },
    ],
    options: {
      activeBackground: 'A',
      activeTabId: 'A',
      tabs: [
        { id: 'A', label: 'Main', texturePath: 'gui/gui_controller_a.png' },
        { id: 'B', label: 'Extension', texturePath: 'gui/gui_controller_b.png' },
      ],
      backgroundA: { texturePath: 'gui/gui_controller_a.png' },
      backgroundB: { texturePath: 'gui/gui_controller_b.png' },
      gridSize: 8,
      showGuides: true,
    },
    widgets: [],
  };
}
