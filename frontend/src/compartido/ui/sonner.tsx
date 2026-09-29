import { Toaster as Sonner, type ToasterProps } from 'sonner';
import type { CSSProperties } from 'react';

/**
 * Avisos flotantes (`sonner` 2.0.8): se monta una vez en `nucleo/Proveedores.tsx`. Solo tema claro
 * (ADR-043 §6): el oscuro queda para una versión posterior.
 */
export function Toaster(props: ToasterProps) {
  return (
    <Sonner
      theme="light"
      className="toaster group"
      position="bottom-right"
      style={
        {
          '--normal-bg': 'var(--color-superficie)',
          '--normal-text': 'var(--color-texto)',
          '--normal-border': 'var(--color-borde)',
        } as CSSProperties
      }
      {...props}
    />
  );
}
