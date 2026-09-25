package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caso de uso «exigir que la app esté instalada» (PLT-004, ADR-021 y ADR-030): si la ruta pertenece a una app del
 * catálogo y la empresa activa no la tiene instalada, la petición se rechaza. Los códigos de app salen de la tabla
 * {@code aplicacion}, no del código.
 */
@Service
public class ExigirAppInstalada {

    private final ConsultaAplicaciones aplicaciones;
    private final TransactionTemplate lectura;

    /**
     * Crea el caso de uso.
     *
     * @param aplicaciones consulta del catálogo y las instalaciones
     * @param gestor gestor de transacciones de la aplicación
     */
    public ExigirAppInstalada(ConsultaAplicaciones aplicaciones, PlatformTransactionManager gestor) {
        this.aplicaciones = aplicaciones;
        this.lectura = new TransactionTemplate(gestor);
        this.lectura.setReadOnly(true);
    }

    /**
     * Verifica la instalación cuando el primer segmento de la ruta es el código de una app.
     *
     * @param segmento primer segmento después de {@code /api/v1/} (p. ej. {@code contabilidad})
     * @param empresaId empresa activa, ya validada
     * @param usuarioId usuario autenticado
     * @throws ExcepcionPlataforma 403 {@code PLT-004} si es una app del catálogo y no está instalada en la empresa
     */
    public void exigir(String segmento, UUID empresaId, UUID usuarioId) {
        // 1. La consulta lee empresa_aplicacion (RLS), así que corre en el contexto de la empresa ya validada
        Optional<Boolean> instalada = ContextoEmpresa.ejecutarCon(
                new EmpresaId(empresaId),
                usuarioId.toString(),
                () -> lectura.execute(s -> aplicaciones.estaInstalada(segmento, empresaId)));
        // 2. Vacío = el segmento no es una app (p. ej. /me, /aplicaciones): no aplica. False = app sin instalar
        if (instalada.isPresent() && !instalada.get()) {
            throw ExcepcionPlataforma.appNoInstalada();
        }
    }
}
