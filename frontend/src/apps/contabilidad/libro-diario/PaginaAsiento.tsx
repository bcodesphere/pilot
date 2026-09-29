import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useObtenerAsiento } from '@/api/asientos/asientos';
import type { Asiento } from '@/api/modelos';
import { EstadoDocumento } from '@/compartido/dominio/EstadoDocumento';
import { Monto } from '@/compartido/dominio/Monto';
import { formatearFechaHora } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { DialogoReversion } from './DialogoReversion';
import { ETIQUETA_ORIGEN, filasDeAsiento, numeroAsiento } from './etiquetas';
import { TablaLineas } from './presentacion';

/** Texto del modo de precio con que se separó el IVA (ADR-015). */
const ETIQUETA_MODO = { CON_IVA: 'Precios con IVA incluido', SIN_IVA: 'Precios más IVA' } as const;

/**
 * Pantalla de detalle de un asiento (`/contabilidad/libro-diario/:asientoId`). React Router reutiliza la misma
 * instancia de esta pantalla cuando solo cambia `:asientoId` (p. ej. al navegar de un asiento a su reversión),
 * así que aquí solo se lee el parámetro y se delega en {@link DetalleAsiento} con `key={asientoId}`: la `key`
 * fuerza a React a desmontar y volver a montar el detalle, para que su estado local (el diálogo de reversión,
 * incluida la fecha elegida) pertenezca siempre a un solo asiento y nunca sobreviva al cambio de asiento.
 */
export function PaginaAsiento() {
  const { asientoId = '' } = useParams();
  return <DetalleAsiento key={asientoId} asientoId={asientoId} />;
}

/** Propiedades de {@link DetalleAsiento}. */
interface PropsDetalleAsiento {
  /** Id del asiento a mostrar; una instancia de este componente pertenece a un solo asiento. */
  asientoId: string;
}

/**
 * Detalle de un asiento (patrón "Documento" del sistema de diseño, spec F4.5 §6.3): `EstadoDocumento`
 * arriba, los datos capturados y el asiento generado, y al final el Historial con la acción "Revertir".
 * El contador ve "Revertir" si el asiento está `CONTABILIZADO` y no es una reversión (una reversión no se
 * revierte: CON-009; un revertido tampoco: CON-008). Muestra el aviso que dejó la pantalla anterior (p. ej.
 * "Asiento N.º 3/2026 registrado").
 */
function DetalleAsiento({ asientoId }: PropsDetalleAsiento) {
  const { puedeEscribir } = usePermisosContabilidad();
  const [revirtiendo, setRevirtiendo] = useState(false);
  // El aviso de éxito viaja en el estado de la navegación
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;
  const consulta = useObtenerAsiento(asientoId);
  const asiento = consulta.data?.data as Asiento | undefined;

  if (consulta.isPending) {
    return (
      <p role="status" className="text-sm text-[var(--color-texto-suave)]">
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
        <EstadoDocumento
          estado={asiento.estado}
          enlaceReversion={
            asiento.asientoReversionId
              ? `/contabilidad/libro-diario/${asiento.asientoReversionId}`
              : undefined
          }
        />
      </div>

      {aviso && <Alert variant="success">{aviso}</Alert>}

      {/* Cabecera del asiento: los datos que se capturaron al registrarlo */}
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
      </dl>

      {/* Este asiento es la reversión de otro (la relación inversa la anuncia EstadoDocumento arriba) */}
      {asiento.asientoRevertidoId && (
        <p className="text-sm">
          Este asiento revierte a otro:{' '}
          <Link className="underline" to={`/contabilidad/libro-diario/${asiento.asientoRevertidoId}`}>
            ver el asiento revertido
          </Link>
        </p>
      )}

      <TablaLineas leyenda="Líneas del asiento" filas={filasDeAsiento(asiento)} />

      <dl className="flex gap-6 rounded-[var(--radius-panel)] bg-[var(--color-lienzo)] p-3 text-sm">
        <div className="flex gap-2">
          <dt>Total Debe</dt>
          <dd>
            <Monto valor={asiento.totalDebe} />
          </dd>
        </div>
        <div className="flex gap-2">
          <dt>Total Haber</dt>
          <dd>
            <Monto valor={asiento.totalHaber} />
          </dd>
        </div>
      </dl>

      {/* Historial (spec F4.5 §6.3): auditoría de quién y cuándo, y la acción "Revertir" al final. Sin
          un endpoint de auditoría del asiento (CLAUDE.md §9.2 `auditoria` no se expone por asiento en 1.0),
          solo se muestra `creadoEn`; `creadoPor` no está en el contrato (ver "Solicitudes" del reporte). */}
      <div className="space-y-2 border-t border-[var(--color-borde)] pt-3">
        <h3 className="text-sm font-medium">Historial</h3>
        <p className="text-sm text-[var(--color-texto-suave)]">
          Registrado el {formatearFechaHora(asiento.creadoEn)}
        </p>
        {puedeRevertir && <Button onClick={() => setRevirtiendo(true)}>Revertir</Button>}
      </div>

      <Link className="text-sm underline" to="/contabilidad/libro-diario">
        Volver al Libro Diario
      </Link>

      {revirtiendo && <DialogoReversion asiento={asiento} onCerrar={() => setRevirtiendo(false)} />}
    </section>
  );
}
