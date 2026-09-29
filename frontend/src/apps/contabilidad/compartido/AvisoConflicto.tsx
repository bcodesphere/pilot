import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { MENSAJE_CONFLICTO } from '../mensajesContabilidad';

/**
 * Aviso de conflicto de versión (412 `PLT-016`): otra persona cambió el dato mientras se editaba.
 * Ofrece "Recargar" para volver a leerlo con su ETag nuevo (CLAUDE.md §8.3), igual que el espacio de trabajo.
 * @param props.onRecargar vuelve a pedir el dato al servidor
 */
export function AvisoConflicto({ onRecargar }: { onRecargar: () => void }) {
  return (
    <Alert variant="warning" className="flex items-center justify-between gap-2">
      <span>{MENSAJE_CONFLICTO}</span>
      <Button size="sm" variant="outline" onClick={onRecargar}>
        Recargar
      </Button>
    </Alert>
  );
}
