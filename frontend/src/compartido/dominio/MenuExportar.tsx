import { ChevronDown, Download } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/compartido/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/compartido/ui/dropdown-menu';

/** Formato de exportación de un reporte (ADR-038). */
export type FormatoExportar = 'pdf' | 'xlsx' | 'csv';

/** Propiedades de {@link MenuExportar}. */
export interface PropsMenuExportar {
  /** Pide el archivo al backend y dispara la descarga; el nombre y el contenido son suyos. */
  onExportar: (formato: FormatoExportar) => Promise<void>;
  /** Se deshabilita mientras falten los filtros que la exportación exige (p. ej. un rango de fechas). */
  deshabilitado?: boolean;
}

/** Etiquetas en el orden de la ficha de reportes (spec F4.5 §8: "Exportar ▾"). */
const FORMATOS: { formato: FormatoExportar; etiqueta: string }[] = [
  { formato: 'pdf', etiqueta: 'PDF' },
  { formato: 'xlsx', etiqueta: 'Excel' },
  { formato: 'csv', etiqueta: 'CSV' },
];

/**
 * Botón "Exportar ▾" con los tres formatos (spec F4.5 §8, reemplaza los tres botones sueltos de
 * F4). El cálculo del archivo es todo del backend; aquí solo se dispara la descarga y se avisa el
 * resultado con un toast, sin bloquear el resto de la pantalla.
 * @param props ver {@link PropsMenuExportar}
 */
export function MenuExportar({ onExportar, deshabilitado = false }: PropsMenuExportar) {
  const [enCurso, setEnCurso] = useState<FormatoExportar | null>(null);

  /** Exporta un formato; el error se avisa por toast (no se expone detalle interno). */
  const exportar = async (formato: FormatoExportar) => {
    setEnCurso(formato);
    try {
      await onExportar(formato);
    } catch {
      toast.error('No pudimos generar el archivo. Inténtalo de nuevo.');
    } finally {
      setEnCurso(null);
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" disabled={deshabilitado || enCurso !== null}>
          <Download aria-hidden="true" className="size-4" />
          {enCurso ? 'Descargando…' : 'Exportar'}
          <ChevronDown aria-hidden="true" className="size-3.5" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {FORMATOS.map(({ formato, etiqueta }) => (
          <DropdownMenuItem key={formato} onSelect={() => void exportar(formato)}>
            {etiqueta}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
