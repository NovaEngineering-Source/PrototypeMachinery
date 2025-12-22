import {MachineUiTextWidget, MachineUiWidget} from '../../model/ir';

export function asContainerChildrenIds(w: MachineUiWidget): string[] {
  const t = (w as any).type;
  if (t !== 'panel' && t !== 'scroll_container') return [];
  const ids = (w as any).childrenIds;
  if (!Array.isArray(ids)) return [];
  return ids.map((x: any) => String(x ?? '').trim()).filter((x: string) => x.length > 0);
}

export function isTextWidget(w: MachineUiWidget): w is MachineUiTextWidget {
  return (w as any).type === 'text';
}
