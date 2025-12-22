import {ActionIcon, Autocomplete, Button, Divider, Group, ScrollArea, Stack, Text, TextInput} from '@mantine/core';
import {IconPlus, IconTrash} from '@tabler/icons-react';
import {useMemo} from 'react';
import {useMachineUiStore} from '../../store/machineUiStore';

export type TabsTabProps = {
  pmPaths: string[] | null;
};

export function TabsTab({ pmPaths }: TabsTabProps) {
  const doc = useMachineUiStore((s) => s.doc);

  const setActiveTabId = useMachineUiStore((s) => s.setActiveTabId);
  const addTab = useMachineUiStore((s) => s.addTab);
  const removeTab = useMachineUiStore((s) => s.removeTab);
  const updateTab = useMachineUiStore((s) => s.updateTab);

  const setBackgroundTexturePath = useMachineUiStore((s) => s.setBackgroundTexturePath);

  const activeLegacy = doc.options?.activeBackground ?? 'A';
  const activeTabId = doc.options?.activeTabId ?? activeLegacy;
  const tabs = doc.options?.tabs ?? [
    { id: 'A', label: 'Main', texturePath: doc.options?.backgroundA?.texturePath ?? '' },
    { id: 'B', label: 'Extension', texturePath: doc.options?.backgroundB?.texturePath ?? '' },
  ];

  const texA = doc.options?.backgroundA?.texturePath ?? '';
  const texB = doc.options?.backgroundB?.texturePath ?? '';

  const controllerBgCandidates = useMemo(() => {
    const base = ['gui/gui_controller_a.png', 'gui/gui_controller_b.png'];
    if (!pmPaths || pmPaths.length === 0) return base;
    const extra = pmPaths.filter((p) => p.startsWith('gui/gui_controller_') && p.endsWith('.png'));
    return Array.from(new Set([...base, ...extra])).sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const guiTextureCandidates = useMemo(() => {
    if (!pmPaths || pmPaths.length === 0) return controllerBgCandidates;
    return pmPaths.filter((p) => p.startsWith('gui/') && p.endsWith('.png')).sort((a, b) => a.localeCompare(b));
  }, [pmPaths, controllerBgCandidates]);

  return (
    <ScrollArea h="100%" type="auto" offsetScrollbars>
      <Stack gap="sm" pb="md">
        <Group justify="space-between" wrap="nowrap">
          <Text size="sm" c="dimmed">
            当前 Tab：{activeTabId}
          </Text>
          <Button size="xs" variant="light" leftSection={<IconPlus size={14} />} onClick={() => addTab({ label: 'Custom' })}>
            新增 Tab
          </Button>
        </Group>

        <Stack gap={6}>
          {tabs.map((t) => {
            const selected = t.id === activeTabId;
            const textureValue = String(t.texturePath ?? '').trim();
            return (
              <Stack
                key={t.id}
                gap={6}
                style={{
                  padding: '8px 10px',
                  borderRadius: 8,
                  background: selected ? 'rgba(255,255,255,0.06)' : 'transparent',
                  border: selected ? '1px solid rgba(255,255,255,0.14)' : '1px solid transparent',
                }}
              >
                <Group justify="space-between" wrap="nowrap">
                  <Group gap={8} wrap="nowrap">
                    <Button size="xs" variant={selected ? 'light' : 'subtle'} onClick={() => setActiveTabId(t.id)}>
                      {t.id}
                    </Button>
                    <TextInput
                      placeholder="label"
                      value={String(t.label ?? '')}
                      onChange={(e) => updateTab(t.id, { label: e.currentTarget.value || undefined })}
                    />
                  </Group>
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    title="删除 Tab"
                    onClick={() => removeTab(t.id)}
                    disabled={(doc.options?.tabs?.length ?? 2) <= 1}
                  >
                    <IconTrash size={18} />
                  </ActionIcon>
                </Group>

                <Autocomplete
                  label="背景贴图"
                  value={textureValue}
                  data={guiTextureCandidates}
                  onChange={(v) => updateTab(t.id, { texturePath: v.trim() || undefined })}
                />
              </Stack>
            );
          })}
        </Stack>

        <Divider label="背景（兼容旧字段）" />
        <Text size="xs" c="dimmed">
          说明：Tabs 已可自定义；下面的 A/B 背景输入仅用于兼容旧数据结构。
        </Text>
        <Group grow>
          <Autocomplete label="背景 A" value={texA} data={controllerBgCandidates} onChange={(v) => setBackgroundTexturePath('A', v)} />
          <Autocomplete label="背景 B" value={texB} data={controllerBgCandidates} onChange={(v) => setBackgroundTexturePath('B', v)} />
        </Group>
      </Stack>
    </ScrollArea>
  );
}
