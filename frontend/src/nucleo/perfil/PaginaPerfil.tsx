import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { actualizarUsuarioActual } from '@/api/usuario-actual/usuario-actual';
import { Alert } from '@/compartido/ui/alert';
import { Switch } from '@/compartido/ui/switch';
import { mensajeDeError } from '@/nucleo/mensajesError';
import { useSesion } from '@/nucleo/sesion/contextoSesion';

/**
 * Pantalla "Mi perfil" (`/perfil`): datos de la cuenta (solo lectura, se editan en Keycloak) y el
 * consentimiento "Acepto recibir recomendaciones por correo" (ADR-028), con actualización optimista.
 */
export function PaginaPerfil() {
  const { usuario, recargarUsuario } = useSesion();
  // Valor optimista mientras se guarda; null = mostrar el valor real de /me
  const [optimista, setOptimista] = useState<boolean | null>(null);
  const valor = optimista ?? usuario.recomendacionesCorreo;

  const guardar = useMutation({
    mutationFn: (recomendacionesCorreo: boolean) => actualizarUsuarioActual({ recomendacionesCorreo }),
    onSuccess: async () => {
      // El servidor es la fuente de verdad: recarga /me y suelta el valor optimista
      await recargarUsuario();
      setOptimista(null);
    },
    // Reversión: si falla, vuelve al valor real
    onError: () => setOptimista(null),
  });

  /** Cambia el interruptor de inmediato y lo confirma con el servidor. */
  const cambiar = (nuevo: boolean) => {
    setOptimista(nuevo);
    guardar.mutate(nuevo);
  };

  return (
    <section aria-labelledby="titulo-perfil" className="max-w-lg space-y-4">
      <h1 id="titulo-perfil" className="text-2xl font-bold">
        Mi perfil
      </h1>

      {/* 1. Datos de la cuenta (solo lectura) */}
      <dl className="grid grid-cols-[8rem_1fr] gap-2 text-sm">
        <dt className="font-medium">Nombre</dt>
        <dd>{usuario.nombre}</dd>
        <dt className="font-medium">Correo</dt>
        <dd>{usuario.correo}</dd>
        <dt className="font-medium">Teléfono</dt>
        <dd>{usuario.telefono}</dd>
      </dl>
      <p className="text-sm text-neutral-600">Estos datos se editan en tu cuenta de Keycloak.</p>

      {/* 2. Consentimiento de recomendaciones (ADR-028) */}
      <div className="flex items-center gap-3">
        <Switch
          aria-labelledby="etiqueta-recomendaciones"
          checked={valor}
          disabled={guardar.isPending}
          onCheckedChange={cambiar}
        />
        <span id="etiqueta-recomendaciones" className="text-sm">
          Acepto recibir recomendaciones por correo
        </span>
      </div>
      {guardar.isError && (
        <Alert variant="error">{mensajeDeError(guardar.error, {})} Se restauró el valor anterior.</Alert>
      )}
    </section>
  );
}
