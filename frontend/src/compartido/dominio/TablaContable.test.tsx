import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { TableCell, TableRow } from '@/compartido/ui/table';
import { TablaContable, type ColumnaContable } from './TablaContable';

interface Fila {
  id: string;
  cuenta: string;
  monto: string;
}

const columnas: ColumnaContable<Fila>[] = [
  { clave: 'cuenta', encabezado: 'Cuenta', celda: (f) => f.cuenta },
  { clave: 'monto', encabezado: 'Monto', celda: (f) => f.monto, alineacion: 'derecha' },
];

describe('TablaContable', () => {
  it('cargando muestra el esqueleto y ninguna fila de datos', () => {
    const { container } = render(
      <TablaContable columnas={columnas} filas={[]} cargando vacio={<span>Sin filas</span>} />,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
    expect(screen.queryByText('Sin filas')).not.toBeInTheDocument();
  });

  it('sin filas y sin cargar, muestra el nodo vacío', () => {
    render(
      <TablaContable columnas={columnas} filas={[]} cargando={false} vacio={<span>Sin movimientos</span>} />,
    );
    expect(screen.getByText('Sin movimientos')).toBeInTheDocument();
  });

  it('la fila de totales queda dentro de un <tfoot>', () => {
    const { container } = render(
      <TablaContable
        columnas={columnas}
        filas={[{ id: '1', cuenta: 'Caja', monto: '$100.00' }]}
        cargando={false}
        vacio={null}
        totales={
          <TableRow>
            <TableCell>Total</TableCell>
            <TableCell className="text-right">$100.00</TableCell>
          </TableRow>
        }
      />,
    );
    const pie = container.querySelector('tfoot');
    expect(pie).not.toBeNull();
    expect(pie).toHaveTextContent('Total');
    expect(pie).toHaveTextContent('$100.00');
  });

  it('sin totales no se renderiza ningún <tfoot>', () => {
    const { container } = render(
      <TablaContable
        columnas={columnas}
        filas={[{ id: '1', cuenta: 'Caja', monto: '$1.00' }]}
        cargando={false}
        vacio={null}
      />,
    );
    expect(container.querySelector('tfoot')).toBeNull();
  });

  it('filaExpandible muestra el detalle solo tras hacer clic en Ver detalle', async () => {
    render(
      <TablaContable
        columnas={columnas}
        filas={[{ id: '1', cuenta: 'Caja', monto: '$1.00' }]}
        cargando={false}
        vacio={null}
        filaExpandible={(f) => <span>Detalle de {f.cuenta}</span>}
      />,
    );
    expect(screen.queryByText('Detalle de Caja')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ver detalle' }));
    expect(screen.getByText('Detalle de Caja')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ocultar detalle' }));
    expect(screen.queryByText('Detalle de Caja')).not.toBeInTheDocument();
  });
});
