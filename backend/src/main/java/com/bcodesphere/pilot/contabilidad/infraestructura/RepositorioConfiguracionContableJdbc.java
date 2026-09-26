package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.contabilidad.aplicacion.RepositorioConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioConfiguracionContable} sobre {@code configuracion_contable} (V11), una fila por
 * empresa. RLS forzado: solo funciona dentro de una transacción con el contexto de la empresa.
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): además de RLS, toda sentencia filtra explícitamente por
 * {@code empresa_id} de la empresa activa, y los JOIN igualan también la empresa.
 */
@Repository
class RepositorioConfiguracionContableJdbc implements RepositorioConfiguracionContable {

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioConfiguracionContableJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ConfiguracionContable> obtener() {
        // Une las dos cuentas de IVA para devolver su resumen (id, código y nombre)
        return jdbc.sql("SELECT k.modo_precio_defecto, k.version,"
                        + " d.id AS d_id, d.codigo AS d_codigo, d.nombre AS d_nombre,"
                        + " c.id AS c_id, c.codigo AS c_codigo, c.nombre AS c_nombre"
                        + " FROM configuracion_contable k"
                        + " JOIN cuenta_contable d ON d.empresa_id = k.empresa_id AND d.id = k.cuenta_iva_debito_id"
                        + " JOIN cuenta_contable c ON c.empresa_id = k.empresa_id AND c.id = k.cuenta_iva_credito_id"
                        + " WHERE k.empresa_id = :empresa")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .query((rs, n) -> new ConfiguracionContable(
                        ModoPrecio.valueOf(rs.getString("modo_precio_defecto")),
                        new ResumenCuenta(
                                rs.getObject("d_id", UUID.class), rs.getString("d_codigo"), rs.getString("d_nombre")),
                        new ResumenCuenta(
                                rs.getObject("c_id", UUID.class), rs.getString("c_codigo"), rs.getString("c_nombre")),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean actualizar(ModoPrecio modo, UUID cuentaIvaDebitoId, UUID cuentaIvaCreditoId, long versionEsperada) {
        // WHERE version = :v es el control optimista; RLS limita la fila a la empresa activa
        return jdbc.sql("UPDATE configuracion_contable SET modo_precio_defecto = :modo,"
                                + " cuenta_iva_debito_id = :debito, cuenta_iva_credito_id = :credito,"
                                + " version = version + 1, actualizado_en = now(), actualizado_por = :por"
                                + " WHERE empresa_id = :empresa AND version = :version")
                        .param("modo", modo.name())
                        .param("debito", cuentaIvaDebitoId)
                        .param("credito", cuentaIvaCreditoId)
                        .param("por", ContextoEmpresa.usuarioOSistema())
                        .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                        .param("version", versionEsperada)
                        .update()
                == 1;
    }
}
