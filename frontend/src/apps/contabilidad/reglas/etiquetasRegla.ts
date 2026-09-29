import type { CategoriaRegla, ReglaContabilizacion } from '@/api/modelos';

/** Etiquetas en español de los códigos de regla del `CIERRE_INGRESOS_DIARIO` (CLAUDE.md §12.3 y §12.5). */
const ETIQUETAS: Record<string, string> = {
  VENTAS_GRAVADAS: 'Ventas gravadas',
  VENTAS_EXENTAS: 'Ventas exentas',
  VENTAS_NO_SUJETAS: 'Ventas no sujetas',
  EFECTIVO: 'Efectivo',
  TARJETA: 'Tarjeta',
  TRANSFERENCIA: 'Transferencia',
  CHEQUE: 'Cheque',
  CREDITO: 'Crédito',
  OTRO: 'Otro',
};

/** Orden de presentación de los códigos dentro de su grupo (el de las tablas de §12.3). */
const ORDEN = Object.keys(ETIQUETAS);

/**
 * Etiqueta legible de un código de regla; un código desconocido se muestra con formato de frase.
 * @param codigo p. ej. `VENTAS_GRAVADAS`
 */
export function etiquetaRegla(codigo: string): string {
  if (ETIQUETAS[codigo]) return ETIQUETAS[codigo];
  const texto = codigo.toLowerCase().replaceAll('_', ' ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Filtra las reglas de una categoría y las ordena como se presentan en la pantalla.
 * @param reglas todas las reglas del tipo de operación
 * @param categoria `INGRESO` (concepto) o `COBRO` (forma de pago)
 */
export function reglasDeCategoria(
  reglas: readonly ReglaContabilizacion[],
  categoria: CategoriaRegla,
): ReglaContabilizacion[] {
  const posicion = (codigo: string) => {
    const i = ORDEN.indexOf(codigo);
    return i < 0 ? ORDEN.length : i;
  };
  return reglas
    .filter((r) => r.categoria === categoria)
    .sort((a, b) => posicion(a.codigo) - posicion(b.codigo));
}
