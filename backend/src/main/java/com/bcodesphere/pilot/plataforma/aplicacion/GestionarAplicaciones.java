package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.AplicacionInstalada;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.plataforma.dominio.AplicacionEmpresa;
import com.bcodesphere.pilot.plataforma.dominio.EstadoAplicacionEmpresa;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del catálogo de apps (ADR-030): listarlo con su estado para la empresa activa e instalar una app.
 * Corren con el contexto de la empresa ya validada por el filtro de empresa activa (RLS).
 */
@Service
public class GestionarAplicaciones {

    /** Resultado de instalar: la app con su estado final y si esta llamada la instaló ahora. */
    public record Resultado(AplicacionEmpresa aplicacion, boolean instaladaAhora) {}

    private final RepositorioAplicaciones aplicaciones;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;

    /**
     * Crea el caso de uso.
     *
     * @param aplicaciones puerto del catálogo y las instalaciones
     * @param auditoria puerto de auditoría
     * @param eventos publicador de eventos síncronos de Spring
     */
    public GestionarAplicaciones(
            RepositorioAplicaciones aplicaciones, RegistroAuditoria auditoria, ApplicationEventPublisher eventos) {
        this.aplicaciones = aplicaciones;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    /**
     * Catálogo disponible con el estado de cada app para la empresa activa (rol mínimo {@code auditor}).
     *
     * @return apps ordenadas por su orden en el catálogo
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public List<AplicacionEmpresa> listar() {
        return aplicaciones.listar(ContextoEmpresa.empresaRequerida().valor());
    }

    /**
     * Instala una app comunitaria en la empresa activa (rol mínimo {@code admin_empresa}). Todo ocurre en una sola
     * transacción: inserción, auditoría y evento {@link AplicacionInstalada}; si un oyente falla, se revierte todo.
     *
     * @param codigo código de la app
     * @return la app con su estado y si se instaló ahora ({@code false} si ya estaba instalada)
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si no existe o no está disponible; 403 {@code PLT-011} si es
     *     Enterprise
     */
    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @Transactional
    public Resultado instalar(String codigo) {
        EmpresaId empresa = ContextoEmpresa.empresaRequerida();

        // 1. El código debe existir y estar disponible en el catálogo; si no, 404 sin más detalle
        AplicacionEmpresa app =
                aplicaciones.buscar(codigo, empresa.valor()).orElseThrow(ExcepcionPlataforma::noEncontrado);

        // 2. Las apps Enterprise se muestran pero no se instalan en 1.0 (ADR-030)
        if (app.estado() == EstadoAplicacionEmpresa.BLOQUEADA_ENTERPRISE) {
            throw ExcepcionPlataforma.appEnterprise();
        }

        // 3. Inserción idempotente: 0 filas = ya estaba instalada (o una instalación concurrente ganó): 200 sin efectos
        if (!aplicaciones.instalar(empresa.valor(), codigo)) {
            return new Resultado(releer(codigo, empresa), false);
        }

        // 4. Solo si se insertó de verdad: auditoría y evento, dentro de esta transacción (ADR-030 punto 5)
        auditoria.registrar(
                "empresa_aplicacion", empresa.valor() + ":" + codigo, "INSTALAR", null, Map.of("codigo", codigo));
        eventos.publishEvent(new AplicacionInstalada(empresa, codigo));
        return new Resultado(releer(codigo, empresa), true);
    }

    /** Lee la app con su estado ya actualizado; siempre existe porque se validó al inicio. */
    private AplicacionEmpresa releer(String codigo, EmpresaId empresa) {
        return aplicaciones.buscar(codigo, empresa.valor()).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }
}
