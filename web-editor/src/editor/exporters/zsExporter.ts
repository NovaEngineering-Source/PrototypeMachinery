import type {EditorDoc} from '../model/ir';
import {normalizeJeiBackgroundTextureArgForExport} from '../components/canvas/elementPreview';

type ExportableRole =
  | 'INPUT'
  | 'OUTPUT'
  | 'INPUT_PER_TICK'
  | 'OUTPUT_PER_TICK'
  | 'OTHER'
  | 'ANY';

function normalizeRole(raw: unknown): ExportableRole | undefined {
  const s = typeof raw === 'string' ? raw.trim() : '';
  if (!s) return undefined;
  const upper = s.toUpperCase();
  if (upper === 'ANY' || upper === '*') return 'ANY';
  if (
    upper === 'INPUT' ||
    upper === 'OUTPUT' ||
    upper === 'INPUT_PER_TICK' ||
    upper === 'INPUTPERTICK' ||
    upper === 'OUTPUT_PER_TICK' ||
    upper === 'OUTPUTPERTICK' ||
    upper === 'OTHER'
  ) {
    // keep original spellings for _PER_TICK variants
    if (upper === 'INPUTPERTICK') return 'INPUT_PER_TICK';
    if (upper === 'OUTPUTPERTICK') return 'OUTPUT_PER_TICK';
    return upper as ExportableRole;
  }
  return undefined;
}

function inferTypeId(elementType: string): string | undefined {
  switch (elementType) {
    case 'slot':
      return 'prototypemachinery:item';
    case 'tank':
      return 'prototypemachinery:fluid';
    case 'energy':
      return 'prototypemachinery:energy';
    case 'parallelism':
      return 'prototypemachinery:parallelism';
    default:
      return undefined;
  }
}

function inferDefaultVariantId(elementType: string): string | undefined {
  switch (elementType) {
    case 'slot':
      return 'prototypemachinery:slot/1x1';
    case 'tank':
      return 'prototypemachinery:tank/1x3';
    case 'energy':
      return 'prototypemachinery:energy/1x3';
    default:
      return undefined;
  }
}

function toJavaMapLiteral(data: unknown): string {
  if (!data || typeof data !== 'object') return 'null';
  const obj = data as Record<string, unknown>;
  const keys = Object.keys(obj);
  if (keys.length === 0) return 'null';
  // CraftTweaker accepts map literals like {"energyLedYOffset": -1}
  // JSON is close enough for our limited set of primitives.
  return JSON.stringify(obj);

}

function decoratorPathFromId(decoratorId: string): string {
  const s = decoratorId.trim();
  const i = s.indexOf(':');
  return i >= 0 ? s.slice(i + 1) : s;
}

function augmentDecoratorDataForExport(decoratorId: string, el: { w: number; h: number; data?: unknown }): unknown {
  const base = el.data && typeof el.data === 'object' ? { ...(el.data as Record<string, unknown>) } : {};
  const path = decoratorPathFromId(decoratorId);

  // Some decorators read width/height from data (not from the placement rule).
  if (path === 'decorator/progress' || path === 'decorator/recipe_duration') {
    if (base.width == null) base.width = el.w;
    if (base.height == null) base.height = el.h;
  }

  return base;
}

/**
 * Draft exporter.
 *
 * 目标：把 IR 转成 CraftTweaker ZenScript（PMJEI LayoutBuilder）脚本。
 *
 * 说明：
 * - 目前只是框架 + 可读的输出，后续会补齐：role/节点绑定、decorator、fixed slot 等。
 * - 对于 placement data：会输出 *WithVariantAndData 形式，便于覆盖 renderer-specific 参数。
 */
