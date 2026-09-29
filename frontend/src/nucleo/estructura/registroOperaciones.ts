/**
 * Registro de entradas de "+ Registrar" (ADR-041, spec F4.5 §8): cada operación guiada agrega su
 * fila aquí cuando su pantalla exista (U3); `MenuRegistrar` solo lee esta lista, así que agregar un
 * tipo nuevo no exige tocar el menú. El asiento manual siempre va al final, sin `grupo`.
 */
export interface EntradaRegistrar {
  etiqueta: string;
  href: string;
  /** Encabezado del grupo en el menú ("Ingresos", "Gastos", "Bancos", "Activos"); sin grupo = al final. */
  grupo?: string;
}

/** En U1 solo existe el asiento manual; las operaciones guiadas llegan con U3. */
export const registroOperaciones: EntradaRegistrar[] = [
  { etiqueta: 'Asiento manual (avanzado)', href: '/contabilidad/libro-diario/nuevo' },
];
