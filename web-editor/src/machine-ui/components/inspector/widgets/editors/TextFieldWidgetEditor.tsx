import {Autocomplete, Group, NumberInput, SegmentedControl, Stack, TextInput} from '@mantine/core';
import {MachineUiTextFieldWidget} from '../../../../model/ir';
import {guiStatesTextFieldSkins} from '../../../../model/guiStatesSkins';
import {useMachineUiStore} from '../../../../store/machineUiStore';

export function TextFieldWidgetEditor(props: { w: MachineUiTextFieldWidget }) {
  const { w } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  const inputType = String((w as any).inputType ?? 'string').trim().toLowerCase();
  const isLong = inputType === 'long';

  const setMinLong = (v: number | string) => {
    const n = typeof v === 'number' ? v : Number(v);
    updateWidget(w.id, { minLong: Number.isFinite(n) ? Math.trunc(n) : undefined } as any);
  };

  const setMaxLong = (v: number | string) => {
    const n = typeof v === 'number' ? v : Number(v);
    updateWidget(w.id, { maxLong: Number.isFinite(n) ? Math.trunc(n) : undefined } as any);
  };

  return (
    <Stack gap="xs">
      <Autocomplete
        label="skin（可选）"
        description='运行时皮肤 id（例如 gui_states/input_box/normal 或 gui_states/input_box/expand/normal）。'
        value={String((w as any).skin ?? '')}
        data={[...guiStatesTextFieldSkins] as unknown as string[]}
        onChange={(v) => updateWidget(w.id, { skin: v.trim() || undefined } as any)}
      />

      <TextInput
        label="valueKey（可选）"
        description="运行时 string 绑定 key。"
        value={String((w as any).valueKey ?? '')}
        onChange={(e) => updateWidget(w.id, { valueKey: e.currentTarget.value.trim() || undefined } as any)}
      />

      <Stack gap={6}>
        <SegmentedControl
          fullWidth
          value={isLong ? 'long' : 'string'}
          data={[
            { label: 'string', value: 'string' },
            { label: 'long', value: 'long' },
          ]}
          onChange={(v) => updateWidget(w.id, { inputType: v as any } as any)}
        />

        {isLong ? (
          <Group grow>
            <NumberInput
              label="minLong（可选）"
              value={(w as any).minLong ?? ''}
              step={1}
              onChange={setMinLong as any}
            />
            <NumberInput
              label="maxLong（可选）"
              value={(w as any).maxLong ?? ''}
              step={1}
              onChange={setMaxLong as any}
            />
          </Group>
        ) : null}
      </Stack>
    </Stack>
  );
}
