import {ActionIcon, Menu} from '@mantine/core';
import {useHotkeys} from '@mantine/hooks';
import {IconArrowBackUp, IconArrowForwardUp, IconDeviceFloppy, IconDownload, IconFileImport} from '@tabler/icons-react';
import {useEffect, useMemo, useRef} from 'react';
import {downloadText} from '../../editor/io/download';
import {exportMachineUiRuntimeJson} from '../exporters/jsonExporter';
import {exportMachineUiZenScript} from '../exporters/zsExporter';
import {useMachineUiStore} from '../store/machineUiStore';

export function MachineUiToolbar() {
  const importInputRef = useRef<HTMLInputElement | null>(null);

  const doc = useMachineUiStore((s) => s.doc);
  const importFromJson = useMachineUiStore((s) => s.importFromJson);
  const saveToLocal = useMachineUiStore((s) => s.saveToLocal);
  const loadFromLocal = useMachineUiStore((s) => s.loadFromLocal);

  const canUndo = useMachineUiStore((s) => s.historyPast.length > 0);
  const canRedo = useMachineUiStore((s) => s.historyFuture.length > 0);
  const undo = useMachineUiStore((s) => s.undo);
  const redo = useMachineUiStore((s) => s.redo);

  const deleteSelected = useMachineUiStore((s) => s.deleteSelected);
  const duplicateSelectedWidget = useMachineUiStore((s) => s.duplicateSelectedWidget);

  const nudgeSelectionLive = useMachineUiStore((s) => s.nudgeSelectionLive);
  const commitNudgeBatch = useMachineUiStore((s) => s.commitNudgeBatch);

  const shouldIgnoreEditorHotkey = () => {
    const el = document.activeElement as HTMLElement | null;
    if (!el) return false;
    const tag = el.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
    if ((el as any).isContentEditable) return true;
    return false;
  };

  // Arrow keys nudge: Arrow = 1px, Shift+Arrow = gridSize. Batch into one undo step.
  useEffect(() => {
    let timer: number | undefined;

    const scheduleCommit = () => {
      if (timer) window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        commitNudgeBatch();
      }, 250);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (shouldIgnoreEditorHotkey()) return;
      const key = e.key;
      if (key !== 'ArrowUp' && key !== 'ArrowDown' && key !== 'ArrowLeft' && key !== 'ArrowRight') return;

      e.preventDefault();

      const grid = Math.max(1, Math.floor(Number(doc.options?.gridSize ?? 8)));
      const step = e.shiftKey ? grid : 1;

      let dx = 0;
      let dy = 0;
      if (key === 'ArrowUp') dy = -step;
      if (key === 'ArrowDown') dy = step;
      if (key === 'ArrowLeft') dx = -step;
      if (key === 'ArrowRight') dx = step;

      nudgeSelectionLive(dx, dy);
      scheduleCommit();
    };

    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      if (timer) window.clearTimeout(timer);
    };
  }, [commitNudgeBatch, doc.options?.gridSize, nudgeSelectionLive]);

  useHotkeys([
    ['mod+z', () => undo(), { preventDefault: true }],
    ['mod+shift+z', () => redo(), { preventDefault: true }],
    ['mod+y', () => redo(), { preventDefault: true }],
    [
      'delete',
      () => {
        if (shouldIgnoreEditorHotkey()) return;
        deleteSelected();
      },
    ],
    [
      'backspace',
      () => {
        if (shouldIgnoreEditorHotkey()) return;
        deleteSelected();
      },
    ],
    [
      'mod+d',
      () => {
        if (shouldIgnoreEditorHotkey()) return;
        duplicateSelectedWidget();
      },
    ],
  ]);

  const editorJson = useMemo(() => JSON.stringify(doc, null, 2), [doc]);
  const runtimeJson = useMemo(() => JSON.stringify(exportMachineUiRuntimeJson(doc), null, 2), [doc]);
  const zenScriptRuntimeJson = useMemo(() => exportMachineUiZenScript(doc, { format: 'runtime-json' }), [doc]);
  const zenScriptBuilders = useMemo(() => exportMachineUiZenScript(doc, { format: 'builders' }), [doc]);

  const handleImportJsonFile = async (file: File | undefined) => {
    if (!file) return;
    try {
      const text = await file.text();
      const raw = JSON.parse(text);
      importFromJson(raw);
    } catch (e) {
      console.error('Import machine ui json failed', e);
    } finally {
      // allow re-importing same file
      if (importInputRef.current) importInputRef.current.value = '';
    }
  };

  return (
    <>
      <input
        ref={importInputRef}
        type="file"
        accept="application/json,.json"
        style={{ display: 'none' }}
        onChange={(e) => {
          const file = e.currentTarget.files?.[0];
          void handleImportJsonFile(file);
        }}
      />

      <ActionIcon variant="light" title="撤销 (Ctrl+Z)" disabled={!canUndo} onClick={() => undo()}>
        <IconArrowBackUp size={18} />
      </ActionIcon>
      <ActionIcon variant="light" title="重做 (Ctrl+Y)" disabled={!canRedo} onClick={() => redo()}>
        <IconArrowForwardUp size={18} />
      </ActionIcon>

      <ActionIcon variant="light" title="从 JSON 导入" onClick={() => importInputRef.current?.click()}>
        <IconFileImport size={18} />
      </ActionIcon>
      <ActionIcon variant="light" title="保存到 localStorage" onClick={() => saveToLocal()}>
        <IconDeviceFloppy size={18} />
      </ActionIcon>
      <ActionIcon variant="light" title="从 localStorage 载入" onClick={() => loadFromLocal()}>
        <IconFileImport size={18} />
      </ActionIcon>

      <Menu withinPortal position="bottom-end">
        <Menu.Target>
          <ActionIcon variant="light" title="导出">
            <IconDownload size={18} />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item onClick={() => downloadText('pm_machine_ui.editor.json', editorJson)}>下载 JSON（含 editor 元数据）</Menu.Item>
          <Menu.Item onClick={() => downloadText('pm_machine_ui.runtime.json', runtimeJson)}>下载 JSON（runtime）</Menu.Item>
          <Menu.Item onClick={() => downloadText('pm_machine_ui.runtime-json.zs', zenScriptRuntimeJson)}>下载 ZenScript（runtime-json，内嵌 JSON）</Menu.Item>
          <Menu.Item onClick={() => downloadText('pm_machine_ui.builders.zs', zenScriptBuilders)}>下载 ZenScript（builders / 原生）</Menu.Item>
        </Menu.Dropdown>
      </Menu>
    </>
  );
}
