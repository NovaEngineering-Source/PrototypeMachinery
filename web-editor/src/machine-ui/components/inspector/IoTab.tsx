import {Box, Code, Divider, ScrollArea, Stack, Text} from '@mantine/core';
import {useMemo} from 'react';
import {exportMachineUiRuntimeJson} from '../../exporters/jsonExporter';
import {exportMachineUiZenScript} from '../../exporters/zsExporter';
import {useMachineUiStore} from '../../store/machineUiStore';

export function IoTab() {
  const doc = useMachineUiStore((s) => s.doc);

  const editorJson = useMemo(() => JSON.stringify(doc, null, 2), [doc]);
  const runtimeJson = useMemo(() => JSON.stringify(exportMachineUiRuntimeJson(doc), null, 2), [doc]);
  const zenScript = useMemo(() => exportMachineUiZenScript(doc), [doc]);

  return (
    <ScrollArea h="100%" type="auto" offsetScrollbars>
      <Stack gap="sm" pb="md">
        <Text size="sm" c="dimmed">
          说明：导入/导出在顶部工具栏也可用；这里提供内容预览便于复制。
        </Text>

        <Divider label="Editor JSON" />
        <Box>
          <Code block style={{ whiteSpace: 'pre', overflow: 'auto' }}>{editorJson}</Code>
        </Box>

        <Divider label="Runtime JSON" />
        <Box>
          <Code block style={{ whiteSpace: 'pre', overflow: 'auto' }}>{runtimeJson}</Code>
        </Box>

        <Divider label="ZenScript（Runtime JSON）" />
        <Box>
          <Code block style={{ whiteSpace: 'pre', overflow: 'auto' }}>{zenScript}</Code>
        </Box>
      </Stack>
    </ScrollArea>
  );
}
