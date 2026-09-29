import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAtajo } from '@/compartido/dominio/useAtajo';
import { Button } from '@/compartido/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuTrigger,
} from '@/compartido/ui/dropdown-menu';
import { useCatalogoApps } from '@/nucleo/apps/useCatalogoApps';
import { registroOperaciones, type EntradaRegistrar } from './registroOperaciones';

/** Agrupa las entradas por `grupo`; las que no tienen grupo quedan en una lista final aparte. */
function agrupar(entradas: EntradaRegistrar[]) {
  const grupos = new Map<string, EntradaRegistrar[]>();
  const sinGrupo: EntradaRegistrar[] = [];
  for (const entrada of entradas) {
    if (!entrada.grupo) {
      sinGrupo.push(entrada);
      continue;
    }
    grupos.set(entrada.grupo, [...(grupos.get(entrada.grupo) ?? []), entrada]);
  }
  return { grupos, sinGrupo };
}

/**
 * Botón "+ Registrar" de la barra superior (spec F4.5 §7.4 y §8): agrupa las operaciones guiadas y,
 * al final, "Asiento manual (avanzado)". Atajo `N` (no se dispara con el foco en un campo de texto,
 * `useAtajo`). Sin Contabilidad instalada no hay nada que registrar, así que el botón no se muestra.
 */
export function MenuRegistrar() {
  const [abierto, setAbierto] = useState(false);
  const navegar = useNavigate();
  const { apps } = useCatalogoApps();
  const contabilidadInstalada = apps.some((a) => a.codigo === 'contabilidad' && a.estado === 'INSTALADA');

  useAtajo('n', () => setAbierto(true));

  if (!contabilidadInstalada || registroOperaciones.length === 0) return null;

  const { grupos, sinGrupo } = agrupar(registroOperaciones);

  return (
    <DropdownMenu open={abierto} onOpenChange={setAbierto}>
      <DropdownMenuTrigger asChild>
        <Button size="sm">
          <Plus aria-hidden="true" className="size-4" />
          Registrar
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {[...grupos.entries()].map(([grupo, entradas]) => (
          <div key={grupo}>
            <DropdownMenuLabel>{grupo}</DropdownMenuLabel>
            {entradas.map((entrada) => (
              <DropdownMenuItem key={entrada.href} onSelect={() => navegar(entrada.href)}>
                {entrada.etiqueta}
              </DropdownMenuItem>
            ))}
          </div>
        ))}
        {grupos.size > 0 && sinGrupo.length > 0 && <DropdownMenuSeparator />}
        {sinGrupo.map((entrada) => (
          <DropdownMenuItem key={entrada.href} onSelect={() => navegar(entrada.href)}>
            {entrada.etiqueta}
            <DropdownMenuShortcut>N</DropdownMenuShortcut>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
