/** Propiedades de {@link CodigoCuenta}. */
export interface PropsCodigoCuenta {
  /** Código del catálogo (`cuenta_contable.codigo`, CLAUDE.md §9.3). */
  codigo: string;
  /** Nombre de la cuenta; se omite en contextos donde ya se conoce (p. ej. una columna angosta). */
  nombre?: string;
}

/**
 * Código de cuenta en fuente monoespaciada (Red Hat Mono, spec F4.5 §7.3) seguido de su nombre.
 * @param props ver {@link PropsCodigoCuenta}
 */
export function CodigoCuenta({ codigo, nombre }: PropsCodigoCuenta) {
  return (
    <span className="inline-flex items-baseline gap-2">
      <span className="codigo-cuenta cifra text-[var(--color-texto-suave)]">{codigo}</span>
      {nombre && <span className="text-[var(--color-texto)]">{nombre}</span>}
    </span>
  );
}
