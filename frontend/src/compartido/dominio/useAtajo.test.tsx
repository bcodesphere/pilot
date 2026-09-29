import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { useAtajo } from './useAtajo';

/** Componente de prueba: registra un atajo de letra y uno con Ctrl, y expone un campo de texto. */
function Prueba({ onN, onK }: { onN: () => void; onK: () => void }) {
  useAtajo('n', onN);
  useAtajo('k', onK, { ctrlOMeta: true });
  return <input aria-label="campo" />;
}

describe('useAtajo', () => {
  it('una letra sola dispara la acción cuando el foco no está en un campo', async () => {
    const onN = vi.fn();
    render(<Prueba onN={onN} onK={vi.fn()} />);
    await userEvent.keyboard('n');
    expect(onN).toHaveBeenCalledOnce();
  });

  it('una letra sola NO se dispara con el foco en un campo de texto', async () => {
    const onN = vi.fn();
    render(<Prueba onN={onN} onK={vi.fn()} />);
    await userEvent.click(screen.getByLabelText('campo'));
    await userEvent.keyboard('n');
    expect(onN).not.toHaveBeenCalled();
  });

  it('Ctrl+K se dispara sin importar el foco', async () => {
    const onK = vi.fn();
    render(<Prueba onN={vi.fn()} onK={onK} />);
    await userEvent.click(screen.getByLabelText('campo'));
    await userEvent.keyboard('{Control>}k{/Control}');
    expect(onK).toHaveBeenCalledOnce();
  });

  it('"k" sin Ctrl no dispara el atajo que exige ctrlOMeta', async () => {
    const onK = vi.fn();
    render(<Prueba onN={vi.fn()} onK={onK} />);
    await userEvent.keyboard('k');
    expect(onK).not.toHaveBeenCalled();
  });
});
