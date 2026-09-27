import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useObtenerAsiento } from '@/api/asientos/asientos';
import type { Asiento } from '@/api/modelos';
import { formatearMoneda } from '@/compartido/dinero';
import { formatearFechaHora } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { DialogoReversion } from './DialogoReversion';
import { ETIQUETA_ORIGEN, filasDeAsiento, numeroAsiento } from './etiquetas';
import { InsigniaEstado, TablaLineas } from './presentacion';

/** Texto del modo de precio con que se separó el IVA (ADR-015). */
const ETIQUETA_MODO = { CON_IVA: 'Precios con IVA incluido', SIN_IVA: 'Precios más IVA' } as const;

/**
 * Pantalla de detalle de un asiento (`/contabilidad/libro-diario/:asientoId`): cabecera, líneas (las de IVA
 * marcadas), totales y enlaces al asiento revertido o a su reversión. El contador ve "Revertir" si el asiento
 * está `CONTABILIZADO` y no es una reversión (una reversión no se revierte: CON-009; un revertido tampoco: CON-008).
 * Muestra el aviso que dejó la pantalla anterior (p. ej. "Asiento N.º 3/2026 registrado").
 */
export function PaginaAsiento() {
  const { asientoId = '' } = useParams();
  const { puedeEscribir } = usePermisosContabilidad();
  const [revirtiendo, setRevirtiendo] = useState(false);
  // El aviso de éxito viaja en el estado de la navegación
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;
  const consulta = useObtenerAsiento(asientoId);
  const asiento = consulta.data?.data as Asiento | undefined;

  if (consulta.isPending) {
    return (
      <p role="status" className="text-sm text-neutral-600">
        Cargando…
      </p>
    );
  }
  if (consulta.isError || !asiento) {
    // 404 (PLT-017): no existe o no pertenece a la empresa activa
    const noExiste = esErrorApi(consulta.error) && consulta.error.status === 404;
    return (
      <Alert variant="error">
        {noExiste
          ? 'El asiento no existe o no pertenece a tu espacio de trabajo.'
          : 'No pudimos cargar el asiento.'}
      </Alert>
    );
  }

  const puedeRevertir =
    puedeEscribir && asiento.estado === 'CONTABILIZADO' && asiento.origenTipo !== 'REVERSION';

  return (
    <section aria-labelledby="titulo-asiento" className="max-w-5xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="titulo-asiento" className="text-xl font-semibold">
          Asiento N.º {numeroAsiento(asiento)}
        </h2>
        <div className="flex items-center gap-2">
          <InsigniaEstado estado={asiento.estado} />
          {puedeRevertir && <Button onClick={() => setRevirtiendo(true)}>Revertir</Button>}
        </div>
      </div>

      {aviso && <Alert variant="success">{aviso}</Alert>}

      {/* Cabecera del asiento */}
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="font-medium">Fecha</dt>
        <dd>{asiento.fecha}</dd>
        <dt className="font-medium">Concepto</dt>
        <dd>{asiento.concepto}</dd>
        <dt className="font-medium">Origen</dt>
        <dd>{ETIQUETA_ORIGEN[asiento.origenTipo]}</dd>
        {asiento.modoPrecio && (
          <>
            <dt className="font-medium">Modo de precio</dt>
            <dd>{ETIQUETA_MODO[asiento.modoPrecio]}</dd>
          </>
        )}
        <dt className="font-medium">Registrado</dt>
        <dd>{formatearFechaHora(asiento.creadoEn)}</dd>
      </dl>

      {/* Enlaces entre un asiento y su reversión */}
      {asiento.asientoReversionId && (
        <p className="text-sm">
          Este asiento fue revertido:{' '}
          <Link className="underline" to={`/contabilidad/libro-diario/${asiento.asientoReversionId}`}>
            ver la reversión
          </Link>
        </p>
      )}
      {asiento.asientoRevertidoId && (
        <p className="text-sm">
          Este asiento revierte a otro:{' '}
          <Link className="underline" to={`/contabilidad/libro-diario/${asiento.asientoRevertidoId}`}>
            ver el asiento revertido
          </Link>
        </p>
      )}

      <TablaLineas leyenda="Líneas del asiento" filas={filasDeAsiento(asiento)} />

      <dl className="flex gap-6 rounded-md bg-neutral-50 p-3 text-sm">
        <div className="flex gap-2">
          <dt>Total Debe</dt>
          <dd className="tabular-nums">{formatearMoneda(asiento.totalDebe)}</dd>
        </div>
        <div className="flex gap-2">
          <dt>Total Haber</dt>
          <dd className="tabular-nums">{formatearMoneda(asiento.totalHaber)}</dd>
        </div>
      </dl>

      <Link className="text-sm underline" to="/contabilidad/libro-diario">
        Volver al Libro Diario
      </Link>

      {revirtiendo && <DialogoReversion asiento={asiento} onCerrar={() => setRevirtiendo(false)} />}
    </section>
  );
}
