import { useCallback, useState } from 'react';

/** Context-menu anchor state shared by every right-clickable surface. */
export interface ContextMenuState<T> {
  x: number;
  y: number;
  target: T;
}

export function useContextMenu<T>() {
  const [menu, setMenu] = useState<ContextMenuState<T> | null>(null);

  const openMenu = useCallback((event: React.MouseEvent, target: T) => {
    event.preventDefault();
    setMenu({ x: event.clientX, y: event.clientY, target });
  }, []);

  const closeMenu = useCallback(() => setMenu(null), []);

  return { menu, openMenu, closeMenu };
}
