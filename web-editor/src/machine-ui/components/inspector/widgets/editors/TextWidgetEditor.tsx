import {Group, NumberInput, SegmentedControl, Stack, Switch, TextInput} from '@mantine/core';
import {MachineUiTextWidget} from '../../../../model/ir';
import {useMachineUiStore} from '../../../../store/machineUiStore';

export function TextWidgetEditor({ w }: { w: MachineUiTextWidget }) {
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  return (
    <Stack gap="xs">
      <TextInput label="文本" value={String(w.text ?? '')} onChange={(e) => updateWidget(w.id, { text: e.currentTarget.value } as any)} />
      <Group grow>
        <NumberInput
          label="字号"
          value={Number((w as any).fontSize ?? 12)}
          min={1}
          step={1}
          onChange={(v) => updateWidget(w.id, { fontSize: Number(v) || 1 } as any)}
        />
        <TextInput
          label="颜色"
          placeholder="#e9ecef"
          value={String((w as any).color ?? '')}
          onChange={(e) => updateWidget(w.id, { color: e.currentTarget.value } as any)}
        />
      </Group>
      <Group grow>
        <SegmentedControl
          fullWidth
          value={String((w as any).align ?? 'left')}
          onChange={(v: string) => updateWidget(w.id, { align: v } as any)}
          data={[
            { label: '左', value: 'left' },
            { label: '中', value: 'center' },
            { label: '右', value: 'right' },
          ]}
        />
        <Switch
          label="阴影"
          checked={Boolean((w as any).shadow)}
          onChange={(e) => updateWidget(w.id, { shadow: e.currentTarget.checked } as any)}
        />
      </Group>
    </Stack>
  );
}
