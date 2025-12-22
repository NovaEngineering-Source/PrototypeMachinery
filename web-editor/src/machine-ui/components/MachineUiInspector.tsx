import {Tabs} from '@mantine/core';
import {useState} from 'react';
import {DocTab} from './inspector/DocTab';
import {GuidesTab} from './inspector/GuidesTab';
import {IoTab} from './inspector/IoTab';
import {TabsTab} from './inspector/TabsTab';
import {WidgetsTab} from './inspector/WidgetsTab';
import {useMachineUiStore} from '../store/machineUiStore';

export type MachineUiInspectorProps = {
  pmPaths: string[] | null;
};
export function MachineUiInspector({ pmPaths }: MachineUiInspectorProps) {
  const [tab, setTab] = useState<string>('doc');
  const setMode = useMachineUiStore((s) => s.setMode);

  const onTabChange = (next: string | null) => {
    const v = next ?? 'doc';
    setTab(v);
    if (v === 'widgets') setMode('widgets');
    if (v === 'guides') setMode('guides');
  };

  return (
    <Tabs value={tab} onChange={onTabChange} style={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <Tabs.List>
        <Tabs.Tab value="doc">文档</Tabs.Tab>
        <Tabs.Tab value="tabs">Tabs</Tabs.Tab>
        <Tabs.Tab value="widgets">Widgets</Tabs.Tab>
        <Tabs.Tab value="guides">Guides</Tabs.Tab>
        <Tabs.Tab value="io">导入/导出</Tabs.Tab>
      </Tabs.List>

      <Tabs.Panel value="doc" pt="sm" style={{ flex: 1, minHeight: 0 }}>
        <DocTab />
      </Tabs.Panel>

      <Tabs.Panel value="tabs" pt="sm" style={{ flex: 1, minHeight: 0 }}>
        <TabsTab pmPaths={pmPaths} />
      </Tabs.Panel>

      <Tabs.Panel value="widgets" pt="sm" style={{ flex: 1, minHeight: 0 }}>
        <WidgetsTab pmPaths={pmPaths} />
      </Tabs.Panel>

      <Tabs.Panel value="guides" pt="sm" style={{ flex: 1, minHeight: 0 }}>
        <GuidesTab />
      </Tabs.Panel>

      <Tabs.Panel value="io" pt="sm" style={{ flex: 1, minHeight: 0 }}>
        <IoTab />
      </Tabs.Panel>
    </Tabs>
  );
}
