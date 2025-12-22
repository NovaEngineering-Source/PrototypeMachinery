// gui_states skin ids used by PrototypeMachinery runtime UI.
// These are NOT texture paths; they are semantic ids resolved by the mod at runtime.

export const guiStatesButtonSkins = [
  'gui_states/empty_button/normal',
  'gui_states/empty_button/shadow',
  'gui_states/expand_button/normal',
  'gui_states/expand_button/shadow',
] as const;

export const guiStatesToggleSkins = ['gui_states/switch/normal', 'gui_states/switch/shadow'] as const;

const sliderSizes = ['s', 'm'] as const;
const sliderAxes = ['x', 'x_expand', 'y', 'y_expand'] as const;
const sliderLooks = ['normal', 'shadow'] as const;

export const guiStatesSliderSkins = sliderSizes.flatMap((s) =>
  sliderAxes.flatMap((a) => sliderLooks.map((l) => `gui_states/slider/${s}/${a}/${l}` as const)),
);

export const guiStatesTextFieldSkins = [
  'gui_states/input_box/normal',
  'gui_states/input_box/shadow',
  'gui_states/input_box/expand/normal',
  'gui_states/input_box/expand/shadow',
] as const;

export const guiStatesAllSkins = [
  ...guiStatesButtonSkins,
  ...guiStatesToggleSkins,
  ...guiStatesSliderSkins,
  ...guiStatesTextFieldSkins,
] as const;
