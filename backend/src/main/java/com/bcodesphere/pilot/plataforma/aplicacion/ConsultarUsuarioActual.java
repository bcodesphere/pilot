package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.plataforma.dominio.MembresiaUsuario;
import com.bcodesphere.pilot.plataforma.dominio.Usuario;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Casos de uso de {@code GET /me} y {@code PATCH /me}: el usuario autenticado con sus membresías activas y el cambio
 * de su consentimiento de correos de recomendaciones (ADR-028).
 *
 * <p>Corren en el modo «sin empresa» (ADR-026): {@code /me} no lleva {@code X-Empresa-Id}, porque sirve justamente para
 * elegir la empresa activa. Solo tocan la tabla global {@code usuario}, {@code auditoria_global} y la función
 * {@code membresias_de_usuario}.
 */
@Service
public class ConsultarUsuarioActual {

    /** Usuario y sus membresías activas, tal como las necesita la respuesta de {@code /me}. */
    public record Vista(Usuario usuario, List<MembresiaUsuario> membresias) {

        /** Copia defensiva e inmutable de la lista de membresías. */
        public Vista {
            membresias = List.copyOf(membresias);
        }
    }

    private final RepositorioUsuarios usuarios;
    private final ConsultaMembresias membresias;
    private final RegistroAuditoria auditoria;
    private final TransactionTemplate lectura;
    private final TransactionTemplate escritura;

    /**
     * Crea el caso de uso.
     *
     * @param usuarios persistencia de usuarios
     * @param membresias consulta de membresías (función SECURITY DEFINER)
     * @param auditoria puerto de auditoría
     * @param gestor gestor de transacciones de la aplicación
     */
    public ConsultarUsuarioActual(
            RepositorioUsuarios usuarios,
            ConsultaMembresias membresias,
            RegistroAuditoria auditoria,
            PlatformTransactionManager gestor) {
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.auditoria = auditoria;
        this.lectura = new TransactionTemplate(gestor);
        this.lectura.setReadOnly(true);
        this.escritura = new TransactionTemplate(gestor);
    }

    /**
     * Usuario y membresías activas.
     *
     * @param usuarioId usuario autenticado
     * @return la vista para {@code GET /me}
     */
    public Vista obtener(UUID usuarioId) {
        return ContextoEmpresa.ejecutarSinEmpresa(usuarioId.toString(), () -> lectura.execute(s -> leer(usuarioId)));
    }

    /**
     * Da o retira el consentimiento de recomendaciones. Si el estado vigente ya coincide no escribe nada.
     *
     * @param usuarioId usuario autenticado
     * @param aceptado {@code true} da el consentimiento; {@code false} lo retira
     * @return la vista actualizada para la respuesta de {@code PATCH /me}
     */
    public Vista actualizarConsentimiento(UUID usuarioId, boolean aceptado) {
        return ContextoEmpresa.ejecutarSinEmpresa(
                usuarioId.toString(),
                () -> escritura.execute(s -> {
                    // 1. Estado vigente; si ya es el pedido no hay escritura ni auditoría
                    Usuario antes = usuarios.buscarPorId(usuarioId).orElseThrow(this::usuarioInexistente);
                    if (antes.recomendacionesVigentes() == aceptado) {
                        return leer(usuarioId);
                    }
                    // 2. Cambia el estado: fija aceptadas_en (true) o retiradas_en (false) con la hora de la base de
                    // datos
                    usuarios.fijarConsentimiento(usuarioId, aceptado);
                    // 3. Auditoría del cambio en auditoria_global (solo el consentimiento, sin datos personales)
                    auditoria.registrarGlobal(
                            "usuario",
                            usuarioId.toString(),
                            "ACTUALIZAR",
                            Map.of("recomendacionesCorreo", antes.recomendacionesVigentes()),
                            Map.of("recomendacionesCorreo", aceptado));
                    return leer(usuarioId);
                }));
    }

    /** Lee al usuario y sus membresías dentro de la transacción en curso. */
    private Vista leer(UUID usuarioId) {
        Usuario usuario = usuarios.buscarPorId(usuarioId).orElseThrow(this::usuarioInexistente);
        return new Vista(usuario, membresias.deUsuario(usuarioId));
    }

    /** El usuario ya se resolvió en el filtro; que no exista aquí solo puede ser una carrera improbable: 401. */
    private ExcepcionPlataforma usuarioInexistente() {
        return ExcepcionPlataforma.noAutenticado("No fue posible identificar al usuario");
    }
}
