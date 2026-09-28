import { useMutation } from '@tanstack/react-query';
import { Navigate } from 'react-router-dom';
import { diagnosticarMayorizacion } from '@/api/reportes-contables/reportes-contables';
import type { DiagnosticoMayorizacion } from '@/api/modelos';
import { formatearMoneda } from '@/compartido/dinero';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { mensajeContabilidad } from '../mensajesContabilidad';
import { usePermisosContabilidad } from '../usePermisosContabilidad';

/**
 * Pantalla "Diagnóstico" (`/contabilidad/reportes/diagnostico`): verifica que `saldo_cuenta_mensual`
 * sea igual a la suma de las líneas del Libro Diario para toda cuenta, año y mes (ADR-018). Solo
 * `contador`/`admin_empresa` la ven (CLAUDE.md §13); un `auditor` que entre por la URL es redirigido.
 */
export function PaginaDiagnostico() {
  const { puedeEscribir } = usePermisosContabilidad();
  const verificacion = useMutation({ mutationFn: diagnosticarMayorizacion });

  if (!puedeEscribir) return <Navigate to="/contabilidad/reportes/balanza" replace />;

  const resultado = verificacion.data?.data as DiagnosticoMayorizacion | undefined;

  return (
    <section aria-labelledby="titulo-diagnostico" className="space-y-4">
      <h2 id="titulo-diagnostico" className="text-xl font-semibold">
        Diagnóstico de mayorización
      </h2>
      <p className="text-sm text-neutral-600">
        Verifica que el saldo mensual guardado de cada cuenta sea igual a la suma de sus líneas del Libro
        Diario (ADR-018). Con la partida doble garantizada, una diferencia solo puede deberse a un error de
        datos.
      </p>
      <Button type="button" onClick={() => verificacion.mutate()} disabled={verificacion.isPending}>
        {verificacion.isPending ? 'Verificando…' : 'Verificar mayorización'}
      </Button>

      {verificacion.isError && <Alert variant="error">{mensajeContabilidad(verificacion.error)}</Alert>}

      {resultado?.consistente && (
        <Alert variant="success">
          Consistente: se revisaron {resultado.cantidadCuentasRevisadas} combinaciones de cuenta, año y mes
          sin encontrar diferencias.
        </Alert>
      )}

      {resultado && !resultado.consistente && (
        <div className="space-y-2">
          <Alert variant="error">
            Se encontraron {resultado.diferencias.length} diferencias de {resultado.cantidadCuentasRevisadas}{' '}
            combinaciones revisadas.
          </Alert>
          <div className="overflow-x-auto">
            <table aria-label="Diferencias de mayorización" className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-300 text-left">
                  <th scope="col" className="py-1 pr-2">
                    Cuenta
                  </th>
                  <th scope="col" className="px-2 py-1">
                    Año
                  </th>
                  <th scope="col" className="px-2 py-1">
                    Mes
                  </th>
                  <th scope="col" className="px-2 py-1 text-right">
                    Saldo Debe
                  </th>
                  <th scope="col" className="px-2 py-1 text-right">
                    Saldo Haber
                  </th>
                  <th scope="col" className="px-2 py-1 text-right">
                    Líneas Debe
                  </th>
                  <th scope="col" className="py-1 pl-2 text-right">
                    Líneas Haber
                  </th>
                </tr>
              </thead>
              <tbody>
                {resultado.diferencias.map((d, i) => (
                  <tr key={`${d.cuenta.id}-${d.anio}-${d.mes}-${i}`} className="border-b border-neutral-100">
                    <td className="py-1 pr-2">
                      <span className="font-mono">{d.cuenta.codigo}</span> — {d.cuenta.nombre}
                    </td>
                    <td className="px-2 py-1">{d.anio}</td>
                    <td className="px-2 py-1">{d.mes}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(d.saldoDebe)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(d.saldoHaber)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(d.lineasDebe)}</td>
                    <td className="py-1 pl-2 text-right tabular-nums">{formatearMoneda(d.lineasHaber)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
