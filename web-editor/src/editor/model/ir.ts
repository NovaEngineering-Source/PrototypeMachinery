import {z} from 'zod';

export type ElementType =
  | 'slot'
  | 'tank'
  | 'energy'
  | 'decorator'
  | 'text'
  | 'custom';

export const EditorElementSchema = z.object({
  id: z.string(),
  type: z.string(),
  /**
   * Optional explicit binding to a concrete PM JEI node id.
   * If present, exporter may prefer `placeNode*` over `placeFirst*`.
   */
  nodeId: z.string().optional(),

  /**
   * Optional explicit requirement type id, e.g. "prototypemachinery:item".
   * If omitted, exporter may infer it from `type`.
   */
  typeId: z.string().optional(),

  /**
   * Optional requirement role string understood by LayoutBuilder, e.g.:
   * INPUT / OUTPUT / INPUT_PER_TICK / OUTPUT_PER_TICK / OTHER / ANY.
   */
  role: z.string().optional(),
  x: z.number().int(),
  y: z.number().int(),
  w: z.number().int().min(1),
  h: z.number().int().min(1),
  variantId: z.string().optional(),
  data: z.record(z.string(), z.any()).optional(),
});

export type EditorElement = z.infer<typeof EditorElementSchema>;

export const EditorDocSchema = z.object({
  schemaVersion: z.literal(1),
  name: z.string(),
  options: z
    .object({
      /**
       * If enabled, role="INPUT" matches INPUT + INPUT_PER_TICK; same for OUTPUT.
       * Mirrors LayoutBuilder.mergePerTickRoles().
       */
      mergePerTickRoles: z.boolean().optional(),

      /**
       * Editor-only: grid size in pixels for snapping and grid rendering.
       * Must be >= 2 to avoid overly dense grids.
       */
      gridSize: z.number().int().min(2).optional(),

      /**
       * Optional JEI panel background override.
       * Mirrors LayoutBuilder.setBackgroundNineSlice().
       */
      backgroundNineSlice: z
        .object({
          /**
           * Either:
           * - a path relative to `prototypemachinery:textures/gui/jei_recipeicons/` (e.g. "jei_base.png")
           * - or a full resource location string (e.g. "mymod:textures/gui/foo.png")
           */
          texture: z.string().optional(),
          /** Border thickness in pixels (default 2). */
          borderPx: z.number().int().min(0).optional(),
          /** If true, fill center with sampled solid color instead of stretching. */
          fillCenter: z.boolean().optional(),
        })
        .optional(),
    })
    .optional(),
  canvas: z.object({
    width: z.number().int().min(1),
    height: z.number().int().min(1),
  }),
  elements: z.array(EditorElementSchema),
});

export type EditorDoc = z.infer<typeof EditorDocSchema>;

export function createEmptyDoc(): EditorDoc {
  return {
    schemaVersion: 1,
    name: 'Untitled',
    options: {
      mergePerTickRoles: false,
      gridSize: 8,
      backgroundNineSlice: undefined,
    },
    canvas: {
      width: 176,
      height: 96,
    },
    elements: [],
  };
}
