package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionVersionNoCoincide;
import com.bcodesphere.pilot.compartido.VersionEtag;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ReglasCatalogo;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la configuración contable (CLAUDE.md 11.3): modo de precio por defecto y cuentas de IVA débito y
 * crédito. Leer exige {@code auditor}; escribir, {@code contador}. Cada cambio queda auditado con valor anterior y
 * nuevo y aplica solo a los asientos futuros.
 */
@Service
public class GestionarConfiguracionContable {

    private final RepositorioConfiguracionContable configuracion;
    private final RepositorioCuentas cuentas;
    private final RegistroAuditoria auditoria;

    /**
     * Crea el caso de uso.
     *
     * @param configuracion puerto de persistencia de la configuración
     * @param cuentas puerto del catálogo, para validar las cuentas de IVA
     * @param auditoria puerto de auditoría
     */
    public GestionarConfiguracionContable(
            RepositorioConfiguracionContable configuracion, RepositorioCuentas cuentas, RegistroAuditoria auditoria) {
        this.configuracion = configuracion;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
    }

    /**
     * Consulta la configuración de la empresa activa.
     *
     * @return la configuración con su versión
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si la empresa no la tiene
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public ConfiguracionContable obtener() {
        return configuracion.obtener().orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /**
     * Reemplaza la configuración (PUT completo) con concurrencia optimista.
     *
     * @param ifMatch valor crudo de {@code If-Match}
     * @param modo modo de precio por defecto
     * @param cuentaIvaDebitoId cuenta del IVA débito fiscal
     * @param cuentaIvaCreditoId cuenta del IVA crédito fiscal
     * @return la configuración con su versión actual (sin cambios si los valores son los mismos)
     * @throws ExcepcionVersionNoCoincide 412 {@code PLT-016}
     * @throws com.bcodesphere.pilot.compartido.ExcepcionValidacion 422 {@code CON-006} con {@code errores} por campo si una cuenta no existe,
     *     está inactiva o no es de detalle
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional
    public ConfiguracionContable actualizar(
            String ifMatch, ModoPrecio modo, UUID cuentaIvaDebitoId, UUID cuentaIvaCreditoId) {
        // 1. If-Match primero: mal formado o distinto de la versión actual = 412
        long versionEsperada = VersionEtag.parsear(ifMatch);
        ConfiguracionContable actual = configuracion.obtener().orElseThrow(ExcepcionPlataforma::noEncontrado);
        if (actual.version() != versionEsperada) {
            throw new ExcepcionVersionNoCoincide();
        }

        // 2. Las dos cuentas deben ser de la empresa, activas y de detalle (CON-006); una de otra empresa no aparece
        //    Si fallan las dos, el error lleva ambos campos para que la pantalla marque los dos selectores
        List<ErrorCampo> errores = new ArrayList<>();
        ReglasCatalogo.comprobarImputable(cuentas.buscar(cuentaIvaDebitoId).orElse(null), "cuentaIvaDebitoId")
                .ifPresent(errores::add);
        ReglasCatalogo.comprobarImputable(cuentas.buscar(cuentaIvaCreditoId).orElse(null), "cuentaIvaCreditoId")
                .ifPresent(errores::add);
        ReglasCatalogo.exigirImputables(errores);

        // 3. Sin cambios reales no se escribe ni se sube la versión
        if (actual.modoPrecioDefecto() == modo
                && actual.cuentaIvaDebito().id().equals(cuentaIvaDebitoId)
                && actual.cuentaIvaCredito().id().equals(cuentaIvaCreditoId)) {
            return actual;
        }

        // 4. UPDATE ... WHERE version = :v: si otra petición ganó la carrera, 412 igual
        if (!configuracion.actualizar(modo, cuentaIvaDebitoId, cuentaIvaCreditoId, versionEsperada)) {
            throw new ExcepcionVersionNoCoincide();
        }

        // 5. Auditoría con el valor anterior y el nuevo (criterio de F2: el cambio de modo queda auditado)
        auditoria.registrar(
                "configuracion_contable",
                ContextoEmpresa.empresaRequerida().valor().toString(),
                "ACTUALIZAR",
                instantanea(
                        actual.modoPrecioDefecto(),
                        actual.cuentaIvaDebito().id(),
                        actual.cuentaIvaCredito().id()),
                instantanea(modo, cuentaIvaDebitoId, cuentaIvaCreditoId));
        return configuracion.obtener().orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /** Campos auditables de la configuración. */
    private static Map<String, Object> instantanea(ModoPrecio modo, UUID debitoId, UUID creditoId) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("modoPrecioDefecto", modo.name());
        valores.put("cuentaIvaDebitoId", debitoId.toString());
        valores.put("cuentaIvaCreditoId", creditoId.toString());
        return valores;
    }
}
