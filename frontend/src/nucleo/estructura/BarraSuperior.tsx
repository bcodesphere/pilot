import { MenuUsuario } from '@/nucleo/MenuUsuario';
import { SelectorEmpresa } from '@/nucleo/SelectorEmpresa';
import { BuscadorGlobal } from './BuscadorGlobal';
import { MenuRegistrar } from './MenuRegistrar';
import { MigasDePan } from './MigasDePan';

/**
 * Barra superior del shell (spec F4.5 §7.4): migas de pan, buscador global, "+ Registrar" y el
 * menú de usuario. `BuscadorGlobal` se monta siempre (escucha `Ctrl+K` aunque el botón visible sea
 * pequeño); su diálogo permanece cerrado hasta que se invoca.
 */
export function BarraSuperior() {
  return (
    <header className="flex h-14 shrink-0 items-center gap-3 border-b border-[var(--color-borde)] bg-[var(--color-superficie)] px-4">
      <MigasDePan />
      <div className="ml-auto flex items-center gap-2">
        <SelectorEmpresa />
        <BuscadorGlobal />
        <MenuRegistrar />
        <MenuUsuario />
      </div>
    </header>
  );
}
