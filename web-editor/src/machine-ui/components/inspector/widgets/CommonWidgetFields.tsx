import {Autocomplete, TextInput} from '@mantine/core';
import {useMachineUiStore} from '../../../store/machineUiStore';

export function CommonWidgetFields(props: {
  widgetId: string;
  tabIdCandidates: string[];
  tabId?: string;
  visibleIf?: string;
  enabledIf?: string;
}) {
  const { widgetId, tabIdCandidates, tabId, visibleIf, enabledIf } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  return (
    <>
      <Autocomplete
        label="所属 Tab（留空=全局）"
        value={String(tabId ?? '')}
        data={tabIdCandidates}
        onChange={(v) => updateWidget(widgetId, { tabId: v.trim() || undefined } as any)}
      />

      <TextInput
        label="visibleIf（运行时）"
        description="bool 绑定 key 或表达式（例如：not(flag)、and(a;b)、or(a;b)）。留空=总是可见。"
        value={String(visibleIf ?? '')}
        onChange={(e) => updateWidget(widgetId, { visibleIf: e.currentTarget.value.trim() || undefined } as any)}
      />

      <TextInput
        label="enabledIf（运行时）"
        description="bool 绑定 key 或表达式。留空=总是可交互。"
        value={String(enabledIf ?? '')}
        onChange={(e) => updateWidget(widgetId, { enabledIf: e.currentTarget.value.trim() || undefined } as any)}
      />
    </>
  );
}