export function exportZenScript(doc: EditorDoc): string {
  const lines: string[] = [];

  lines.push('#loader crafttweaker reloadable');
  lines.push('');
  lines.push('import mods.prototypemachinery.jei.PMJEI;');
  lines.push('import mods.prototypemachinery.jei.LayoutRegistry;');
  lines.push('');
  lines.push('// TODO: 填写 machine type id');
  lines.push('val MACHINE_ID = "prototypemachinery:example_recipe_processor";');
  lines.push('');
  const mergePerTickRoles = Boolean(doc.options?.mergePerTickRoles);
  lines.push(`val layout = PMJEI.createLayoutSized(${doc.canvas.width}, ${doc.canvas.height})${mergePerTickRoles ? '.mergePerTickRoles(true)' : ''}`);

  const bg = doc.options?.backgroundNineSlice;
  if (bg) {
    const textureArg = normalizeJeiBackgroundTextureArgForExport(bg.texture);
    const borderPx = typeof bg.borderPx === 'number' && Number.isFinite(bg.borderPx) ? Math.max(0, Math.floor(bg.borderPx)) : 2;
    const fillCenter = Boolean(bg.fillCenter);

    if (borderPx === 2 && fillCenter === false) {
      lines.push(`    .setBackgroundNineSlice(${JSON.stringify(textureArg)})`);
    } else {
      lines.push(`    .setBackgroundNineSlice(${JSON.stringify(textureArg)}, ${borderPx}, ${fillCenter ? 'true' : 'false'})`);
    }
  }

  const elements = [...doc.elements].sort((a, b) => a.y - b.y || a.x - b.x || a.id.localeCompare(b.id));

  if (elements.length === 0) {
    lines.push('    // (empty)');
  } else {
    for (const el of elements) {
      // Decorators are not requirements; they are placed by explicit decoratorId.
      if (el.type === 'decorator') {
        const decoratorId = typeof el.variantId === 'string' && el.variantId.trim().length > 0 ? el.variantId.trim() : undefined;
        const dataLit = decoratorId ? toJavaMapLiteral(augmentDecoratorDataForExport(decoratorId, el)) : toJavaMapLiteral(el.data);

        if (!decoratorId) {
          lines.push(`    // TODO: decorator missing decoratorId (use variantId), element=${JSON.stringify(el.id)}`);
          continue;
        }

        if (dataLit !== 'null') {
          lines.push(`    .addDecoratorWithData(${JSON.stringify(decoratorId)}, ${el.x}, ${el.y}, ${dataLit})`);
        } else {
          lines.push(`    .addDecorator(${JSON.stringify(decoratorId)}, ${el.x}, ${el.y})`);
        }
        continue;
      }

      const explicitNodeId = typeof el.nodeId === 'string' && el.nodeId.trim().length > 0 ? el.nodeId.trim() : undefined;
      const role = normalizeRole(el.role);
      const roleArg = !role || role === 'ANY' ? 'null' : JSON.stringify(role);

      const typeId = (typeof el.typeId === 'string' && el.typeId.trim().length > 0 ? el.typeId.trim() : undefined) ?? inferTypeId(el.type);
      const defaultVariantId = inferDefaultVariantId(el.type);
      const variantId = (typeof el.variantId === 'string' && el.variantId.trim().length > 0 ? el.variantId.trim() : undefined) ?? defaultVariantId;
      const dataLit = toJavaMapLiteral(el.data);

      // If nodeId is explicitly set, we treat this as a hard binding.
      if (explicitNodeId) {
        if (dataLit !== 'null') {
          if (!variantId) {
            lines.push(`    // NOTE: data ignored (missing variantId), element=${JSON.stringify(el.id)}`);
            lines.push(`    .placeNode(${JSON.stringify(explicitNodeId)}, ${el.x}, ${el.y})`);
          } else {
            lines.push(
              `    .placeNodeWithVariantAndData(${JSON.stringify(explicitNodeId)}, ${el.x}, ${el.y}, ${JSON.stringify(variantId)}, ${dataLit})`
            );
          }
        } else if (variantId) {
          lines.push(`    .placeNodeWithVariant(${JSON.stringify(explicitNodeId)}, ${el.x}, ${el.y}, ${JSON.stringify(variantId)})`);
        } else {
          lines.push(`    .placeNode(${JSON.stringify(explicitNodeId)}, ${el.x}, ${el.y})`);
        }
        continue;
      }

      // Otherwise we use typeId+role based matching.
      if (!typeId) {
        lines.push(`    // TODO: unsupported element type=${JSON.stringify(el.type)} (id=${JSON.stringify(el.id)})`);
        continue;
      }

      if (dataLit !== 'null') {
        if (!variantId) {
          lines.push(`    // NOTE: data ignored (missing variantId), element=${JSON.stringify(el.id)}`);
          lines.push(`    .placeFirst(${JSON.stringify(typeId)}, ${roleArg}, ${el.x}, ${el.y})`);
        } else {
          lines.push(
            `    .placeFirstWithVariantAndData(${JSON.stringify(typeId)}, ${roleArg}, ${el.x}, ${el.y}, ${JSON.stringify(variantId)}, ${dataLit})`
          );
        }
      } else if (variantId) {
        lines.push(`    .placeFirstWithVariant(${JSON.stringify(typeId)}, ${roleArg}, ${el.x}, ${el.y}, ${JSON.stringify(variantId)})`);
      } else {
        lines.push(`    .placeFirst(${JSON.stringify(typeId)}, ${roleArg}, ${el.x}, ${el.y})`);
      }
    }
  }

  lines.push('    ;');
  lines.push('');
  lines.push('LayoutRegistry.register(MACHINE_ID, layout);');

  return lines.join('\n');
}
