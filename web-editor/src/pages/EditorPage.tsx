import {
  ActionIcon,
  Autocomplete,
  Box,
  Button,
  Code,
  Divider,
  Group,
  Menu,
  NumberInput,
  ScrollArea,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Tabs,
  Text,
  TextInput
} from '@mantine/core';
import {useHotkeys} from '@mantine/hooks';
import {IconArrowBackUp, IconArrowForwardUp, IconDeviceFloppy, IconDownload, IconFileImport, IconPlus, IconTrash,} from '@tabler/icons-react';
import {useEffect, useMemo, useRef, useState} from 'react';
import {CanvasStage} from '../editor/components/CanvasStage';
import {useEditorStore} from '../editor/store/editorStore';
import {exportZenScript} from '../editor/exporters/zsExporter';
import {downloadText} from '../editor/io/download';
import {loadExampleLayout} from '../editor/io/examples';
import {ELEMENT_PRESETS} from '../editor/presets/elementPresets';
import {fetchPmAssetIndex, pmTextureUrl} from '../editor/assets/pmAssets';
import {
  decoratorIdSuggestions,
  typeIdSuggestionsForType,
  typeSuggestions,
  variantIdSuggestionsForType
} from '../editor/suggestions/elementSuggestions';
import {parseResourceLocation} from '../editor/components/canvas/elementPreview';

