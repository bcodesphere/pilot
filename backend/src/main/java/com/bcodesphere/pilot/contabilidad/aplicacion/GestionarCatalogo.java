package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.compartido.ExcepcionVersionNoCoincide;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.compartido.VersionEtag;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.CodigoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ConsultaMovimientosCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ReglasCatalogo;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del catálogo de cuentas (CLAUDE.md 10.2, ADR-035): listar, obtener, crear y actualizar. Leer exige
 * {@code auditor}; escribir, {@code contador} (jerarquía admin_empresa &gt; contador &gt; auditor). Toda escritura
 * corre en una transacción, se audita con valor anterior y nuevo y usa concurrencia optimista con {@code If-Match}.
 */
@Service
public class GestionarCatalogo {

    private final RepositorioCuentas cuentas;
    private final ConsultaMovimientosCuenta movimientos;
    private final RegistroAuditoria auditoria;

    /**
     * Crea el caso de uso.
     *
     * @param cuentas puerto de persistencia del catálogo
     * @param movimientos puerto de movimientos y saldos (F3 lo reemplaza, ADR-035)
     * @param auditoria puerto de auditoría
     */
    public GestionarCatalogo(
            RepositorioCuentas cuentas, ConsultaMovimientosCuenta movimientos, RegistroAuditoria auditoria) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.auditoria = auditoria;
    }

    /**
     * Lista el catálogo completo, sin paginar.
     *
     * @param busqueda texto para filtrar por código o nombre, o nulo
     * @param soloDetalle solo cuentas que aceptan movimientos
     * @param soloActivas solo cuentas activas
     * @return las cuentas ordenadas por código
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public List<Cuenta> listar(String busqueda, boolean soloDetalle, boolean soloActivas) {
        return cuentas.listar(busqueda, soloDetalle, soloActivas);
    }

    /**
     * Obtiene una cuenta.
     *
     * @param id identificador
     * @return la cuenta
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si no existe en la empresa activa
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public Cuenta obtener(UUID id) {
        return cuentas.buscar(id).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /**
     * Crea una cuenta. El padre se deduce del código (prefijo del nivel anterior); si era hoja, deja de aceptar
     * movimientos. Todo en una transacción.
     *
     * @param codigoTexto código de la cuenta nueva
     * @param nombre nombre de la cuenta
     * @param naturaleza naturaleza pedida, o nulo para usar la de su clase
     * @return la cuenta creada, con versión 0
     * @throws ExcepcionContabilidad {@code CON-010}, {@code CON-011},
     *     {@code CON-014}, {@code CON-015} o {@code CON-016}
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional
    public Cuenta crear(String codigoTexto, String nombre, NaturalezaCuenta naturaleza) {
        // 1. Código válido (clase 1 a 5, longitud de un nivel): CON-010 / CON-015
        CodigoCuenta codigo = CodigoCuenta.de(codigoTexto);
        String nombreLimpio = normalizarNombre(nombre);

        // 2. Duplicado: se detecta antes de insertar; el índice único es la última defensa (CON-014)
        if (cuentas.buscarPorCodigo(codigo.valor()).isPresent()) {
            throw ExcepcionContabilidad.codigoDuplicado();
        }

        // 3. Padre existente, activo, prefijo esperado, sin movimientos y, si es hoja, fuera de uso
        Cuenta padre = codigo.codigoPadreEsperado()
                .flatMap(p -> cuentas.buscarPorCodigo(p.valor()))
                .orElse(null);
        boolean padreEnUso = padre != null && cuentas.enUsoPorConfiguracionOReglaActiva(padre.id());
        ReglasCatalogo.validarAlta(codigo, padre, padreEnUso, movimientos);

        // 4. Inserta la hija (hoja y activa; naturaleza pedida o la de su clase)
        Cuenta nueva = new Cuenta(
                GeneradorId.nuevo(),
                codigo,
                nombreLimpio,
                padre == null ? null : padre.id(),
                naturaleza == null ? codigo.naturalezaPorDefecto() : naturaleza,
                true,
                true,
                0L);
        cuentas.crear(nueva);
        auditoria.registrar("cuenta_contable", nueva.id().toString(), "CREAR", null, instantanea(nueva));

        // 5. El padre que era hoja deja de aceptar movimientos, y ese cambio también se audita
        if (padre != null && padre.aceptaMovimientos()) {
            cuentas.dejarDeAceptarMovimientos(padre.id());
            auditoria.registrar(
                    "cuenta_contable",
                    padre.id().toString(),
                    "ACTUALIZAR",
                    Map.of("aceptaMovimientos", true),
                    Map.of("aceptaMovimientos", false));
        }
        return cuentas.buscar(nueva.id()).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /**
     * Actualiza código, nombre, naturaleza o estado de una cuenta con concurrencia optimista. Los campos nulos no
     * cambian.
     *
     * @param id cuenta a editar
     * @param ifMatch valor crudo de {@code If-Match} (la versión entre comillas)
     * @param codigoTexto código nuevo, o nulo
     * @param nombre nombre nuevo, o nulo
     * @param naturaleza naturaleza nueva, o nulo
     * @param activa estado nuevo, o nulo
     * @return la cuenta con su versión actual (sin cambios si nada cambió)
     * @throws ExcepcionVersionNoCoincide 412 {@code PLT-016}
     * @throws ExcepcionContabilidad {@code CON-010} a {@code CON-016}
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional
    public Cuenta actualizar(
            UUID id, String ifMatch, String codigoTexto, String nombre, NaturalezaCuenta naturaleza, Boolean activa) {
        // 1. If-Match primero: mal formado o distinto de la versión actual = 412 (igual que el espacio de trabajo)
        long versionEsperada = VersionEtag.parsear(ifMatch);
        Cuenta actual = cuentas.buscar(id).orElseThrow(ExcepcionPlataforma::noEncontrado);
        if (actual.version() != versionEsperada) {
            throw new ExcepcionVersionNoCoincide();
        }

        // 2. Valores finales: lo que no se envía se conserva
        CodigoCuenta codigo = codigoTexto == null ? actual.codigo() : CodigoCuenta.de(codigoTexto);
        String nombreFinal = nombre == null ? actual.nombre() : normalizarNombre(nombre);
        NaturalezaCuenta naturalezaFinal = naturaleza == null ? actual.naturaleza() : naturaleza;
        boolean activaFinal = activa == null ? actual.activa() : activa;

        // 3. Reglas de negocio solo sobre lo que cambia
        if (!codigo.equals(actual.codigo())) {
            ReglasCatalogo.validarCambioCodigo(actual, codigo, cuentas.tieneHijas(id), movimientos);
        }
        if (actual.activa() && !activaFinal) {
            ReglasCatalogo.validarDesactivacion(actual, cuentas.enUsoPorConfiguracionOReglaActiva(id), movimientos);
        }

        // 4. Sin cambios reales no se escribe ni se sube la versión
        Cuenta nueva = new Cuenta(
                id,
                codigo,
                nombreFinal,
                actual.padreId(),
                naturalezaFinal,
                actual.aceptaMovimientos(),
                activaFinal,
                actual.version());
        if (nueva.equals(actual)) {
            return actual;
        }

        // 5. UPDATE ... WHERE version = :v: si otra petición ganó la carrera, 412 igual
        if (!cuentas.actualizar(nueva, versionEsperada)) {
            throw new ExcepcionVersionNoCoincide();
        }

        // 6. Auditoría con el valor anterior y el nuevo, en la misma transacción
        auditoria.registrar("cuenta_contable", id.toString(), "ACTUALIZAR", instantanea(actual), instantanea(nueva));
        return cuentas.buscar(id).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /** Nombre sin espacios en los extremos; vacío es un error de validación (422 PLT-002). */
    private static String normalizarNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("nombre", "El nombre es obligatorio")));
        }
        return limpio;
    }

    /** Campos auditables de la cuenta (sin la versión, que cambia siempre). */
    private static Map<String, Object> instantanea(Cuenta c) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("codigo", c.codigo().valor());
        valores.put("nombre", c.nombre());
        valores.put("naturaleza", c.naturaleza().name());
        valores.put("aceptaMovimientos", c.aceptaMovimientos());
        valores.put("activa", c.activa());
        return valores;
    }
}
