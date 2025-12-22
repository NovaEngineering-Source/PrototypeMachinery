import {Autocomplete, Group, NumberInput, Stack, Switch, TextInput} from '@mantine/core';
import {MachineUiSliderWidget} from '../../../../model/ir';
import {guiStatesSliderSkins} from '../../../../model/guiStatesSkins';
import {useMachineUiStore} from '../../../../store/machineUiStore';

export function SliderWidgetEditor(props: { w: MachineUiSliderWidget }) {
  const { w } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  return (
    <Stack gap="xs">
      <Autocomplete
        label="skin（可选）"
        description="运行时皮肤 id（例如 gui_states/slider/m/x_expand/normal）。"
        value={String((w as any).skin ?? '')}
        data={guiStatesSliderSkins as unknown as string[]}
        onChange={(v) => updateWidget(w.id, { skin: v.trim() || undefined } as any)}
      />

      <Group grow>
        <NumberInput
          label="min"
          value={Number((w as any).min ?? 0)}
          step={1}
          onChange={(v) => updateWidget(w.id, { min: Number(v) } as any)}
        />
        <NumberInput
          label="max"
          value={Number((w as any).max ?? 100)}
          step={1}
          onChange={(v) => updateWidget(w.id, { max: Number(v) } as any)}
        />
      </Group>

      <NumberInput
        label="step"
        value={Number((w as any).step ?? 1)}
        step={0.25}
        onChange={(v) => updateWidget(w.id, { step: Number(v) } as any)}
      />

      <TextInput
        label="valueKey（可选）"
        description="运行时 number 绑定 key / 表达式。"
        value={String((w as any).valueKey ?? '')}
        onChange={(e) => updateWidget(w.id, { valueKey: e.currentTarget.value.trim() || undefined } as any)}
      />

      <Switch
        label="horizontal"
        checked={Boolean((w as any).horizontal ?? true)}
        onChange={(e) => updateWidget(w.id, { horizontal: e.currentTarget.checked } as any)}
      />
    </Stack>
  );
}
