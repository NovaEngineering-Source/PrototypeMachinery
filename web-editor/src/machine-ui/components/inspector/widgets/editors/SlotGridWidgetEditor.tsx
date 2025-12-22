import {Autocomplete, Button, Group, NumberInput, Stack, Switch, Text, TextInput} from '@mantine/core';
import {MachineUiSlotGridWidget} from '../../../../model/ir';
import {useMachineUiStore} from '../../../../store/machineUiStore';
import {computeSlotGridSize, usePmTextureSize} from '../../utils';

export function SlotGridWidgetEditor(props: { w: MachineUiSlotGridWidget; slotTextureCandidates: string[] }) {
  const { w, slotTextureCandidates } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  const selectedSlotTexPath = String((w as any).slotTexturePath ?? '').trim() || undefined;
  const slotTexSize = usePmTextureSize(selectedSlotTexPath);

  return (
    <Stack gap="xs">
      <Group grow>
        <NumberInput
          label="列"
          value={Number((w as any).cols ?? 1)}
          min={1}
          step={1}
          onChange={(v) => {
            const next = computeSlotGridSize(Number(v), Number((w as any).rows ?? 1), Number((w as any).slotSize ?? 18), Number((w as any).gap ?? 0));
            updateWidget(w.id, { cols: next.cols, w: next.w, h: next.h } as any);
          }}
        />
        <NumberInput
          label="行"
          value={Number((w as any).rows ?? 1)}
          min={1}
          step={1}
          onChange={(v) => {
            const next = computeSlotGridSize(Number((w as any).cols ?? 1), Number(v), Number((w as any).slotSize ?? 18), Number((w as any).gap ?? 0));
            updateWidget(w.id, { rows: next.rows, w: next.w, h: next.h } as any);
          }}
        />
      </Group>

      <Group grow>
        <NumberInput
          label="格子尺寸"
          value={Number((w as any).slotSize ?? 18)}
          min={1}
          step={1}
          onChange={(v) => {
            const next = computeSlotGridSize(Number((w as any).cols ?? 1), Number((w as any).rows ?? 1), Number(v), Number((w as any).gap ?? 0));
            updateWidget(w.id, { slotSize: next.slotSize, w: next.w, h: next.h } as any);
          }}
        />
        <NumberInput
          label="间距"
          value={Number((w as any).gap ?? 0)}
          min={0}
          step={1}
          onChange={(v) => {
            const next = computeSlotGridSize(Number((w as any).cols ?? 1), Number((w as any).rows ?? 1), Number((w as any).slotSize ?? 18), Number(v));
            updateWidget(w.id, { gap: next.gap, w: next.w, h: next.h } as any);
          }}
        />
      </Group>

      <Autocomplete
        label="Slot 贴图"
        description="可留空：表示不渲染 slot 贴图（由背景贴图提供槽位外观）。"
        value={String((w as any).slotTexturePath ?? '')}
        data={slotTextureCandidates}
        onChange={(v) => updateWidget(w.id, { slotTexturePath: v.trim() || undefined } as any)}
      />

      <Group justify="space-between" wrap="nowrap">
        <Text size="xs" c="dimmed">
          贴图尺寸：{slotTexSize ? `${slotTexSize.w}×${slotTexSize.h}` : '（未加载/无）'}
        </Text>
        <Button
          size="xs"
          variant="light"
          disabled={!slotTexSize}
          onClick={() => {
            if (!slotTexSize) return;
            // slot grid assumes square slots; use min(w,h) to be safe
            const nextSlot = Math.max(1, Math.min(slotTexSize.w, slotTexSize.h));
            const cols = Number((w as any).cols ?? 1);
            const rows = Number((w as any).rows ?? 1);
            const gap = Number((w as any).gap ?? 0);
            const next = computeSlotGridSize(cols, rows, nextSlot, gap);
            updateWidget(w.id, { slotSize: next.slotSize, w: next.w, h: next.h } as any);
          }}
        >
          按贴图适配格子
        </Button>
      </Group>

      <Group grow>
        <TextInput
          label="slotKey"
          description="运行时槽位组 key（ItemSlotGroupBuilder）。"
          value={String((w as any).slotKey ?? 'default')}
          onChange={(e) => updateWidget(w.id, { slotKey: e.currentTarget.value.trim() || undefined } as any)}
        />
        <NumberInput
          label="startIndex"
          description="运行时槽位起始 index。"
          value={Number((w as any).startIndex ?? 0)}
          min={0}
          step={1}
          onChange={(v) => updateWidget(w.id, { startIndex: Math.max(0, Math.floor(Number(v) || 0)) } as any)}
        />
      </Group>

      <Group grow>
        <Switch
          label="允许插入"
          checked={Boolean((w as any).canInsert ?? true)}
          onChange={(e) => updateWidget(w.id, { canInsert: e.currentTarget.checked } as any)}
        />
        <Switch
          label="允许取出"
          checked={Boolean((w as any).canExtract ?? true)}
          onChange={(e) => updateWidget(w.id, { canExtract: e.currentTarget.checked } as any)}
        />
      </Group>
    </Stack>
  );
}
