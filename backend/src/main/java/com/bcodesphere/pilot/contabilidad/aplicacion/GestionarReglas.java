package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.compartido.ExcepcionVersionNoCoincide;
import com.bcodesphere.pilot.compartido.VersionEtag;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ReglasCatalogo;
import com.bcodesphere.pilot.contabilidad.dominio.reglas.ReglaContabilizacion;
import com.bcodesphere.pilot.contabilidad.dominio.reglas.TipoOperacionContable;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de las reglas de contabilización (ADR-020, ADR-035): listarlas y editar su cuenta y su estado. Las
 * reglas nacen en la precarga; las claves (tipo, categoría, código) no se editan. Leer exige {@code auditor};
 * escribir, {@code contador}.
 */
@Service
public class GestionarReglas {

    private final RepositorioReglas reglas;
    private final RepositorioCuentas cuentas;
    private final RegistroAuditoria auditoria;

    /**
     * Crea el caso de uso.
     *
     * @param reglas puerto de persistencia de reglas
     * @param cuentas puerto del catálogo, para validar la cuenta asignada
     * @param auditoria puerto de auditoría
     */
    public GestionarReglas(RepositorioReglas reglas, RepositorioCuentas cuentas, RegistroAuditoria auditoria) {
        this.reglas = reglas;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
    }

    /**
     * Lista las reglas de la empresa activa.
     *
     * @param tipoOperacion filtra por tipo de operación, o nulo para todas
     * @return las reglas ordenadas por tipo, categoría y código
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public List<ReglaContabilizacion> listar(TipoOperacionContable tipoOperacion) {
        return reglas.listar(tipoOperacion);
    }

    /**
     * Cambia la cuenta y el estado de una regla con concurrencia optimista.
     *
     * @param reglaId regla a editar
     * @param ifMatch valor crudo de {@code If-Match}
     * @param cuentaId cuenta nueva, o nulo (solo con la regla inactiva)
     * @param activa estado nuevo
     * @return la regla con su versión actual (sin cambios si los valores son los mismos)
     * @throws ExcepcionVersionNoCoincide 412 {@code PLT-016}
     * @throws ExcepcionValidacion 422 {@code PLT-002} si se activa sin cuenta
     * @throws ExcepcionValidacion 422 {@code CON-006} con el campo {@code cuentaId} si la cuenta no existe,
     *     está inactiva o no es de detalle
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional
    public ReglaContabilizacion actualizar(UUID reglaId, String ifMatch, UUID cuentaId, boolean activa) {
        // 1. If-Match primero: mal formado o distinto de la versión actual = 412
        long versionEsperada = VersionEtag.parsear(ifMatch);
        ReglaContabilizacion actual = reglas.buscar(reglaId).orElseThrow(ExcepcionPlataforma::noEncontrado);
        if (actual.version() != versionEsperada) {
            throw new ExcepcionVersionNoCoincide();
        }

        // 2. Una regla activa siempre tiene cuenta (422 PLT-002 con el campo cuentaId, ADR-035)
        ReglaContabilizacion.validarEstado(activa, cuentaId);

        // 3. Si trae cuenta, debe ser de la empresa, activa y de detalle (CON-006)
        if (cuentaId != null) {
            ReglasCatalogo.exigirImputable(cuentas.buscar(cuentaId).orElse(null), "cuentaId");
        }

        // 4. Sin cambios reales no se escribe ni se sube la versión
        UUID cuentaActual = actual.cuenta() == null ? null : actual.cuenta().id();
        if (actual.activa() == activa && Objects.equals(cuentaActual, cuentaId)) {
            return actual;
        }

        // 5. UPDATE ... WHERE version = :v: si otra petición ganó la carrera, 412 igual
        if (!reglas.actualizar(reglaId, cuentaId, activa, versionEsperada)) {
            throw new ExcepcionVersionNoCoincide();
        }

        // 6. Auditoría con el valor anterior y el nuevo
        auditoria.registrar(
                "regla_contabilizacion",
                reglaId.toString(),
                "ACTUALIZAR",
                instantanea(cuentaActual, actual.activa()),
                instantanea(cuentaId, activa));
        return reglas.buscar(reglaId).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /** Campos auditables de la regla. */
    private static Map<String, Object> instantanea(UUID cuentaId, boolean activa) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("cuentaId", cuentaId == null ? null : cuentaId.toString());
        valores.put("activa", activa);
        return valores;
    }
}
