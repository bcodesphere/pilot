import * as CollapsiblePrimitive from '@radix-ui/react-collapsible';
import type { ComponentProps } from 'react';

/**
 * Sección plegable (`@radix-ui/react-collapsible` 1.1.20): la fila expandible de `TablaContable` y
 * los grupos de la barra lateral cuando no está contraída a íconos.
 */
export function Collapsible(props: ComponentProps<typeof CollapsiblePrimitive.Root>) {
  return <CollapsiblePrimitive.Root {...props} />;
}

export function CollapsibleTrigger(props: ComponentProps<typeof CollapsiblePrimitive.CollapsibleTrigger>) {
  return <CollapsiblePrimitive.CollapsibleTrigger {...props} />;
}

export function CollapsibleContent(props: ComponentProps<typeof CollapsiblePrimitive.CollapsibleContent>) {
  return <CollapsiblePrimitive.CollapsibleContent {...props} />;
}
