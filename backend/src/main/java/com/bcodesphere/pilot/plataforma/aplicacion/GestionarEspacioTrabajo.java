package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.plataforma.dominio.EspacioTrabajo;
import com.bcodesphere.pilot.plataforma.dominio.NombreEspacio;
import com.bcodesphere.pilot.plataforma.dominio.VersionEtag;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de {@code GET} y {@code PATCH /empresas/{empresaId}}: consultar y renombrar el espacio de trabajo de una
 * persona natural (ADR-032). Solo se edita el nombre; nombre comercial, NIT y NRC no se leen ni se escriben.
 */
@Service
public class GestionarEspacioTrabajo {

    private final RepositorioEmpresas empresas;
    private final RegistroAuditoria auditoria;

    /**
     * Crea el caso de uso.
     *
     * @param empresas puerto de persistencia de empresas
     * @param auditoria puerto de auditoría
     */
    public GestionarEspacioTrabajo(RepositorioEmpresas empresas, RegistroAuditoria auditoria) {
        this.empresas = empresas;
        this.auditoria = auditoria;
    }

    /**
     * Consulta el espacio de trabajo de la empresa activa (rol mínimo {@code admin_empresa}).
     *
     * @param empresaId empresa de la ruta; debe ser la activa
     * @return el espacio de trabajo
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si no es la empresa activa
     */
    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @Transactional(readOnly = true)
    public EspacioTrabajo obtener(UUID empresaId) {
        exigirEmpresaActiva(empresaId);
        return empresas.buscar(empresaId).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /**
     * Cambia el nombre del espacio de trabajo con concurrencia optimista (rol mínimo {@code admin_empresa}).
     *
     * @param empresaId empresa de la ruta; debe ser la activa
     * @param ifMatch valor crudo del header {@code If-Match} (la versión entre comillas)
     * @param nombreNuevo nombre enviado por el cliente
     * @return el espacio con su versión actual (sin cambios si el nombre no cambió)
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si no es la empresa activa; 412 {@code PLT-016} si {@code If-Match}
     *     está mal formado o no coincide
     */
    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @Transactional
    public EspacioTrabajo renombrar(UUID empresaId, String ifMatch, String nombreNuevo) {
        // 1. Otra empresa se responde como inexistente (no revela su existencia)
        exigirEmpresaActiva(empresaId);

        // 2. If-Match primero, antes de mirar el nombre: mal formado o distinto de la versión actual = 412, aunque el
        //    nombre no cambie
        long versionEsperada = VersionEtag.parsear(ifMatch);
        EspacioTrabajo actual = empresas.buscar(empresaId).orElseThrow(ExcepcionPlataforma::noEncontrado);
        if (actual.version() != versionEsperada) {
            throw ExcepcionPlataforma.versionNoCoincide();
        }

        // 3. Normaliza (sin espacios en los extremos; vacío = 422) y, si no cambia, no escribe ni cambia la versión
        String nombre = NombreEspacio.normalizar(nombreNuevo);
        if (nombre.equals(actual.nombre())) {
            return actual;
        }

        // 4. UPDATE ... WHERE version = :v: si una carrera cambió la fila entre la lectura y la escritura, 412 igual
        if (!empresas.actualizarNombre(empresaId, nombre, versionEsperada)) {
            throw ExcepcionPlataforma.versionNoCoincide();
        }

        // 5. Auditoría con el valor anterior y el nuevo, en la misma transacción
        auditoria.registrar(
                "empresa",
                empresaId.toString(),
                "ACTUALIZAR",
                Map.of("nombre", actual.nombre()),
                Map.of("nombre", nombre));
        return empresas.buscar(empresaId).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /** La ruta solo admite la empresa activa; cualquier otra es 404 PLT-017. */
    private static void exigirEmpresaActiva(UUID empresaId) {
        if (!ContextoEmpresa.empresaRequerida().valor().equals(empresaId)) {
            throw ExcepcionPlataforma.noEncontrado();
        }
    }
}
