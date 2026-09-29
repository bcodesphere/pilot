package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.aplicacion.RepositorioApiKeys;
import com.bcodesphere.pilot.plataforma.dominio.AlcanceClave;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyAutenticable;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyRegistrada;
import com.bcodesphere.pilot.plataforma.dominio.CursorApiKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioApiKeys} sobre {@code api_key} (V7) y la función {@code api_key_por_prefijo}
 * (V8). La tabla tiene RLS forzado: salvo la búsqueda por prefijo, todo corre con la empresa en el contexto. Todo el
 * SQL es parametrizado. {@code pilot_app} solo puede insertar, leer y actualizar {@code revocada_en} y
 * {@code ultimo_uso_en} (V7); nunca se lee ni se devuelve el hash fuera de la búsqueda de autenticación.
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): además de RLS, toda sentencia sobre {@code api_key} (salvo la
 * búsqueda por prefijo, que corre en modo «sin empresa», ADR-026) filtra explícitamente por {@code empresa_id} de la
 * empresa activa del contexto, igual que {@code RepositorioCuentasJdbc} en {@code contabilidad}. Es redundante con la
 * política {@code aislamiento_empresa} de V7 mientras RLS esté bien configurado; deja de serlo si algún día no lo
 * está.
 */
@Repository
class RepositorioApiKeysJdbc implements RepositorioApiKeys {

    /** Columnas públicas de una clave (jamás {@code hash_secreto}). */
    private static final String COLUMNAS =
            "id, nombre, prefijo, alcances, expira_en, revocada_en, ultimo_uso_en, creado_en";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioApiKeysJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Instant> guardarSiPrefijoLibre(
            UUID id,
            UUID empresaId,
            String nombre,
            String prefijo,
            String hashSecreto,
            Set<AlcanceClave> alcances,
            Instant expiraEn,
            String creadoPor) {
        // ON CONFLICT DO NOTHING: un prefijo repetido no lanza error (que abortaría la transacción de PostgreSQL) y el
        // caso de uso puede reintentar con otro dentro de la misma transacción. RETURNING solo devuelve fila si insertó
        return jdbc.sql("INSERT INTO api_key (id, empresa_id, nombre, prefijo, hash_secreto, alcances, expira_en,"
                        + " creado_por) VALUES (:id, :empresa, :nombre, :prefijo, :hash, :alcances, :expira, :por)"
                        + " ON CONFLICT (prefijo) DO NOTHING RETURNING creado_en")
                .param("id", id)
                .param("empresa", empresaId)
                .param("nombre", nombre)
                .param("prefijo", prefijo)
                .param("hash", hashSecreto)
                .param("alcances", alcances.stream().map(AlcanceClave::codigo).toArray(String[]::new))
                .param("expira", aOffset(expiraEn), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("por", creadoPor)
                .query((rs, n) ->
                        rs.getObject("creado_en", OffsetDateTime.class).toInstant())
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ApiKeyRegistrada> listar(int maximo, CursorApiKey despuesDe) {
        // Defensa en profundidad (1.1.3): empresa_id = :empresa además de RLS. Comparación de fila (creado_en, id)
        // para el orden estable DESC/DESC; sin cursor se omite ese filtro.
        if (despuesDe == null) {
            return jdbc.sql("SELECT " + COLUMNAS + " FROM api_key WHERE empresa_id = :empresa"
                            + " ORDER BY creado_en DESC, id DESC LIMIT :maximo")
                    .param("empresa", empresa())
                    .param("maximo", maximo)
                    .query((rs, n) -> mapear(rs))
                    .list();
        }
        return jdbc.sql("SELECT " + COLUMNAS + " FROM api_key WHERE empresa_id = :empresa"
                        + " AND (creado_en, id) < (:creado, :id) ORDER BY creado_en DESC, id DESC LIMIT :maximo")
                .param("empresa", empresa())
                .param("creado", aOffset(despuesDe.creadoEn()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", despuesDe.id())
                .param("maximo", maximo)
                .query((rs, n) -> mapear(rs))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ApiKeyRegistrada> revocarSiVigente(UUID id) {
        // WHERE revocada_en IS NULL: una clave ya revocada no se toca (no cambia la fecha de revocación original).
        // empresa_id = :empresa: defensa en profundidad (1.1.3), además de RLS
        return jdbc.sql("UPDATE api_key SET revocada_en = now() WHERE id = :id AND empresa_id = :empresa"
                        + " AND revocada_en IS NULL RETURNING " + COLUMNAS)
                .param("id", id)
                .param("empresa", empresa())
                .query((rs, n) -> mapear(rs))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean existe(UUID id) {
        // empresa_id = :empresa: defensa en profundidad (1.1.3), además de RLS
        return jdbc.sql("SELECT count(*) FROM api_key WHERE id = :id AND empresa_id = :empresa")
                        .param("id", id)
                        .param("empresa", empresa())
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ApiKeyAutenticable> buscarPorPrefijo(String prefijo) {
        // Función SECURITY DEFINER de V8: devuelve solo lo necesario para verificar el secreto y derivar la empresa
        return jdbc.sql("SELECT id, empresa_id, hash_secreto, alcances, expira_en, revocada_en"
                        + " FROM api_key_por_prefijo(:prefijo)")
                .param("prefijo", prefijo)
                .query((rs, n) -> new ApiKeyAutenticable(
                        rs.getObject("id", UUID.class),
                        rs.getObject("empresa_id", UUID.class),
                        rs.getString("hash_secreto"),
                        alcances(rs),
                        instante(rs, "expira_en"),
                        instante(rs, "revocada_en")))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registrarUso(UUID id) {
        // Como mucho una escritura por minuto por clave: la condición del WHERE la aplica la base, así que también
        // vale entre varias instancias. Solo se toca ultimo_uso_en, la única columna de uso concedida (V7).
        // empresa_id = :empresa: defensa en profundidad (1.1.3); FiltroApiKey ya llama con la empresa de la propia
        // clave en el contexto (ContextoEmpresa.ejecutarCon), así que este filtro nunca reduce el resultado esperado
        return jdbc.sql("UPDATE api_key SET ultimo_uso_en = now() WHERE id = :id AND empresa_id = :empresa"
                                + " AND (ultimo_uso_en IS NULL OR ultimo_uso_en < now() - interval '1 minute')")
                        .param("id", id)
                        .param("empresa", empresa())
                        .update()
                > 0;
    }

    /** Empresa activa del contexto: filtra cada sentencia como defensa en profundidad sobre RLS (1.1.3). */
    private static UUID empresa() {
        return ContextoEmpresa.empresaRequerida().valor();
    }

    /** Convierte una fila de columnas públicas en {@link ApiKeyRegistrada}. */
    private static ApiKeyRegistrada mapear(ResultSet rs) throws SQLException {
        return new ApiKeyRegistrada(
                rs.getObject("id", UUID.class),
                rs.getString("nombre"),
                rs.getString("prefijo"),
                alcances(rs),
                instante(rs, "expira_en"),
                instante(rs, "revocada_en"),
                instante(rs, "ultimo_uso_en"),
                instante(rs, "creado_en"));
    }

    /** Lee la columna {@code alcances} (text[]) como lista de códigos. */
    private static List<String> alcances(ResultSet rs) throws SQLException {
        Object[] valores = (Object[]) rs.getArray("alcances").getArray();
        return Arrays.stream(valores).map(Object::toString).collect(Collectors.toList());
    }

    /** Lee un timestamptz que puede ser nulo. */
    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    /** Convierte un instante (posiblemente nulo) a {@link OffsetDateTime} UTC para el driver. */
    private static OffsetDateTime aOffset(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}
