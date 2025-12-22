import {MachineUiDoc, MachineUiWidget} from '../model/ir';
import {exportMachineUiRuntimeJson} from './jsonExporter';

function stripLeadingSlashes(s: string): string {
  return s.replace(/^\/+/, '');
}

/**
 * Convert a texture reference from editor form into a ZenScript resource location argument.
 *
 * Examples:
 * - "gui/gui_controller_a.png" -> "prototypemachinery:gui/gui_controller_a"
 * - "prototypemachinery:textures/gui/foo.png" -> "prototypemachinery:gui/foo"
 * - "mymod:gui/bar" -> "mymod:gui/bar" (kept as-is)
 */
export function normalizePmUiTextureArgForExport(input?: string): string | undefined {
  const raw = typeof input === 'string' ? input.trim() : '';
  if (!raw) return undefined;

  const cleaned = stripLeadingSlashes(raw);

  const colon = cleaned.indexOf(':');
  if (colon >= 0) {
    const ns = cleaned.slice(0, colon).trim();
    let path = cleaned.slice(colon + 1).trim();
    path = stripLeadingSlashes(path);
    path = path.replace(/^textures\//, '');
    path = path.replace(/\.png$/i, '');
    return `${ns}:${path}`;
  }

  let path = cleaned;
  path = path.replace(/^textures\//, '');
  path = path.replace(/\.png$/i, '');
  return `prototypemachinery:${path}`;
}

function parseCssHexColorToInt(color?: string): number | undefined {
  const raw = typeof color === 'string' ? color.trim() : '';
  if (!raw) return undefined;
  // Accept #RRGGBB or #AARRGGBB
  const m = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.exec(raw);
  if (!m) return undefined;
  const hex = m[1];
  // TextBuilder expects 0xRRGGBB; if ARGB is provided, we drop alpha.
  const rgb = hex.length === 8 ? hex.slice(2) : hex;
  return parseInt(rgb, 16);
}

function escapeZenString(s: string): string {
  // JSON.stringify gives us a valid quoted string literal for ZenScript.
  return JSON.stringify(s);
}

function clampInt(n: unknown, fallback: number): number {
  const v = typeof n === 'number' ? n : Number(n);
  if (!Number.isFinite(v)) return fallback;
  return Math.trunc(v);
}

function widgetSortKey(w: MachineUiWidget): string {
  return `${w.y}\u0000${w.x}\u0000${w.id}`;
}

function asStringId(v: unknown): string {
  return typeof v === 'string' ? v.trim() : '';
}

function buildContainerForestByTab(widgets: MachineUiWidget[], tabId: string | undefined): MachineUiWidget[] {
  const list = widgets.filter((w) => normalizeTabIdMaybe((w as any).tabId) === (tabId ?? ''));
  if (list.length === 0) return [];

  const byId = new Map<string, MachineUiWidget>();
  for (const w of list) {
    const id = asStringId((w as any).id);
    if (!id) continue;
    byId.set(id, w);
  }

  const parentByChild = new Map<string, string>();
  for (const w of list) {
    const t = (w as any).type;
    if (t !== 'panel' && t !== 'scroll_container') continue;
    const cid = Array.isArray((w as any).childrenIds) ? ((w as any).childrenIds as any[]) : [];
    for (const raw of cid) {
      const childId = asStringId(raw);
      if (!childId) continue;
      if (!byId.has(childId)) continue;
      if (!parentByChild.has(childId)) parentByChild.set(childId, String((w as any).id));
    }
  }

  return list
    .filter((w) => {
      const id = asStringId((w as any).id);
      if (!id) return false;
      return !parentByChild.has(id);
    })
    .sort((a, b) => widgetSortKey(a).localeCompare(widgetSortKey(b)));
}

function indentLines(lines: string[], level: number): string[] {
  if (level <= 0) return lines;
  const pad = '    '.repeat(level);
  return lines.map((l) => (l.length > 0 ? pad + l : l));
}

type RuntimeTab = {
  id: string;
  label?: string;
  texturePath?: string;
};

function normalizeTabIdMaybe(value: unknown): string {
  const raw = typeof value === 'string' ? value.trim() : '';
  return raw;
}

function getRuntimeTabsLikeMod(doc: MachineUiDoc): { tabs: RuntimeTab[]; initialTabId: string } {
  const options = doc.options;
  const activeBackground = normalizeTabIdMaybe(options?.activeBackground);
  const activeTabId = normalizeTabIdMaybe(options?.activeTabId);

  const backgroundA = options?.backgroundA?.texturePath?.trim();
  const backgroundB = options?.backgroundB?.texturePath?.trim();

  const widgets = doc.widgets ?? [];
  const usedTabIds = new Set<string>();
  for (const w of widgets) {
    const t = normalizeTabIdMaybe((w as any).tabId);
    if (t) usedTabIds.add(t);
  }

  const tabsFromOptions = (options?.tabs ?? [])
    .map((t) => ({ id: t.id.trim(), label: t.label?.trim(), texturePath: t.texturePath?.trim() }))
    .filter((t) => t.id.length > 0);

  let tabs: RuntimeTab[];
  if (tabsFromOptions.length > 0) {
    tabs = tabsFromOptions;
  } else {
    // Legacy A/B logic mirrors MachineUiRuntimeJson.kt
    const legacy: RuntimeTab[] = [];
    if (backgroundA || usedTabIds.has('A') || (!backgroundB && !usedTabIds.has('B'))) {
      legacy.push({ id: 'A', label: 'Main', texturePath: backgroundA });
    }
    if (backgroundB || usedTabIds.has('B')) {
      legacy.push({ id: 'B', label: 'Extension', texturePath: backgroundB });
    }
    if (legacy.length === 0) {
      legacy.push({ id: 'A', label: 'Main', texturePath: backgroundA });
    }
    tabs = legacy;
  }

  const initial = activeTabId || activeBackground || tabs[0]?.id || 'A';
  const ordered = [...tabs].sort((a, b) => {
    const aIsInitial = a.id === initial ? 1 : 0;
    const bIsInitial = b.id === initial ? 1 : 0;
    if (aIsInitial !== bIsInitial) return bIsInitial - aIsInitial;
    return a.id.localeCompare(b.id);
  });

  return { tabs: ordered, initialTabId: initial };
}

function emitWidgetBuilderExprTree(
  w: MachineUiWidget,
  widgetsById: Map<string, MachineUiWidget>,
  origin?: { x: number; y: number },
  stack?: Set<string>,
): string[] | undefined {
  const x = (origin ? clampInt(w.x, 0) - clampInt(origin.x, 0) : w.x) ?? 0;
  const y = (origin ? clampInt(w.y, 0) - clampInt(origin.y, 0) : w.y) ?? 0;
  const width = w.w;
  const height = w.h;

  // Editor-only container: Panel group (childrenIds -> nested addChild)
  if ((w as any).type === 'panel') {
    const id = asStringId((w as any).id);
    const nextStack = stack ? new Set(stack) : new Set<string>();
    if (id) {
      if (nextStack.has(id)) {
        return ['PMUI.createPanel()', `.setPos(${x}, ${y})`, `.setSize(${width}, ${height})`, '// WARNING: cycle detected in panel.childrenIds; children skipped'];
      }
      nextStack.add(id);
    }

    const lines: string[] = [];
    lines.push('PMUI.createPanel()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);

    const cid = Array.isArray((w as any).childrenIds) ? ((w as any).childrenIds as any[]) : [];
    for (const raw of cid) {
      const childId = asStringId(raw);
      if (!childId) continue;
      const child = widgetsById.get(childId);
      if (!child) continue;
      const childExpr = emitWidgetExprWithConditions(child, widgetsById, { x: clampInt(w.x, 0), y: clampInt(w.y, 0) }, nextStack);
      if (!childExpr) continue;
      lines.push('.addChild(');
      lines.push(...indentLines(childExpr, 1));
      lines.push(')');
    }

    return lines;
  }

  if ((w as any).type === 'scroll_container') {
    const id = asStringId((w as any).id);
    const nextStack = stack ? new Set(stack) : new Set<string>();
    if (id) {
      if (nextStack.has(id)) {
        return [
          'PMUI.scrollContainer()',
          `.setPos(${x}, ${y})`,
          `.setSize(${width}, ${height})`,
          '// WARNING: cycle detected in scroll_container.childrenIds; children skipped',
        ];
      }
      nextStack.add(id);
    }

    const scrollX = typeof (w as any).scrollX === 'boolean' ? Boolean((w as any).scrollX) : undefined;
    const scrollY = typeof (w as any).scrollY === 'boolean' ? Boolean((w as any).scrollY) : undefined;
    const scrollSpeed = typeof (w as any).scrollSpeed === 'number' ? Math.trunc((w as any).scrollSpeed) : undefined;
    const cancelScrollEdge = typeof (w as any).cancelScrollEdge === 'boolean' ? Boolean((w as any).cancelScrollEdge) : undefined;
    const scrollBarOnStartX = typeof (w as any).scrollBarOnStartX === 'boolean' ? Boolean((w as any).scrollBarOnStartX) : undefined;
    const scrollBarOnStartY = typeof (w as any).scrollBarOnStartY === 'boolean' ? Boolean((w as any).scrollBarOnStartY) : undefined;
    const scrollBarThicknessX = typeof (w as any).scrollBarThicknessX === 'number' ? Math.trunc((w as any).scrollBarThicknessX) : undefined;
    const scrollBarThicknessY = typeof (w as any).scrollBarThicknessY === 'number' ? Math.trunc((w as any).scrollBarThicknessY) : undefined;

    const lines: string[] = [];
    lines.push('PMUI.scrollContainer()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    if (typeof scrollX === 'boolean') lines.push(`.setScrollX(${scrollX ? 'true' : 'false'})`);
    if (typeof scrollY === 'boolean') lines.push(`.setScrollY(${scrollY ? 'true' : 'false'})`);
    if (typeof scrollSpeed === 'number') lines.push(`.setScrollSpeed(${Math.max(1, scrollSpeed)})`);
    if (typeof cancelScrollEdge === 'boolean') lines.push(`.setCancelScrollEdge(${cancelScrollEdge ? 'true' : 'false'})`);
    if (typeof scrollBarOnStartX === 'boolean') lines.push(`.setScrollBarOnStartX(${scrollBarOnStartX ? 'true' : 'false'})`);
    if (typeof scrollBarOnStartY === 'boolean') lines.push(`.setScrollBarOnStartY(${scrollBarOnStartY ? 'true' : 'false'})`);
    if (typeof scrollBarThicknessX === 'number') lines.push(`.setScrollBarThicknessX(${scrollBarThicknessX})`);
    if (typeof scrollBarThicknessY === 'number') lines.push(`.setScrollBarThicknessY(${scrollBarThicknessY})`);

    const cid = Array.isArray((w as any).childrenIds) ? ((w as any).childrenIds as any[]) : [];
    for (const raw of cid) {
      const childId = asStringId(raw);
      if (!childId) continue;
      const child = widgetsById.get(childId);
      if (!child) continue;
      const childExpr = emitWidgetExprWithConditions(child, widgetsById, { x: clampInt(w.x, 0), y: clampInt(w.y, 0) }, nextStack);
      if (!childExpr) continue;
      lines.push('.addChild(');
      lines.push(...indentLines(childExpr, 1));
      lines.push(')');
    }

    return lines;
  }

  if (w.type === 'text') {
    const align = (w.align ?? 'left').toUpperCase();
    const shadow = Boolean((w as any).shadow);
    const colorInt = parseCssHexColorToInt(w.color);
    const lines: string[] = [];
    lines.push(`PMUI.text(${escapeZenString(w.text)})`);
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    if (typeof colorInt === 'number') {
      lines.push(`.setColor(0x${colorInt.toString(16).padStart(6, '0')})`);
    }
    lines.push(`.setShadow(${shadow ? 'true' : 'false'})`);
    lines.push(`.setAlignment(${escapeZenString(align)})`);
    return lines;
  }

  if (w.type === 'slotGrid') {
    const slotKey =
      typeof (w as any).slotKey === 'string' && String((w as any).slotKey).trim().length > 0
        ? String((w as any).slotKey).trim()
        : 'default';
    const startIndex = Math.max(0, Math.floor(Number((w as any).startIndex ?? 0) || 0));
    const canInsert = (w as any).canInsert;
    const canExtract = (w as any).canExtract;

    const lines: string[] = [];
    lines.push(`PMUI.itemSlotGroup(${escapeZenString(slotKey)}, ${startIndex}, ${w.cols}, ${w.rows})`);
    lines.push(`.setPos(${x}, ${y})`);
    if (typeof canInsert === 'boolean') lines.push(`.setInsert(${canInsert ? 'true' : 'false'})`);
    if (typeof canExtract === 'boolean') lines.push(`.setExtract(${canExtract ? 'true' : 'false'})`);
    // size is derived; keep the editor bounds in a comment when it differs from default 18px/grid.
    const slotSize = Math.floor((w as any).slotSize ?? 18);
    const gap = Math.floor((w as any).gap ?? 0);
    if (slotSize !== 18 || gap !== 0) {
      lines.push(`// NOTE: SlotGrid 运行时固定为 18px 无间距；editor 配置为 slotSize=${slotSize}, gap=${gap}（导出时无法保留）`);
    }
    return lines;
  }

  if (w.type === 'progress') {
    const dir = (w.direction ?? 'right').toUpperCase();
    const tex = normalizePmUiTextureArgForExport(w.runTexturePath ?? w.baseTexturePath);
    const progressKey = typeof (w as any).progressKey === 'string' ? String((w as any).progressKey).trim() : '';
    const tooltipTemplate = typeof (w as any).tooltipTemplate === 'string' ? String((w as any).tooltipTemplate) : '';
    const lines: string[] = [];
    lines.push('PMUI.progressBar()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    lines.push(`.setDirection(${escapeZenString(dir)})`);
    lines.push(`.setTexture(${tex ? escapeZenString(tex) : 'null'})`);
    if (progressKey) {
      lines.push(`.bindProgress(${escapeZenString(progressKey)})`);
    }
    if (tooltipTemplate.trim().length > 0) {
      lines.push(`.setTooltip(${escapeZenString(tooltipTemplate)})`);
    }
    return lines;
  }

  if (w.type === 'image') {
    const tex = normalizePmUiTextureArgForExport(w.texturePath);
    if (!tex) return undefined;
    const lines: string[] = [];
    lines.push(`PMUI.image(${escapeZenString(tex)})`);
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    return lines;
  }

  if (w.type === 'button') {
    const text = typeof w.text === 'string' ? w.text.trim() : '';
    const actionKey = typeof (w as any).actionKey === 'string' ? String((w as any).actionKey).trim() : '';
    const skin = typeof (w as any).skin === 'string' ? String((w as any).skin).trim() : '';
    const lines: string[] = [];
    lines.push(text ? `PMUI.button(${escapeZenString(text)})` : 'PMUI.button()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    if (skin) lines.push(`.setSkin(${escapeZenString(skin)})`);
    if (actionKey) lines.push(`.onClick(${escapeZenString(actionKey)})`);
    return lines;
  }

  if (w.type === 'toggle') {
    const skin = typeof (w as any).skin === 'string' ? String((w as any).skin).trim() : '';
    const stateKey = typeof (w as any).stateKey === 'string' ? String((w as any).stateKey).trim() : '';
    const textOn = typeof (w as any).textOn === 'string' ? String((w as any).textOn) : '';
    const textOff = typeof (w as any).textOff === 'string' ? String((w as any).textOff) : '';
    const textureOn = normalizePmUiTextureArgForExport((w as any).textureOn);
    const textureOff = normalizePmUiTextureArgForExport((w as any).textureOff);

    const lines: string[] = [];
    lines.push('PMUI.toggleButton()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    if (skin) lines.push(`.setSkin(${escapeZenString(skin)})`);
    if (stateKey) lines.push(`.bindState(${escapeZenString(stateKey)})`);
    if (textOn.trim().length > 0) lines.push(`.setTextOn(${escapeZenString(textOn)})`);
    if (textOff.trim().length > 0) lines.push(`.setTextOff(${escapeZenString(textOff)})`);
    if (textureOn || textureOff) {
      lines.push(`.setTextures(${textureOn ? escapeZenString(textureOn) : 'null'}, ${textureOff ? escapeZenString(textureOff) : 'null'})`);
    }
    return lines;
  }

  if (w.type === 'slider') {
    const skin = typeof (w as any).skin === 'string' ? String((w as any).skin).trim() : '';
    const min = typeof (w as any).min === 'number' ? (w as any).min : undefined;
    const max = typeof (w as any).max === 'number' ? (w as any).max : undefined;
    const step = typeof (w as any).step === 'number' ? (w as any).step : undefined;
    const valueKey = typeof (w as any).valueKey === 'string' ? String((w as any).valueKey).trim() : '';
    const horizontal = typeof (w as any).horizontal === 'boolean' ? Boolean((w as any).horizontal) : undefined;

    const lines: string[] = [];
    lines.push('PMUI.slider()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    if (skin) lines.push(`.setSkin(${escapeZenString(skin)})`);
    if (typeof min === 'number' && typeof max === 'number') {
      lines.push(`.setRange(${min}, ${max})`);
    }
    if (typeof step === 'number') lines.push(`.setStep(${step})`);
    if (valueKey) lines.push(`.bindValue(${escapeZenString(valueKey)})`);
    if (typeof horizontal === 'boolean') lines.push(`.setHorizontal(${horizontal ? 'true' : 'false'})`);
    return lines;
  }

  if (w.type === 'textField') {
    const skin = typeof (w as any).skin === 'string' ? String((w as any).skin).trim() : '';
    const valueKey = typeof (w as any).valueKey === 'string' ? String((w as any).valueKey).trim() : '';
    const inputTypeRaw = typeof (w as any).inputType === 'string' ? String((w as any).inputType).trim().toLowerCase() : '';
    const inputType = inputTypeRaw === 'long' ? 'long' : inputTypeRaw === 'string' ? 'string' : '';
    const minLong = typeof (w as any).minLong === 'number' ? Math.trunc((w as any).minLong) : undefined;
    const maxLong = typeof (w as any).maxLong === 'number' ? Math.trunc((w as any).maxLong) : undefined;

    const lines: string[] = [];
    lines.push('PMUI.textField()');
    lines.push(`.setPos(${x}, ${y})`);
    lines.push(`.setSize(${width}, ${height})`);
    if (valueKey) lines.push(`.bindValue(${escapeZenString(valueKey)})`);
    if (inputType) lines.push(`.setInputType(${escapeZenString(inputType)})`);
    if (inputType === 'long' && typeof minLong === 'number' && typeof maxLong === 'number') {
      lines.push(`.setLongRange(${minLong}L, ${maxLong}L)`);
    }
    if (skin) lines.push(`.setSkin(${escapeZenString(skin)})`);
    return lines;
  }

  if (w.type === 'playerInventory') {
    const lines: string[] = [];
    lines.push('PMUI.playerInventory()');
    lines.push(`.setPos(${x}, ${y})`);
    // size is fixed in builder, but keep editor value as comment if different.
    if (width !== 162 || height !== 76) {
      lines.push(`// NOTE: PlayerInventory size 运行时固定；editor 为 w=${width}, h=${height}（导出时无法保留）`);
    }
    return lines;
  }

  return undefined;
}

function emitWidgetExprWithConditions(
  w: MachineUiWidget,
  widgetsById: Map<string, MachineUiWidget>,
  origin?: { x: number; y: number },
  stack?: Set<string>,
): string[] | undefined {
  const base = emitWidgetBuilderExprTree(w, widgetsById, origin, stack);
  if (!base) return undefined;
  const visibleIf = typeof (w as any).visibleIf === 'string' ? String((w as any).visibleIf).trim() : '';
  const enabledIf = typeof (w as any).enabledIf === 'string' ? String((w as any).enabledIf).trim() : '';

  if (!visibleIf && !enabledIf) return base;

  const wrapped: string[] = [];
  wrapped.push('PMUI.conditional(');
  wrapped.push(...indentLines(base, 1));
  wrapped.push(')');
  if (visibleIf) wrapped.push(`.setVisibleIf(${escapeZenString(visibleIf)})`);
  if (enabledIf) wrapped.push(`.setEnabledIf(${escapeZenString(enabledIf)})`);
  return wrapped;
}

export type ExportMachineUiZenScriptOptions = {
  /** Placeholder in output; caller can post-process. */
  machineId?: string;
  /** Register priority in MachineUIRegistry (bigger wins). Default: 0. */
  priority?: number;
  /** JSON string formatting. Default: true (minify). */
  minify?: boolean;
  /** Include a short header comment and clear-before-register sequence. Default: true. */
  includeHeaderComment?: boolean;
  /** Output format. Default: 'runtime-json'. */
  format?: 'runtime-json' | 'builders';
  /** (builders only) Only export widgets visible on this tab (global widgets included). */
  tabId?: string;
};

function exportMachineUiZenScriptRuntimeJson(doc: MachineUiDoc, options: ExportMachineUiZenScriptOptions = {}): string {
  const machineId = options.machineId ?? 'prototypemachinery:example_machine';
  const priority = clampInt(options.priority, 0);
  const minify = options.minify ?? true;
  const includeHeaderComment = options.includeHeaderComment ?? true;

  const runtimeDoc = exportMachineUiRuntimeJson(doc);
  const runtimeJson = JSON.stringify(runtimeDoc, null, minify ? undefined : 2);

  const lines: string[] = [];
  lines.push('#loader crafttweaker reloadable');
  lines.push('');
  lines.push('import mods.prototypemachinery.ui.UIRegistry;');
  lines.push('');
  lines.push(`val MACHINE_ID = ${escapeZenString(machineId)};`);
  lines.push(`val PRIORITY = ${priority};`);
  lines.push('');

  if (includeHeaderComment) {
    lines.push('/**');
    lines.push(' * Auto-generated by PrototypeMachinery Web Editor (runtime JSON).');
    if (doc.name && doc.name.trim().length > 0) {
      lines.push(` * UI: ${doc.name.trim()}`);
    }
    lines.push(' *');
    lines.push(' * Notes:');
    lines.push(' * - This registers a runtime JSON UI, parsed by MachineUiRuntimeJson on the mod side.');
    lines.push(' * - If this script gets too large, consider storing the JSON elsewhere and loading it at runtime.');
    lines.push(' */');
    lines.push('');
  }

  lines.push('// reloadable 可能被多次执行：先清理旧注册，避免列表累积。');
  lines.push('UIRegistry.clear(MACHINE_ID);');
  lines.push('');

  lines.push(`val RUNTIME_JSON = ${escapeZenString(runtimeJson)};`);
  lines.push('');
  lines.push('UIRegistry.registerRuntimeJsonWithPriority(MACHINE_ID, RUNTIME_JSON, PRIORITY);');
  lines.push('');

  return lines.join('\n');
}

/**
 * Legacy exporter (PMUI builders).
 *
 * 目标：把 Machine UI IR 转成 CraftTweaker ZenScript（PMUI builders）。
 *
 * 限制（当前版本刻意保持简单）：
 * - 仅导出“当前 tab 可见”的 widgets（global + tabId 匹配）。
 * - ProgressBar 运行时只支持单贴图：优先用 runTexturePath，其次 baseTexturePath。
 * - SlotGrid 导出为 ItemSlotGroup（运行时槽位固定 18px、无 gap），不完全等价。
 */
function exportMachineUiZenScriptBuilders(doc: MachineUiDoc, options: ExportMachineUiZenScriptOptions = {}): string {
  const machineId = options.machineId ?? 'prototypemachinery:example_machine';
  const priority = clampInt(options.priority, 0);
  const includeHeaderComment = options.includeHeaderComment ?? true;

  const { tabs: orderedTabs } = getRuntimeTabsLikeMod(doc);

  const lines: string[] = [];
  lines.push('#loader crafttweaker reloadable');
  lines.push('');
  lines.push('import mods.prototypemachinery.ui.PMUI;');
  lines.push('import mods.prototypemachinery.ui.UIRegistry;');
  lines.push('');
  lines.push(`val MACHINE_ID = ${escapeZenString(machineId)};`);
  lines.push(`val PRIORITY = ${priority};`);
  lines.push('');

  if (includeHeaderComment) {
    lines.push('/**');
    lines.push(' * Auto-generated by PrototypeMachinery Web Editor (PMUI builders).');
    if (doc.name && doc.name.trim().length > 0) {
      lines.push(` * UI: ${doc.name.trim()}`);
    }
    lines.push(' *');
    lines.push(' * Notes:');
    lines.push(' * - This uses PMUI builders and UIRegistry.registerWithPriority (no embedded JSON).');
    lines.push(' * - Tabs/conditions require recent PrototypeMachinery builds (TabContainerBuilder/ConditionalBuilder).');
    lines.push(' */');
    lines.push('');
  }
  lines.push('// reloadable 可能被多次执行：先清理旧注册，避免列表累积。');
  lines.push('UIRegistry.clear(MACHINE_ID);');
  lines.push('');

  const canvasW = doc.canvas.width;
  const canvasH = doc.canvas.height;

  const widgetList = [...(doc.widgets ?? [])];
  const widgetsById = new Map<string, MachineUiWidget>();
  for (const w of widgetList) {
    const id = asStringId((w as any).id);
    if (!id) continue;
    widgetsById.set(id, w);
  }

  // Build per-tab content panels
  for (const t of orderedTabs) {
    const varName = `tab_${t.id.replace(/[^a-zA-Z0-9_]/g, '_')}`;
    const bgArg = normalizePmUiTextureArgForExport(t.texturePath);
    const header: string[] = [`val ${varName} = PMUI.createPanel()`, `    .setPos(0, 0)`, `    .setSize(${canvasW}, ${canvasH})`];
    if (bgArg) header.push(`    .setBackground(${escapeZenString(bgArg)})`);
    lines.push(header.join('\n') + ';');
    lines.push('');

    const tabWidgets = buildContainerForestByTab(widgetList, t.id);

    if (tabWidgets.length === 0) {
      lines.push(`// ${t.id}: (empty)`);
      lines.push('');
      continue;
    }

    for (const w of tabWidgets) {
      const expr = emitWidgetExprWithConditions(w, widgetsById, undefined, undefined);
      if (!expr) {
        lines.push(`// TODO: 未支持的 widget type: ${(w as any).type}`);
        continue;
      }
      lines.push(`${varName}.addChild(`);
      lines.push(...indentLines(expr, 1));
      lines.push(');');
      lines.push('');
    }
  }

  // Build tab container
  lines.push('val tabContainer = PMUI.tabContainer()');
  lines.push(`    .setPos(0, 0)`);
  lines.push(`    .setSize(${canvasW}, ${canvasH})`);
  lines.push(`    .setTabPosition(${escapeZenString('LEFT')})`);
  for (let i = 0; i < orderedTabs.length; i++) {
    const t = orderedTabs[i];
    const varName = `tab_${t.id.replace(/[^a-zA-Z0-9_]/g, '_')}`;
    const title = t.label && t.label.trim().length > 0 ? t.label.trim() : t.id;
    const line = `    .addTab(PMUI.tab(${escapeZenString(t.id)}, ${escapeZenString(title)}).setContent(${varName}))`;
    if (i === orderedTabs.length - 1) {
      lines.push(line + ';');
    } else {
      lines.push(line);
    }
  }
  lines.push('');

  // Root panel (tab container first, then globals)
  lines.push('val panel = PMUI.createPanel()');
  lines.push(`    .setSize(${canvasW}, ${canvasH})`);
  lines.push('    .addChild(tabContainer);');
  lines.push('');

  const globalWidgets = buildContainerForestByTab(widgetList, undefined);

  for (const w of globalWidgets) {
    const expr = emitWidgetExprWithConditions(w, widgetsById, undefined, undefined);
    if (!expr) {
      lines.push(`// TODO: 未支持的 widget type: ${(w as any).type}`);
      continue;
    }
    lines.push('panel.addChild(');
    lines.push(...indentLines(expr, 1));
    lines.push(');');
    lines.push('');
  }

  lines.push('UIRegistry.registerWithPriority(MACHINE_ID, panel, PRIORITY);');
  lines.push('');
  lines.push('// 说明：该脚本导出为“纯 builders”，不含 JSON 字符串。');
  lines.push('// 如果你使用的 PrototypeMachinery 版本缺少 tabs/conditions builders，请切换导出为 runtime-json。');

  return lines.join('\n');
}

/**
 * ZenScript exporter.
 *
 * Default is runtime JSON, because it matches the mod-side runtime loader and preserves
 * tabs + conditional widgets more faithfully.
 */
export function exportMachineUiZenScript(doc: MachineUiDoc, options: ExportMachineUiZenScriptOptions = {}): string {
  const format = options.format ?? 'runtime-json';
  if (format === 'builders') return exportMachineUiZenScriptBuilders(doc, options);
  return exportMachineUiZenScriptRuntimeJson(doc, options);
}
