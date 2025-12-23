# Localization / i18n

This page documents the localization conventions used by PrototypeMachinery, so addon/script authors can provide their own language entries.

> Note: this project targets Minecraft 1.12.2 and uses the legacy `.lang` format (not the 1.13+ JSON format).

---

Chinese original:

- [`docs/Localization.md`](../Localization.md)

## Language file locations

Built-in language files:

- `src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- `src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

Third-party addons should usually ship their own language files under:

- `assets/<modid>/lang/<locale>.lang`

## How keys are used

Different UI paths use different translation helpers:

- `TextComponentTranslation(key, args...)` (chat messages, command feedback)
- `I18n.format(key, args...)` (client UI rendering)
- ModularUI `IKey.lang(key)` (GUI titles/widgets)

If a key is missing, Minecraft typically falls back to displaying the raw key.

## Common key conventions

### Machine Attributes

Built-in attribute translation keys follow:

- `attribute.<namespace>.<path>`

Example (from `zh_cn.lang`):

- `attribute.prototypemachinery.process_speed=...`

Implementation detail:

- `StandardMachineAttributes` builds keys as `"attribute.${'$'}{id.namespace}.${'$'}{id.path}"`.

See also:

- [Machine Attributes](./Attributes.md)

### Block / item names

Blocks/items generally use `translationKey` and language files provide the `.name` entry.

One example pattern for controller blocks:

- `prototypemachinery.machine.<machine_id>_controller`

Corresponding language entry:

- `prototypemachinery.machine.xxx_controller.name=...`

(The exact keys are implementation-defined; you can always print the `translationKey` during debugging to verify.)

### Hints / error messages

For structure preview, selective requirement errors, etc, the project tends to emit fixed keys, e.g.:

- `pm.preview.started`
- `error.selective.invalid_selection`

Keeping these keys centralized in language files makes translation maintenance easier.

## Recommendations for third-party content

- Prefer translation keys for any user-visible text (GUI titles/tooltips/errors) instead of hardcoded strings.
- Ship at least `en_us.lang` for your `<modid>`, and add other locales as needed.
