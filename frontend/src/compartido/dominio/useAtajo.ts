import { useEffect, useRef } from 'react';

/** Nombres de campo de texto: un atajo de letra no debe dispararse con el foco ahí (spec F4.5 §7.4). */
const CAMPOS_DE_TEXTO = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

/** `true` si el foco está en un campo editable (incluido `contenteditable`). */
function elFocoEstaEnUnCampo(): boolean {
  const activo = document.activeElement;
  if (!activo) return false;
  if (CAMPOS_DE_TEXTO.has(activo.tagName)) return true;
  return activo.getAttribute('contenteditable') === 'true';
}

/**
 * Registra un atajo de teclado global de una sola tecla (`Ctrl+K`, `N`…). Los atajos de una sola
 * letra sin modificador nunca se disparan con el foco en un campo de texto, para no interferir con
 * la escritura (criterio de UX obligatorio, AGENTS.md §3.7).
 * @param tecla tecla a comparar con `KeyboardEvent.key`, en minúsculas (p. ej. `"k"`, `"n"`)
 * @param accion se invoca cuando se pulsa la tecla en el contexto correcto
 * @param opciones.ctrlOOMeta si es `true`, exige `Ctrl` (o `Cmd` en Mac); si es `false` (por defecto),
 * la tecla sola sin modificadores, y se ignora con el foco en un campo de texto
 */
export function useAtajo(tecla: string, accion: () => void, opciones: { ctrlOMeta?: boolean } = {}): void {
  // Referencia estable: no reinstala el listener cuando `accion` cambia de identidad en cada render
  const referencia = useRef(accion);
  useEffect(() => {
    referencia.current = accion;
  });

  const { ctrlOMeta = false } = opciones;
  useEffect(() => {
    const alPulsar = (evento: KeyboardEvent) => {
      if (evento.key.toLowerCase() !== tecla.toLowerCase()) return;
      if (ctrlOMeta) {
        if (!(evento.ctrlKey || evento.metaKey)) return;
      } else if (evento.ctrlKey || evento.metaKey || evento.altKey || elFocoEstaEnUnCampo()) {
        return;
      }
      evento.preventDefault();
      referencia.current();
    };
    document.addEventListener('keydown', alPulsar);
    return () => document.removeEventListener('keydown', alPulsar);
  }, [tecla, ctrlOMeta]);
}
