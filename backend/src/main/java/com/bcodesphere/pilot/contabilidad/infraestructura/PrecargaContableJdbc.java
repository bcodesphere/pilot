package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.contabilidad.aplicacion.PrecargaContable;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link PrecargaContable}: copia las plantillas globales (V10) a la empresa activa con
 * {@code INSERT ... SELECT} (ADR-034, ADR-035). Exige la transacción de la instalación (MANDATORY): si algo falla, la
 * excepción sube y se revierte todo. Los UUID v7 se generan en Java (ADR-010) y viajan a SQL como texto separado por
 * comas.
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): además de RLS, las sentencias sobre tablas de la empresa
 * filtran por {@code empresa_id} y los JOIN igualan la empresa.
 */
@Repository
class PrecargaContableJdbc implements PrecargaContable {

    /** Niveles del catálogo, del más alto al más bajo: los padres se insertan antes que sus hijas. */
    private static final int[] NIVELES = {1, 2, 3, 4, 5};

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    PrecargaContableJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Resumen precargar() {
        UUID empresa = ContextoEmpresa.empresaRequerida().valor();
        String por = ContextoEmpresa.usuarioOSistema();

        // 1. Cuentas por nivel: cada nivel se inserta cuando ya existen las cuentas de su nivel superior
        int cuentas = 0;
        for (int nivel : NIVELES) {
            cuentas += copiarCuentasDelNivel(empresa, por, nivel);
        }
        int detalle = jdbc.sql(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = :empresa AND acepta_movimientos")
                .param("empresa", empresa)
                .query(Integer.class)
                .single();

        // 2. Configuración: resuelve los códigos de IVA a los id de las cuentas ya copiadas; debe quedar una fila
        int configuracion = jdbc.sql(
                        "INSERT INTO configuracion_contable (empresa_id, modo_precio_defecto,"
                                + " cuenta_iva_debito_id, cuenta_iva_credito_id, actualizado_por)"
                                + " SELECT :empresa, t.modo_precio_defecto, d.id, c.id, :por"
                                + " FROM plantilla_configuracion_contable t"
                                + " JOIN cuenta_contable d ON d.empresa_id = :empresa AND d.codigo = t.cuenta_iva_debito_codigo"
                                + " JOIN cuenta_contable c ON c.empresa_id = :empresa AND c.codigo = t.cuenta_iva_credito_codigo")
                .param("empresa", empresa)
                .param("por", por)
                .update();
        if (configuracion != 1) {
            throw new IllegalStateException(
                    "La precarga debía crear una configuración contable y creó " + configuracion);
        }

        // 3. Reglas: cuenta_codigo se resuelve a id; OTRO queda inactiva y sin cuenta (LEFT JOIN, ADR-035)
        int reglas = copiarReglas(empresa, por);
        return new Resumen(cuentas, detalle, reglas);
    }

    /** Copia las cuentas de un nivel; devuelve cuántas insertó. */
    private int copiarCuentasDelNivel(UUID empresa, String por, int nivel) {
        // 1. Códigos de la plantilla en este nivel y un UUID v7 por cada uno, generado en Java
        List<String> codigos = jdbc.sql("SELECT codigo FROM plantilla_cuenta WHERE nivel = :nivel ORDER BY codigo")
                .param("nivel", nivel)
                .query(String.class)
                .list();
        if (codigos.isEmpty()) {
            return 0;
        }
        String ids = codigos.stream().map(c -> GeneradorId.nuevo().toString()).collect(Collectors.joining(","));

        // 2. INSERT ... SELECT: empareja código e id por posición (unnest de dos arreglos), busca el padre por el
        //    prefijo de su nivel y marca como hoja (acepta movimientos) la cuenta que no tiene hijas en la plantilla
        return jdbc.sql("INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, cuenta_padre_id,"
                        + " naturaleza, acepta_movimientos, activa, creado_por, actualizado_por)"
                        + " SELECT i.id, :empresa, p.codigo, p.nombre, p.nivel, padre.id, p.naturaleza,"
                        + " NOT EXISTS (SELECT 1 FROM plantilla_cuenta h"
                        + "             WHERE h.codigo <> p.codigo AND starts_with(h.codigo, p.codigo)),"
                        + " true, :por, :por"
                        + " FROM plantilla_cuenta p"
                        + " JOIN unnest(string_to_array(:codigos, ','), string_to_array(:ids, ',')::uuid[])"
                        + "      AS i(codigo, id) ON i.codigo = p.codigo"
                        + " LEFT JOIN cuenta_contable padre ON padre.empresa_id = :empresa AND padre.codigo ="
                        + "      CASE p.nivel WHEN 2 THEN left(p.codigo, 1) WHEN 3 THEN left(p.codigo, 2)"
                        + "                   WHEN 4 THEN left(p.codigo, 4) WHEN 5 THEN left(p.codigo, 6) END"
                        + " WHERE p.nivel = :nivel")
                .param("empresa", empresa)
                .param("por", por)
                .param("codigos", String.join(",", codigos))
                .param("ids", ids)
                .param("nivel", nivel)
                .update();
    }

    /** Copia las reglas de la plantilla; devuelve cuántas insertó. */
    private int copiarReglas(UUID empresa, String por) {
        // 1. Claves de cada regla de la plantilla (son pocas: se insertan una a una con su UUID v7)
        record Clave(String tipo, String categoria, String codigo) {}
        List<Clave> claves = jdbc.sql("SELECT tipo_operacion, categoria, codigo FROM plantilla_regla_contabilizacion"
                        + " ORDER BY tipo_operacion, categoria, codigo")
                .query((rs, n) -> new Clave(rs.getString(1), rs.getString(2), rs.getString(3)))
                .list();

        // 2. INSERT ... SELECT por regla, resolviendo cuenta_codigo al id de la cuenta de la empresa
        int total = 0;
        for (Clave k : claves) {
            total += jdbc.sql("INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo,"
                            + " cuenta_id, activa, creado_por, actualizado_por)"
                            + " SELECT :id, :empresa, r.tipo_operacion, r.categoria, r.codigo, c.id, r.activa, :por, :por"
                            + " FROM plantilla_regla_contabilizacion r"
                            + " LEFT JOIN cuenta_contable c ON c.empresa_id = :empresa AND c.codigo = r.cuenta_codigo"
                            + " WHERE r.tipo_operacion = :tipo AND r.categoria = :categoria AND r.codigo = :codigo")
                    .param("id", GeneradorId.nuevo())
                    .param("empresa", empresa)
                    .param("por", por)
                    .param("tipo", k.tipo())
                    .param("categoria", k.categoria())
                    .param("codigo", k.codigo())
                    .update();
        }
        return total;
    }
}
