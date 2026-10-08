/** Small UI helpers shared across dialogs and panels. */

/** Human-readable message from an unknown thrown value. */
export function errorMessage(err: unknown, fallback = 'Something went wrong'): string {
  return err instanceof Error ? err.message : fallback;
}

/** Copy text to the clipboard, ignoring the (unhandled) promise rejection. */
export function copyText(value: string): void {
  void navigator.clipboard.writeText(value);
}
