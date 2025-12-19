export function saveToLocalStorage(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (e) {
    console.error('saveToLocalStorage failed', e);
  }
}

export function loadFromLocalStorage(key: string): unknown | undefined {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return undefined;
    return JSON.parse(raw);
  } catch (e) {
    console.error('loadFromLocalStorage failed', e);
    return undefined;
  }
}
