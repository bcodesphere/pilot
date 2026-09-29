import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useWatch } from 'react-hook-form';
import { crearCuentaContable } from '@/api/cuentas-contables/cuentas-contables';
import type { CuentaContable, NuevaCuentaContable } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Dialogo } from '@/compartido/ui/dialog';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { campoDeError, mensajeContabilidad } from '../mensajesContabilidad';
import { deducirPadre, LONGITUDES_CODIGO } from './arbol';
import { esquemaNuevaCuenta, type ValoresNuevaCuenta } from './esquemaCuenta';

/** Propiedades del diálogo "Nueva cuenta". */
interface Props {
  /** Catálogo cargado, para la vista previa del padre. */
  cuentas: readonly CuentaContable[];
  /** Código inicial del campo (p. ej. el de la cuenta padre, desde "Agregar subcuenta"); vacío por defecto. */
  codigoInicial?: string;
  /** Cierra el diálogo sin guardar. */
  onCerrar: () => void;
  /** Se invoca tras crear la cuenta (con 201). */
  onCreada: () => void;
}

/**
 * Texto informativo del padre que se deduciría del código escrito. Es solo una ayuda: el backend
 * decide y valida (`CON-015`), y aquí no se calcula nada contable (CLAUDE.md §1.2.1).
 */
function textoPadre(codigo: string, cuentas: readonly CuentaContable[]): string | null {
  // 1. Sin dígitos o con caracteres no válidos no hay nada que deducir
  if (!/^[0-9]+$/.test(codigo)) return null;
  // 2. Longitud fuera de los niveles válidos
  if (!(LONGITUDES_CODIGO as readonly number[]).includes(codigo.length)) {
    return 'Un código válido tiene 1, 2, 4, 6 u 8 dígitos.';
  }
  // 3. Una clase no tiene padre
  if (codigo.length === 1) return 'Es una clase: no tiene cuenta padre.';
  // 4. Padre deducido del prefijo, o aviso de que no se encontró
  const padre = deducirPadre(codigo, cuentas);
  return padre
    ? `Cuenta padre: ${padre.codigo} — ${padre.nombre}`
    : 'No encontramos su cuenta padre en el catálogo; el sistema lo validará al guardar.';
}

/**
 * Diálogo "Nueva cuenta" (rol contador): código y nombre; la naturaleza no se pide (ADR-042: siempre
 * derivada de la cuenta padre o de la clase). `POST /contabilidad/cuentas`; los errores `CON-010`,
 * `CON-011`, `CON-014` y `CON-015` se muestran junto al código.
 */
export function DialogoNuevaCuenta({ cuentas, codigoInicial, onCerrar, onCreada }: Props) {
  const clienteConsultas = useQueryClient();
  const formulario = useForm<ValoresNuevaCuenta>({
    resolver: zodResolver(esquemaNuevaCuenta),
    defaultValues: { codigo: codigoInicial ?? '', nombre: '' },
  });
  const codigo = useWatch({ control: formulario.control, name: 'codigo' });

  const crear = useMutation({
    mutationFn: (v: ValoresNuevaCuenta) => {
      const cuerpo: NuevaCuentaContable = { codigo: v.codigo, nombre: v.nombre };
      return crearCuentaContable(cuerpo);
    },
    onSuccess: async () => {
      // 1. El catálogo cambió: se vuelve a leer completo
      await clienteConsultas.invalidateQueries({ queryKey: ['/contabilidad/cuentas'] });
      // 2. Avisa y cierra
      onCreada();
    },
    onError: (error) => {
      // El error de negocio de un campo se muestra junto a ese campo
      if (campoDeError(error) === 'codigo') {
        formulario.setError('codigo', { message: mensajeContabilidad(error) });
      } else if (esErrorApi(error, 'PLT-002')) {
        const detalle = error.errores[0];
        if (detalle?.campo === 'codigo' || detalle?.campo === 'nombre') {
          formulario.setError(detalle.campo, { message: detalle.mensaje });
        }
      }
    },
  });

  // Errores que no pertenecen a un campo se muestran arriba del botón
  const errorGeneral =
    crear.isError && campoDeError(crear.error) !== 'codigo' && !esErrorApi(crear.error, 'PLT-002')
      ? mensajeContabilidad(crear.error)
      : null;
  const padre = textoPadre(codigo, cuentas);

  return (
    <Dialogo
      titulo={codigoInicial ? 'Agregar subcuenta' : 'Nueva cuenta'}
      onCerrar={onCerrar}
      cerrable={!crear.isPending}
    >
      <form noValidate className="space-y-3" onSubmit={formulario.handleSubmit((v) => crear.mutate(v))}>
        <div className="space-y-1">
          <Label htmlFor="cuenta-codigo">Código</Label>
          <Input
            id="cuenta-codigo"
            inputMode="numeric"
            aria-invalid={formulario.formState.errors.codigo ? true : undefined}
            aria-describedby="cuenta-codigo-ayuda cuenta-codigo-error"
            {...formulario.register('codigo')}
          />
          <p id="cuenta-codigo-ayuda" className="min-h-4 text-xs text-[var(--color-texto-suave)]">
            {padre}
          </p>
          <p id="cuenta-codigo-error" role="alert" className="min-h-4 text-sm text-[var(--color-error)]">
            {formulario.formState.errors.codigo?.message}
          </p>
        </div>

        <div className="space-y-1">
          <Label htmlFor="cuenta-nombre">Nombre</Label>
          <Input
            id="cuenta-nombre"
            aria-invalid={formulario.formState.errors.nombre ? true : undefined}
            aria-describedby="cuenta-nombre-error"
            {...formulario.register('nombre')}
          />
          <p id="cuenta-nombre-error" role="alert" className="min-h-4 text-sm text-[var(--color-error)]">
            {formulario.formState.errors.nombre?.message}
          </p>
        </div>

        {errorGeneral && <Alert variant="error">{errorGeneral}</Alert>}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onCerrar} disabled={crear.isPending}>
            Cancelar
          </Button>
          <Button type="submit" disabled={crear.isPending}>
            {crear.isPending ? 'Guardando…' : 'Crear cuenta'}
          </Button>
        </div>
      </form>
    </Dialogo>
  );
}
