import {z} from 'zod';

export type MachineUiBackgroundKey = 'A' | 'B';

export const MachineUiDocSchema = z.object({
  schemaVersion: z.literal(1),
  name: z.string(),
  canvas: z.object({
    width: z.number().int().min(1),
    height: z.number().int().min(1),
  }),
  options: z
    .object({
      /** Which background to preview (matches DefaultMachineUI Tab A/B). */
      activeBackground: z.enum(['A', 'B']).optional(),

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
      gridSize: z.number().int().min(2).optional(),
    })
    .optional(),

  // Future:
  // widgets: z.array(WidgetSchema)
  widgets: z.array(z.any()).optional(),
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
    options: {
      activeBackground: 'A',
      backgroundA: { texturePath: 'gui/gui_controller_a.png' },
      backgroundB: { texturePath: 'gui/gui_controller_b.png' },
      gridSize: 8,
    },
    widgets: [],
  };
}
