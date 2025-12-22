import {Autocomplete, Button, Group, NumberInput, SegmentedControl, Stack, Text, TextInput} from '@mantine/core';
import {MachineUiProgressBarWidget} from '../../../../model/ir';
import {useMachineUiStore} from '../../../../store/machineUiStore';
import {usePmTextureSize} from '../../utils';

export function ProgressWidgetEditor(props: {
  w: MachineUiProgressBarWidget;
  pmPaths: string[] | null;
  progressBaseCandidates: string[];
  progressRunCandidates: string[];
}) {
  const { w, pmPaths, progressBaseCandidates, progressRunCandidates } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  const basePath = String((w as any).baseTexturePath ?? '').trim() || undefined;
  const runPath = String((w as any).runTexturePath ?? '').trim() || undefined;

  const progressBaseTexSize = usePmTextureSize(basePath);
  const progressRunTexSize = usePmTextureSize(runPath);

  return (
    <Stack gap="xs">
      <NumberInput
        label="预览进度"
        description="0..1（编辑器预览用）"
        value={Number((w as any).progress ?? 0)}
        min={0}
        max={1}
        step={0.05}
        onChange={(v) => updateWidget(w.id, { progress: Number(v) || 0 } as any)}
      />

      <SegmentedControl
        fullWidth
        value={String((w as any).direction ?? 'right')}
        onChange={(v: string) => updateWidget(w.id, { direction: v } as any)}
        data={[
          { label: '→', value: 'right' },
          { label: '←', value: 'left' },
          { label: '↓', value: 'down' },
          { label: '↑', value: 'up' },
        ]}
      />

      <Group grow>
        <TextInput
          label="填充色"
          placeholder="#51cf66"
          value={String((w as any).fillColor ?? '')}
          onChange={(e) => updateWidget(w.id, { fillColor: e.currentTarget.value } as any)}
        />
        <TextInput
          label="背景色"
          placeholder="rgba(0,0,0,0.25)"
          value={String((w as any).bgColor ?? '')}
          onChange={(e) => updateWidget(w.id, { bgColor: e.currentTarget.value } as any)}
        />
      </Group>

      <Autocomplete
        label="Base 贴图（可选）"
        description="选择后会渲染 base/run 贴图；不填则使用纯色。"
        value={String((w as any).baseTexturePath ?? '')}
        data={progressBaseCandidates}
        onChange={(v) => {
          const trimmed = v.trim();
          const patch: any = { baseTexturePath: trimmed || undefined };
          const curRun = String((w as any).runTexturePath ?? '').trim();
          if (!curRun && trimmed.endsWith('_base.png')) {
            const inferred = trimmed.replace(/_base\.png$/, '_run.png');
            if (pmPaths?.includes(inferred)) patch.runTexturePath = inferred;
          }
          updateWidget(w.id, patch);
        }}
      />

      <Autocomplete
        label="Run 贴图（可选）"
        value={String((w as any).runTexturePath ?? '')}
        data={progressRunCandidates}
        onChange={(v) => updateWidget(w.id, { runTexturePath: v.trim() || undefined } as any)}
      />

      <Stack gap={6}>
        <Text size="xs" c="dimmed">
          Base 尺寸：{progressBaseTexSize ? `${progressBaseTexSize.w}×${progressBaseTexSize.h}` : '（未加载/无）'}{'  '}
          Run 尺寸：{progressRunTexSize ? `${progressRunTexSize.w}×${progressRunTexSize.h}` : '（未加载/无）'}
        </Text>
        <Group grow>
          <Button
            size="xs"
            variant="light"
            disabled={!progressBaseTexSize}
            onClick={() => {
              if (!progressBaseTexSize) return;
              updateWidget(w.id, { w: progressBaseTexSize.w, h: progressBaseTexSize.h } as any);
            }}
          >
            按 Base 适配大小
          </Button>
          <Button
            size="xs"
            variant="light"
            disabled={!progressRunTexSize}
            onClick={() => {
              if (!progressRunTexSize) return;
              updateWidget(w.id, { w: progressRunTexSize.w, h: progressRunTexSize.h } as any);
            }}
          >
            按 Run 适配大小
          </Button>
        </Group>
      </Stack>

      <TextInput
        label="progressKey"
        description="运行时绑定 key / 表达式（期望 0..1）。例如：norm(demo_slider;1;100)"
        value={String((w as any).progressKey ?? '')}
        onChange={(e) => updateWidget(w.id, { progressKey: e.currentTarget.value.trim() || undefined } as any)}
      />

      <TextInput
        label="tooltipTemplate"
        description={'运行时 tooltip 模板（String.format）。例如："Progress: %s"'}
        value={String((w as any).tooltipTemplate ?? '')}
        onChange={(e) => updateWidget(w.id, { tooltipTemplate: e.currentTarget.value || undefined } as any)}
      />
    </Stack>
  );
}
