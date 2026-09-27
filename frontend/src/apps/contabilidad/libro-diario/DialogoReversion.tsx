import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { revertirAsiento } from '@/api/asientos/asientos';
import type { Asiento } from '@/api/modelos';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Dialogo } from '@/compartido/ui/dialog';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { mensajeAsiento, MENSAJES_CONTABILIDAD } from '../mensajesContabilidad';
import { invalidarLibroDiario, numeroAsiento } from './etiquetas';
import { useClaveIdempotencia } from './useClaveIdempotencia';

/** Propiedades del diálogo de reversión. */
interface Props {
  /** Asiento que se revierte (CONTABILIZADO y que no es una reversión). */
  asiento: Asiento;
  /** Cierra el diálogo sin revertir. */
  onCerrar: () => void;
}

/**
 * Diálogo "Revertir asiento" (rol contador, CLAUDE.md §10.1, ADR-036): pide la fecha de la reversión (por
 * defecto hoy en El Salvador; no puede ser futura ni anterior a la del asiento original) y llama a
 * `POST /contabilidad/asientos/{id}/reversion`. Al terminar navega al asiento de reversión.
 *
 * El `Idempotency-Key` se conserva para reintentos de la misma reversión (mismo asiento y misma fecha) y cambia
 * si cambia la fecha (ver {@link useClaveIdempotencia}).
 */
export function DialogoReversion({ asiento, onCerrar }: Props) {
  const navegar = useNavigate();
  const clienteConsultas = useQueryClient();
  const obtenerClave = useClaveIdempotencia();
  const [hoy] = useState(() => hoyElSalvador());
  const [fecha, setFecha] = useState(hoy);

  // 1. Validación local con los mismos textos que el backend: CON-007 (futura) y CON-018 (anterior al original)
  const errorLocal =
    fecha === ''
      ? 'La fecha es obligatoria.'
      : fecha > hoy
        ? MENSAJES_CONTABILIDAD['CON-007']
        : fecha < asiento.fecha
          ? MENSAJES_CONTABILIDAD['CON-018']
          : null;

  // 2. Reversión: crea un asiento con Debe y Haber intercambiados y marca el original REVERTIDO
  const revertir = useMutation({
    mutationFn: () => {
      const cuerpo = { fecha };
      return revertirAsiento(asiento.id, cuerpo, {
        headers: { 'Idempotency-Key': obtenerClave(JSON.stringify({ asientoId: asiento.id, ...cuerpo })) },
      });
    },
    onSuccess: async (respuesta) => {
      const reversion = respuesta.data as Asiento;
      await invalidarLibroDiario(clienteConsultas);
      navegar(`/contabilidad/libro-diario/${reversion.id}`, {
        state: {
          aviso: `Asiento N.º ${numeroAsiento(asiento)} revertido con el asiento N.º ${numeroAsiento(reversion)}`,
        },
      });
    },
  });

  return (
    <Dialogo
      titulo={`Revertir el asiento N.º ${numeroAsiento(asiento)}`}
      onCerrar={onCerrar}
      cerrable={!revertir.isPending}
    >
      <p className="text-sm text-neutral-600">
        Se creará un asiento con el Debe y el Haber intercambiados. El asiento original no se modifica salvo
        por pasar a «Revertido».
      </p>
      <div className="space-y-1">
        <Label htmlFor="reversion-fecha">Fecha de la reversión</Label>
        <Input
          id="reversion-fecha"
          type="date"
          min={asiento.fecha}
          max={hoy}
          value={fecha}
          aria-invalid={errorLocal ? true : undefined}
          aria-describedby={errorLocal ? 'error-reversion-fecha' : undefined}
          onChange={(e) => setFecha(e.target.value)}
        />
        {errorLocal && (
          <p id="error-reversion-fecha" className="text-sm text-red-700">
            {errorLocal}
          </p>
        )}
      </div>
      {revertir.isError && <Alert variant="error">{mensajeAsiento(revertir.error)}</Alert>}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCerrar} disabled={revertir.isPending}>
          Cancelar
        </Button>
        <Button onClick={() => revertir.mutate()} disabled={errorLocal !== null || revertir.isPending}>
          {revertir.isPending ? 'Revirtiendo…' : 'Revertir asiento'}
        </Button>
      </div>
    </Dialogo>
  );
}