export function EditorPage() {
  const canvasRef = useRef<HTMLDivElement | null>(null);

  const [pmPaths, setPmPaths] = useState<string[] | null>(null);
  const [pmQuery, setPmQuery] = useState('');
  const [pmError, setPmError] = useState<string | null>(null);

  const doc = useEditorStore((s) => s.doc);
  const selectionId = useEditorStore((s) => s.selectionId);
  const selected = useEditorStore((s) => (s.selectionId ? s.doc.elements.find((e) => e.id === s.selectionId) : undefined));

  const canUndo = useEditorStore((s) => s.historyPast.length > 0);
  const canRedo = useEditorStore((s) => s.historyFuture.length > 0);

  const setDocName = useEditorStore((s) => s.setDocName);
  const setCanvasSize = useEditorStore((s) => s.setCanvasSize);
  const setMergePerTickRoles = useEditorStore((s) => s.setMergePerTickRoles);
  const setGridSize = useEditorStore((s) => s.setGridSize);
  const setBackgroundNineSliceEnabled = useEditorStore((s) => s.setBackgroundNineSliceEnabled);
  const patchBackgroundNineSlice = useEditorStore((s) => s.patchBackgroundNineSlice);
  const addElement = useEditorStore((s) => s.addElement);
  const removeSelected = useEditorStore((s) => s.removeSelected);
  const updateElement = useEditorStore((s) => s.updateElement);
  const undo = useEditorStore((s) => s.undo);
  const redo = useEditorStore((s) => s.redo);
  const saveToLocal = useEditorStore((s) => s.saveToLocal);
  const loadFromLocal = useEditorStore((s) => s.loadFromLocal);
  const importFromJson = useEditorStore((s) => s.importFromJson);

  const zs = useMemo(() => exportZenScript(doc), [doc]);

  const selectedType = selected?.type;
  const typeData = useMemo(() => typeSuggestions(), []);
  const typeIdData = useMemo(() => (selectedType ? typeIdSuggestionsForType(selectedType) : []), [selectedType]);
  const variantData = useMemo(() => (selectedType ? variantIdSuggestionsForType(pmPaths, selectedType) : []), [pmPaths, selectedType]);
  const decoratorData = useMemo(() => decoratorIdSuggestions(), []);

  const progressModuleTypeData = useMemo(() => {
    if (!pmPaths || pmPaths.length === 0) return ['right', 'left', 'up', 'down', 'heat', 'cool', 'compress', 'merge', 'split'];
    const types: string[] = [];
    for (const p of pmPaths) {
      const m = p.match(/^gui\/jei_recipeicons\/progress_module\/(.+?)_base\.png$/);
      if (!m) continue;
      types.push(m[1]);
    }
    return Array.from(new Set(types)).sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const jeiBgTextureData = useMemo(() => {
    const out: string[] = [];
    if (pmPaths) {
      for (const p of pmPaths) {
        const m = p.match(/^gui\/jei_recipeicons\/([^/]+?\.png)$/);
        if (!m) continue;
        out.push(m[1]);
      }
    }
    if (!out.includes('jei_base.png')) out.push('jei_base.png');
    return Array.from(new Set(out)).sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const bgEnabled = Boolean(doc.options?.backgroundNineSlice);
  const bgOpt = doc.options?.backgroundNineSlice;

  const [bgTextureDraft, setBgTextureDraft] = useState('');
  useEffect(() => {
    if (!bgEnabled) {
      setBgTextureDraft('');
      return;
    }
    setBgTextureDraft(typeof bgOpt?.texture === 'string' ? bgOpt.texture : '');
  }, [bgEnabled, bgOpt?.texture]);

  const commitBgTexture = (raw: string) => {
    const t = raw.trim();
    patchBackgroundNineSlice({ texture: t || undefined });
    setBgTextureDraft(t);
  };

  // Autocomplete UX: allow clearing the input to show full suggestions.
  // We keep a local draft for `type` and commit it on blur/selection.
  const [typeDraft, setTypeDraft] = useState('');
  useEffect(() => {
    setTypeDraft(selected?.type ?? '');
  }, [selected?.id, selected?.type]);

  const patchSelectedData = (patch: Record<string, unknown>) => {
    if (!selected) return;
    const prev = selected.data ?? {};
    const next: Record<string, unknown> = { ...prev, ...patch };
    for (const [k, v] of Object.entries(patch)) {
      if (v === undefined) delete next[k];
    }
    updateElement(selected.id, { data: next });
  };

  useEffect(() => {
    let cancelled = false;
    fetchPmAssetIndex()
      .then((idx) => {
        if (cancelled) return;
        setPmPaths(idx.paths);
        setPmError(null);
      })
      .catch((e) => {
        if (cancelled) return;
        setPmError(String(e));
        setPmPaths([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useHotkeys([
    ['mod+z', () => undo(), { preventDefault: true }],
    ['mod+shift+z', () => redo(), { preventDefault: true }],
    ['mod+y', () => redo(), { preventDefault: true }],
    ['delete', () => removeSelected(), { preventDefault: true }],
  ]);

  return (
    <Group
      align="stretch"
      gap="md"
      style={{
        height: '100%',
        overflow: 'hidden',
        minHeight: 0,
      }}
    >
      {/* Left: canvas */}
      <Box ref={canvasRef} style={{ flex: 1, minWidth: 520, minHeight: 0, overflow: 'hidden' }}>
        <Stack h="100%" gap="xs">
          <Group justify="space-between" wrap="nowrap">
            <Group gap="xs">
              <Menu withinPortal>
                <Menu.Target>
                  <Button leftSection={<IconPlus size={16} />} variant="light">
                    添加元素
                  </Button>
                </Menu.Target>
                <Menu.Dropdown>
                  {ELEMENT_PRESETS.map((p) => (
                    <Menu.Item key={p.key} onClick={() => addElement(p.key)}>
                      {p.label}
                    </Menu.Item>
                  ))}
                </Menu.Dropdown>
              </Menu>
              <Button
                leftSection={<IconTrash size={16} />}
                variant="subtle"
                color="red"
                disabled={!selectionId}
                onClick={() => removeSelected()}
              >
                删除选中
              </Button>
            </Group>

            <Group gap="xs">
              <ActionIcon variant="light" title="撤销 (Ctrl+Z)" disabled={!canUndo} onClick={() => undo()}>
                <IconArrowBackUp size={18} />
              </ActionIcon>
              <ActionIcon variant="light" title="重做 (Ctrl+Y)" disabled={!canRedo} onClick={() => redo()}>
                <IconArrowForwardUp size={18} />
              </ActionIcon>

              <ActionIcon variant="light" title="从 localStorage 载入" onClick={() => loadFromLocal()}>
                <IconFileImport size={18} />
              </ActionIcon>
              <ActionIcon variant="light" title="保存到 localStorage" onClick={() => saveToLocal()}>
                <IconDeviceFloppy size={18} />
              </ActionIcon>
              <ActionIcon
                variant="light"
                title="导出 ZenScript"
                onClick={() => downloadText('pm_layout.zs', zs)}
              >
                <IconDownload size={18} />
              </ActionIcon>
            </Group>
          </Group>

          <Divider />

          <CanvasStage />

          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              说明：当前是骨架版本，已支持拖拽/选中/属性编辑/导出占位。
            </Text>
            <Group gap="xs">
              <Button variant="subtle" onClick={() => importFromJson(loadExampleLayout())}>
                载入示例
              </Button>
            </Group>
          </Group>
        </Stack>
      </Box>

      {/* Right: inspector */}
      <Box style={{ width: 360, height: '100%', minHeight: 0, overflow: 'hidden' }}>
        <Tabs
          defaultValue="props"
          style={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}
        >
          <Tabs.List>
            <Tabs.Tab value="props">属性</Tabs.Tab>
            <Tabs.Tab value="assets">素材</Tabs.Tab>
            <Tabs.Tab value="export">导出</Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="props" pt="sm" style={{ flex: 1, minHeight: 0 }}>
            <ScrollArea h="100%" type="auto" offsetScrollbars>
              <Stack gap="sm" pb="md">
                <TextInput label="文档名" value={doc.name} onChange={(e) => setDocName(e.currentTarget.value)} />

                <Group grow>
                  <NumberInput
                    label="画布宽"
                    value={doc.canvas.width}
                    min={1}
                    step={1}
                    onChange={(v) => setCanvasSize(Number(v) || 1, doc.canvas.height)}
                  />
                  <NumberInput
                    label="画布高"
                    value={doc.canvas.height}
                    min={1}
                    step={1}
                    onChange={(v) => setCanvasSize(doc.canvas.width, Number(v) || 1)}
                  />
                </Group>

                <NumberInput
                  label="网格大小"
                  description="编辑器网格/吸附步长（>=2px）。"
                  value={doc.options?.gridSize ?? 8}
                  min={2}
                  step={1}
                  onChange={(v) => setGridSize(Number(v) || 2)}
                />

                <Switch
                  label="合并 per-tick role"
                  description='导出时调用 LayoutBuilder.mergePerTickRoles(true)：role="INPUT" 同时匹配 INPUT + INPUT_PER_TICK（OUTPUT 同理）。'
                  checked={Boolean(doc.options?.mergePerTickRoles)}
                  onChange={(e) => setMergePerTickRoles(e.currentTarget.checked)}
                />

                <Divider label="JEI 背景" />

                <Switch
                  label="自定义背景（nine-slice）"
                  description="导出时调用 LayoutBuilder.setBackgroundNineSlice(...)；不需要手动放一个 decorator/background 元素。"
                  checked={bgEnabled}
                  onChange={(e) => setBackgroundNineSliceEnabled(e.currentTarget.checked)}
                />

                {bgEnabled ? (
                  <Stack gap="xs">
                    <Autocomplete
                      label="texture"
                      description='留空=jei_base.png；不含冒号的值会当作 gui/jei_recipeicons/ 下的相对路径（例如 jei_base.png）。'
                      value={bgTextureDraft}
                      data={jeiBgTextureData}
                      placeholder="(jei_base.png)"
                      onChange={(v) => setBgTextureDraft(v)}
                      onOptionSubmit={(v) => commitBgTexture(v)}
                      onBlur={() => commitBgTexture(bgTextureDraft)}
                    />

                    {(() => {
                      const rl = parseResourceLocation(bgTextureDraft);
                      if (!rl) return null;
                      if (bgTextureDraft.includes(':') && rl.namespace !== 'prototypemachinery') {
                        return (
                          <Text size="xs" c="dimmed">
                            提示：外部 mod 贴图目前无法在编辑器里预览，但会原样导出。
                          </Text>
                        );
                      }
                      return null;
                    })()}

                    <NumberInput
                      label="borderPx"
                      min={0}
                      step={1}
                      value={typeof bgOpt?.borderPx === 'number' ? bgOpt.borderPx : 2}
                      onChange={(v) => {
                        const n = typeof v === 'number' ? v : Number(v);
                        if (!Number.isFinite(n)) return;
                        patchBackgroundNineSlice({ borderPx: Math.max(0, Math.floor(n)) });
                      }}
                    />

                    <Switch
                      label="fillCenter"
                      checked={Boolean(bgOpt?.fillCenter)}
                      onChange={(e) => patchBackgroundNineSlice({ fillCenter: e.currentTarget.checked })}
                    />
                  </Stack>
                ) : null}

                <Divider label="选中元素" />

                {!selected ? (
                  <Text size="sm" c="dimmed">未选中任何元素（点击画布上的矩形）。</Text>
                ) : (
                  <Stack gap="sm" pb="md">
                    <Text size="sm">ID: <Code>{selected.id}</Code></Text>

                    <TextInput
                      label="nodeId（可选）"
                      description="填写后将导出 placeNode*；不填则导出 placeFirst*（按 typeId/role 匹配）。"
                      value={selected.nodeId ?? ''}
                      onChange={(e) => updateElement(selected.id, { nodeId: e.currentTarget.value || undefined })}
                    />

                    <Autocomplete
                      label={selected.type === 'decorator' ? 'typeId（decorator 不需要）' : 'typeId（可选）'}
                      description={
                        selected.type === 'decorator'
                          ? 'decorator 会导出 addDecorator*，不使用 typeId/role 匹配。'
                          : '例如：prototypemachinery:item / prototypemachinery:fluid / prototypemachinery:energy（留空会按 type 推导）'
                      }
                      value={selected.typeId ?? ''}
                      data={typeIdData}
                      onChange={(v) => updateElement(selected.id, { typeId: v.trim() || undefined })}
                      placeholder={selected.type === 'decorator' ? '(建议留空)' : '(auto)'}
                    />

                    <Select
                      label="role（可选）"
                      description='LayoutBuilder 支持：INPUT / OUTPUT / INPUT_PER_TICK / OUTPUT_PER_TICK / OTHER / ANY（留空等同 ANY）'
                      value={selected.role ?? ''}
                      onChange={(v) => updateElement(selected.id, { role: v || undefined })}
                      data={[
                        { value: '', label: '(ANY/不限制)' },
                        { value: 'INPUT', label: 'INPUT' },
                        { value: 'OUTPUT', label: 'OUTPUT' },
                        { value: 'INPUT_PER_TICK', label: 'INPUT_PER_TICK' },
                        { value: 'OUTPUT_PER_TICK', label: 'OUTPUT_PER_TICK' },
                        { value: 'OTHER', label: 'OTHER' },
                      ]}
                    />

                    <NumberInput
                      label="x"
                      value={selected.x}
                      step={1}
                      onChange={(v) => updateElement(selected.id, { x: Number(v) || 0 })}
                    />
                    <NumberInput
                      label="y"
                      value={selected.y}
                      step={1}
                      onChange={(v) => updateElement(selected.id, { y: Number(v) || 0 })}
                    />
                    <NumberInput
                      label="w"
                      value={selected.w}
                      step={1}
                      min={1}
                      onChange={(v) => updateElement(selected.id, { w: Math.max(1, Number(v) || 1) })}
                    />
                    <NumberInput
                      label="h"
                      value={selected.h}
                      step={1}
                      min={1}
                      onChange={(v) => updateElement(selected.id, { h: Math.max(1, Number(v) || 1) })}
                    />

                    <Autocomplete
                      label="type"
                      description="例如：slot / tank / energy / decorator / text（可自由输入；可清空以显示全部候选）"
                      value={typeDraft}
                      data={typeData}
                      onChange={(v) => setTypeDraft(v)}
                      onOptionSubmit={(v) => {
                        const next = v.trim();
                        setTypeDraft(next);
                        if (next) updateElement(selected.id, { type: next });
                      }}
                      onBlur={() => {
                        const next = typeDraft.trim();
                        if (next && next !== selected.type) {
                          updateElement(selected.id, { type: next });
                          return;
                        }
                        // If user leaves it empty (or unchanged), revert the draft to current model value.
                        setTypeDraft(selected.type);
                      }}
                    />

                    {selected.type === 'decorator' ? (
                      <Autocomplete
                        label="decoratorId（使用 variantId 字段）"
                        description='例如：prototypemachinery:decorator/background（可自由输入；会导出 addDecorator*）'
                        value={selected.variantId ?? ''}
                        data={decoratorData}
                        onChange={(v) => updateElement(selected.id, { variantId: v.trim() || undefined })}
                      />
                    ) : (
                      <Autocomplete
                        label="variantId"
                        description='例如：prototypemachinery:energy/1x3（可自由输入）'
                        value={selected.variantId ?? ''}
                        data={variantData}
                        onChange={(v) => updateElement(selected.id, { variantId: v.trim() || undefined })}
                      />
                    )}

                    {selected.type === 'decorator' ? (() => {
                      const path = parseResourceLocation(selected.variantId)?.path;
                      if (!path) {
                        return (
                          <Text size="sm" c="dimmed">
                            提示：选择一个内建 decoratorId 后，这里会出现更友好的表单编辑。
                          </Text>
                        );
                      }

                      if (path === 'decorator/background') {
                        return (
                          <Stack gap="xs">
                            <Divider label="Decorator 表单：background" />
                            <TextInput
                              label="texture"
                              description='对应 JeiBackgroundSpec：留空=jei_base.png；不含冒号的值会当作 gui/jei_recipeicons/ 下的相对路径（例如 jei_base.png）'
                              value={typeof selected.data?.texture === 'string' ? selected.data.texture : ''}
                              onChange={(e) => {
                                const v = e.currentTarget.value.trim();
                                patchSelectedData({ texture: v || undefined });
                              }}
                            />
                            <NumberInput
                              label="borderPx"
                              min={0}
                              step={1}
                              value={typeof selected.data?.borderPx === 'number' ? selected.data.borderPx : 2}
                              onChange={(v) => {
                                const n = typeof v === 'number' ? v : Number(v);
                                patchSelectedData({ borderPx: Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 2 });
                              }}
                            />
                            <Switch
                              label="fillCenter"
                              checked={Boolean(selected.data?.fillCenter)}
                              onChange={(e) => patchSelectedData({ fillCenter: e.currentTarget.checked })}
                            />
                          </Stack>
                        );
                      }

                      if (path === 'decorator/progress_module') {
                        return (
                          <Stack gap="xs">
                            <Divider label="Decorator 表单：progress_module" />
                            <Autocomplete
                              label="type"
                              description='例如：right / left / up / down / heat / cool（基于 progress_module 贴图推导候选）'
                              value={typeof selected.data?.type === 'string' ? selected.data.type : ''}
                              data={progressModuleTypeData}
                              onChange={(v) => patchSelectedData({ type: v.trim() || undefined })}
                            />
                            <Select
                              label="direction"
                              value={typeof selected.data?.direction === 'string' ? selected.data.direction : 'RIGHT'}
                              onChange={(v) => patchSelectedData({ direction: v || 'RIGHT' })}
                              data={[
                                { value: 'RIGHT', label: 'RIGHT' },
                                { value: 'LEFT', label: 'LEFT' },
                                { value: 'UP', label: 'UP' },
                                { value: 'DOWN', label: 'DOWN' },
                              ]}
                            />
                            <NumberInput
                              label="cycleTicks"
                              description="动画周期（tick）；留空会用配方 durationTicks（模组端默认）。"
                              min={1}
                              step={1}
                              value={typeof selected.data?.cycleTicks === 'number' ? selected.data.cycleTicks : undefined}
                              placeholder="(auto)"
                              onChange={(v) => {
                                const n = typeof v === 'number' ? v : Number(v);
                                if (!Number.isFinite(n)) {
                                  patchSelectedData({ cycleTicks: undefined });
                                  return;
                                }
                                patchSelectedData({ cycleTicks: Math.max(1, Math.floor(n)) });
                              }}
                            />
                            <NumberInput
                              label="previewProgress (0~1)"
                              description="仅影响编辑器画布预览。"
                              min={0}
                              max={1}
                              step={0.05}
                              value={typeof selected.data?.previewProgress === 'number' ? selected.data.previewProgress : undefined}
                              placeholder="(0.5)"
                              onChange={(v) => {
                                const n = typeof v === 'number' ? v : Number(v);
                                if (!Number.isFinite(n)) {
                                  patchSelectedData({ previewProgress: undefined });
                                  return;
                                }
                                patchSelectedData({ previewProgress: Math.max(0, Math.min(1, n)) });
                              }}
                            />
                          </Stack>
                        );
                      }

                      if (path === 'decorator/progress') {
                        return (
                          <Stack gap="xs">
                            <Divider label="Decorator 表单：progress" />
                            <Select
                              label="style"
                              value={typeof selected.data?.style === 'string' ? selected.data.style : 'arrow'}
                              onChange={(v) => patchSelectedData({ style: v || 'arrow' })}
                              data={[
                                { value: 'arrow', label: 'arrow' },
                                { value: 'cycle', label: 'cycle' },
                              ]}
                            />
                            <Select
                              label="direction"
                              value={typeof selected.data?.direction === 'string' ? selected.data.direction : 'RIGHT'}
                              onChange={(v) => patchSelectedData({ direction: v || 'RIGHT' })}
                              data={[
                                { value: 'RIGHT', label: 'RIGHT' },
                                { value: 'LEFT', label: 'LEFT' },
                                { value: 'UP', label: 'UP' },
                                { value: 'DOWN', label: 'DOWN' },
                                { value: 'CIRCULAR_CW', label: 'CIRCULAR_CW' },
                              ]}
                            />
                            <NumberInput
                              label="cycleTicks"
                              description="动画周期（tick）；留空会用配方 durationTicks（模组端默认）。"
                              min={1}
                              step={1}
                              value={typeof selected.data?.cycleTicks === 'number' ? selected.data.cycleTicks : undefined}
                              placeholder="(auto)"
                              onChange={(v) => {
                                const n = typeof v === 'number' ? v : Number(v);
                                if (!Number.isFinite(n)) {
                                  patchSelectedData({ cycleTicks: undefined });
                                  return;
                                }
                                patchSelectedData({ cycleTicks: Math.max(1, Math.floor(n)) });
                              }}
                            />
                            <Text size="xs" c="dimmed">
                              注：progress decorator 的 width/height 在模组端来自 data。导出时若未填写 width/height，将自动使用当前元素的 w/h。
                            </Text>
                            <NumberInput
                              label="previewProgress (0~1)"
                              description="仅影响编辑器画布预览。"
                              min={0}
                              max={1}
                              step={0.05}
                              value={typeof selected.data?.previewProgress === 'number' ? selected.data.previewProgress : undefined}
                              placeholder="(0.5)"
                              onChange={(v) => {
                                const n = typeof v === 'number' ? v : Number(v);
                                if (!Number.isFinite(n)) {
                                  patchSelectedData({ previewProgress: undefined });
                                  return;
                                }
                                patchSelectedData({ previewProgress: Math.max(0, Math.min(1, n)) });
                              }}
                            />
                          </Stack>
                        );
                      }

                      if (path === 'decorator/recipe_duration') {
                        return (
                          <Stack gap="xs">
                            <Divider label="Decorator 表单：recipe_duration" />
                            <TextInput
                              label="template"
                              description='例如 "{ticks} t ({seconds}s)"（留空用默认模板）'
                              value={typeof selected.data?.template === 'string' ? selected.data.template : ''}
                              onChange={(e) => {
                                const v = e.currentTarget.value;
                                patchSelectedData({ template: v.trim() ? v : undefined });
                              }}
                            />
                            <Text size="xs" c="dimmed">
                              注：recipe_duration 的 width/height 在模组端来自 data。导出时若未填写 width/height，将自动使用当前元素的 w/h。
                            </Text>
                          </Stack>
                        );
                      }

                      return (
                        <Text size="sm" c="dimmed">
                          未知 decorator：<Code>{path}</Code>（仍可通过 data(JSON) 手写参数）。
                        </Text>
                      );
                    })() : null}

                    <TextInput
                      label="data(JSON)"
                      description='renderer-specific data，例如 {"energyLedYOffset":-1}'
                      value={JSON.stringify(selected.data ?? {})}
                      onChange={(e) => {
                        try {
                          const obj = JSON.parse(e.currentTarget.value);
                          updateElement(selected.id, { data: obj });
                        } catch {
                          // ignore parse error while typing
                        }
                      }}
                    />

                    {selected.type === 'energy' ? (
                      <Stack gap="xs">
                        <Divider label="能量预览 (editor-only)" />
                        <NumberInput
                          label="previewFill (0~1)"
                          description="仅影响编辑器画布预览：0=空，1=满（默认会按 role 给一个稳定值）。"
                          min={0}
                          max={1}
                          step={0.05}
                          value={typeof selected.data?.previewFill === 'number' ? selected.data.previewFill : undefined}
                          placeholder="(auto)"
                          onChange={(v) => {
                            const n = typeof v === 'number' ? v : Number(v);
                            const prev = selected.data ?? {};
                            if (!Number.isFinite(n)) {
                              const { previewFill: _drop, ...rest } = prev;
                              updateElement(selected.id, { data: rest });
                              return;
                            }
                            updateElement(selected.id, { data: { ...prev, previewFill: Math.max(0, Math.min(1, n)) } });
                          }}
                        />

                        <NumberInput
                          label="energyLedYOffset"
                          description="对应模组端 placement data：energyLedYOffset（像素）。"
                          step={1}
                          value={typeof selected.data?.energyLedYOffset === 'number' ? selected.data.energyLedYOffset : 0}
                          onChange={(v) => {
                            const n = typeof v === 'number' ? v : Number(v);
                            const prev = selected.data ?? {};
                            updateElement(selected.id, { data: { ...prev, energyLedYOffset: Number.isFinite(n) ? n : 0 } });
                          }}
                        />
                      </Stack>
                    ) : null}
                  </Stack>
                )}
              </Stack>
            </ScrollArea>
          </Tabs.Panel>

          <Tabs.Panel value="assets" pt="sm" style={{ flex: 1, minHeight: 0 }}>
            <ScrollArea h="100%" type="auto" offsetScrollbars>
              <Stack gap="sm" pb="md">
                <Text size="sm" c="dimmed">
                  这里先做一个“PM 内建贴图”浏览器：贴图来自 PrototypeMachinery 工程里的
                  <Code>assets/prototypemachinery/textures</Code>，运行时由 Vite 提供 <Code>/pm-textures/*</Code>。
                </Text>

                {pmError ? (
                  <Text size="sm" c="red">加载 PM 贴图索引失败：{pmError}</Text>
                ) : (
                  <Text size="sm" c="dimmed">已加载 {pmPaths?.length ?? 0} 张贴图（当前仅包含 jei_recipeicons + gui/slot.png）。</Text>
                )}

                <TextInput
                  label="搜索"
                  placeholder="例如：energy_module/1_3 或 fluid_module/2_4"
                  value={pmQuery}
                  onChange={(e) => setPmQuery(e.currentTarget.value)}
                />

                <Text size="sm" c="dimmed">
                  用法：选中一个元素后，点击下面任意贴图，会把该贴图写入该元素的 <Code>data.texturePath</Code>，画布会直接显示它。
                </Text>

                <SimpleGrid cols={2} spacing="xs" verticalSpacing="xs">
                  {(pmPaths ?? [])
                    .filter((p) => (pmQuery.trim() ? p.toLowerCase().includes(pmQuery.trim().toLowerCase()) : true))
                    .slice(0, 200)
                    .map((p) => (
                      <Box
                        key={p}
                        style={{
                          border: '1px solid var(--mantine-color-dark-4)',
                          borderRadius: 8,
                          padding: 8,
                          cursor: selected ? 'pointer' : 'default',
                          opacity: selected ? 1 : 0.6,
                        }}
                        title={selected ? '点击应用到选中元素' : '先选中一个元素再应用'}
                        onClick={() => {
                          if (!selected) return;
                          const prev = selected.data ?? {};
                          updateElement(selected.id, { data: { ...prev, texturePath: p } });
                        }}
                      >
                        <Box
                          component="img"
                          src={pmTextureUrl(p)}
                          alt={p}
                          style={{
                            width: '100%',
                            height: 64,
                            objectFit: 'contain',
                            background: 'rgba(0,0,0,0.18)',
                            borderRadius: 6,
                          }}
                        />
                        <Text size="xs" c="dimmed" mt={6} style={{ wordBreak: 'break-all' }}>
                          {p}
                        </Text>
                      </Box>
                    ))}
                </SimpleGrid>

                {(pmPaths ?? []).length > 200 ? (
                  <Text size="xs" c="dimmed">为避免卡顿，当前最多渲染前 200 条结果（可以用搜索缩小范围）。</Text>
                ) : null}
              </Stack>
            </ScrollArea>
          </Tabs.Panel>

          <Tabs.Panel value="export" pt="sm" style={{ flex: 1, minHeight: 0 }}>
            <ScrollArea h="100%" type="auto" offsetScrollbars>
              <Stack gap="sm" pb="md">
                <Group justify="space-between">
                  <Text fw={600}>ZenScript（草案导出）</Text>
                  <Button size="xs" variant="light" leftSection={<IconDownload size={14} />} onClick={() => downloadText('pm_layout.zs', zs)}>
                    下载
                  </Button>
                </Group>
                <Code block style={{ whiteSpace: 'pre', overflow: 'auto' }}>{zs}</Code>

                <Divider label="布局 JSON" />
                <Button
                  size="xs"
                  variant="light"
                  leftSection={<IconDownload size={14} />}
                  onClick={() => downloadText('pm_layout.json', JSON.stringify(doc, null, 2))}
                >
                  下载 JSON
                </Button>
              </Stack>
            </ScrollArea>
          </Tabs.Panel>
        </Tabs>
      </Box>
    </Group>
  );
}
