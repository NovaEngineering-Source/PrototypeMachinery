import {
    ActionIcon,
    Autocomplete,
    Box,
    Button,
    Code,
    Divider,
    Group,
    NumberInput,
    ScrollArea,
    SegmentedControl,
    Stack,
    Text,
    TextInput
} from '@mantine/core';
import {useHotkeys} from '@mantine/hooks';
import {IconArrowBackUp, IconArrowForwardUp} from '@tabler/icons-react';
import {useEffect, useMemo, useState} from 'react';
import {fetchPmAssetIndex} from '../editor/assets/pmAssets';
import {MachineUiCanvasStage} from '../machine-ui/components/MachineUiCanvasStage';
import {useMachineUiStore} from '../machine-ui/store/machineUiStore';

export function MachineUiPage() {
  const [pmPaths, setPmPaths] = useState<string[] | null>(null);

  const doc = useMachineUiStore((s) => s.doc);
  const setDocName = useMachineUiStore((s) => s.setDocName);
  const setCanvasSize = useMachineUiStore((s) => s.setCanvasSize);
  const setGridSize = useMachineUiStore((s) => s.setGridSize);
  const setActiveBackground = useMachineUiStore((s) => s.setActiveBackground);
  const setBackgroundTexturePath = useMachineUiStore((s) => s.setBackgroundTexturePath);

  const canUndo = useMachineUiStore((s) => s.historyPast.length > 0);
  const canRedo = useMachineUiStore((s) => s.historyFuture.length > 0);
  const undo = useMachineUiStore((s) => s.undo);
  const redo = useMachineUiStore((s) => s.redo);

  useEffect(() => {
    let cancelled = false;
    fetchPmAssetIndex()
      .then((idx) => {
        if (cancelled) return;
        setPmPaths(idx.paths);
      })
      .catch(() => {
        if (cancelled) return;
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
  ]);

  const controllerBgCandidates = useMemo(() => {
    const base = ['gui/gui_controller_a.png', 'gui/gui_controller_b.png'];
    if (!pmPaths || pmPaths.length === 0) return base;
    const extra = pmPaths.filter((p) => p.startsWith('gui/gui_controller_') && p.endsWith('.png'));
    return Array.from(new Set([...base, ...extra])).sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const active = doc.options?.activeBackground ?? 'A';
  const texA = doc.options?.backgroundA?.texturePath ?? '';
  const texB = doc.options?.backgroundB?.texturePath ?? '';

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
      <Box style={{ flex: 1, minWidth: 520, minHeight: 0, overflow: 'hidden' }}>
        <Stack h="100%" gap="xs">
          <Group justify="space-between" wrap="nowrap">
            <Group gap="xs">
              <Text size="sm" c="dimmed">
                Machine UI 编辑器（骨架）
              </Text>
              <Text size="sm" c="dimmed">
                路由：<Code>/#/machine-ui</Code>
              </Text>
            </Group>

            <Group gap="xs">
              <ActionIcon variant="light" title="撤销 (Ctrl+Z)" disabled={!canUndo} onClick={() => undo()}>
                <IconArrowBackUp size={18} />
              </ActionIcon>
              <ActionIcon variant="light" title="重做 (Ctrl+Y)" disabled={!canRedo} onClick={() => redo()}>
                <IconArrowForwardUp size={18} />
              </ActionIcon>
            </Group>
          </Group>

          <Divider />

          <MachineUiCanvasStage />

          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              说明：当前仅支持背景 A/B 预览与基本画布设置；Widget 系统与导入导出稍后接入。
            </Text>
            <Button variant="subtle" disabled>
              导出（WIP）
            </Button>
          </Group>
        </Stack>
      </Box>

      {/* Right: inspector */}
      <Box style={{ width: 360, height: '100%', minHeight: 0, overflow: 'hidden' }}>
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

            <Divider label="背景" />

            <SegmentedControl
              fullWidth
              value={active}
              onChange={(v) => setActiveBackground((v as any) === 'B' ? 'B' : 'A')}
              data={[
                { label: 'Tab A (Main)', value: 'A' },
                { label: 'Tab B (Extension)', value: 'B' },
              ]}
            />

            <Autocomplete
              label="背景 A 贴图"
              description="默认：gui/gui_controller_a.png（参考 DefaultMachineUI.kt）"
              value={texA}
              data={controllerBgCandidates}
              onChange={(v) => setBackgroundTexturePath('A', v)}
            />

            <Autocomplete
              label="背景 B 贴图"
              description="默认：gui/gui_controller_b.png（参考 DefaultMachineUI.kt）"
              value={texB}
              data={controllerBgCandidates}
              onChange={(v) => setBackgroundTexturePath('B', v)}
            />

            <Text size="xs" c="dimmed">
              提示：后续会把背景的可交互区域（内容面板、tab bar、slot 区等）拆成可编辑的 layout guide。
            </Text>
          </Stack>
        </ScrollArea>
      </Box>
    </Group>
  );
}
