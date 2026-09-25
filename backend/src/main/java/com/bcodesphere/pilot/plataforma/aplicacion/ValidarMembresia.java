package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caso de uso «validar la empresa activa»: comprueba que el usuario tenga una membresía ACTIVA en la empresa que
 * llegó en {@code X-Empresa-Id} y devuelve su rol (CLAUDE.md 4.5). Nunca se confía en el header sin este paso.
 *
 * <p>Corre en el modo «sin empresa» (ADR-026) y solo usa la función {@code membresia_activa}.
 */
@Service
public class ValidarMembresia {

    private final ConsultaMembresias membresias;
    private final TransactionTemplate lectura;

    /**
     * Crea el caso de uso.
     *
     * @param membresias consulta de membresías (funciones SECURITY DEFINER)
     * @param gestor gestor de transacciones de la aplicación
     */
    public ValidarMembresia(ConsultaMembresias membresias, PlatformTransactionManager gestor) {
        this.membresias = membresias;
        this.lectura = new TransactionTemplate(gestor);
        this.lectura.setReadOnly(true);
    }

    /**
     * Rol del usuario en la empresa.
     *
     * @param usuarioId usuario autenticado
     * @param empresaId empresa pedida en el header
     * @return el rol del usuario en esa empresa
     * @throws ExcepcionPlataforma 403 {@code PLT-003} si no hay membresía activa (empresa de otro, membresía inactiva
     *     o empresa inexistente: no se distingue para no revelar cuáles existen)
     */
    public Rol rolEnEmpresa(UUID usuarioId, UUID empresaId) {
        return ContextoEmpresa.ejecutarSinEmpresa(
                        usuarioId.toString(), () -> lectura.execute(s -> membresias.rolActivo(usuarioId, empresaId)))
                .orElseThrow(ExcepcionPlataforma::sinMembresia);
    }
}
