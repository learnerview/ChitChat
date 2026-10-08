import { useCallback, useEffect, useState } from 'react';

/** Multi-select mode for rows: enter with one id, toggle more, Escape to clear. */
export function useSelection() {
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);

  const exitSelect = useCallback(() => {
    setSelectMode(false);
    setSelected([]);
  }, []);

  useEffect(() => {
    if (!selectMode) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') exitSelect();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selectMode, exitSelect]);

  const enterSelection = useCallback((id: string) => {
    setSelected([id]);
    setSelectMode(true);
  }, []);

  const toggleSelected = useCallback((id: string) => {
    setSelected(prev => (prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]));
  }, []);

  return { selectMode, selected, enterSelection, exitSelect, toggleSelected };
}