import {Autocomplete, Group, Stack, TextInput} from '@mantine/core';
import {MachineUiToggleWidget} from '../../../../model/ir';
import {guiStatesToggleSkins} from '../../../../model/guiStatesSkins';
import {useMachineUiStore} from '../../../../store/machineUiStore';

export function ToggleWidgetEditor(props: { w: MachineUiToggleWidget; guiTextureCandidates: string[] }) {
  const { w, guiTextureCandidates } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  return (
    <Stack gap="xs">
      <Autocomplete
        label="skin（可选）"
        description="运行时皮肤 id（例如 gui_states/switch/normal）。设置后，textureOn/Off 仅作为非 gui_states 的备用。"
        value={String((w as any).skin ?? '')}
        data={[...guiStatesToggleSkins] as unknown as string[]}
        onChange={(v) => updateWidget(w.id, { skin: v.trim() || undefined } as any)}
      />

      <TextInput
        label="stateKey（可选）"
        description="运行时 bool 绑定 key / 表达式。"
        value={String((w as any).stateKey ?? '')}
        onChange={(e) => updateWidget(w.id, { stateKey: e.currentTarget.value.trim() || undefined } as any)}
      />

      <Group grow>
        <TextInput
          label="textOn（可选）"
          value={String((w as any).textOn ?? '')}
          onChange={(e) => updateWidget(w.id, { textOn: e.currentTarget.value || undefined } as any)}
        />
        <TextInput
          label="textOff（可选）"
          value={String((w as any).textOff ?? '')}
          onChange={(e) => updateWidget(w.id, { textOff: e.currentTarget.value || undefined } as any)}
        />
      </Group>

      <Group grow>
        <Autocomplete
          label="textureOn（可选）"
          value={String((w as any).textureOn ?? '')}
          data={guiTextureCandidates}
          onChange={(v) => updateWidget(w.id, { textureOn: v.trim() || undefined } as any)}
        />
        <Autocomplete
          label="textureOff（可选）"
          value={String((w as any).textureOff ?? '')}
          data={guiTextureCandidates}
          onChange={(v) => updateWidget(w.id, { textureOff: v.trim() || undefined } as any)}
        />
      </Group>
    </Stack>
  );
}
