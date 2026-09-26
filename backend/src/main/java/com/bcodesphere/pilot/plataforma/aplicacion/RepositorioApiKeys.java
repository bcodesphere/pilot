package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.dominio.AlcanceClave;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyAutenticable;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyRegistrada;
import com.bcodesphere.pilot.plataforma.dominio.CursorApiKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de salida: persistencia de las API keys (tabla {@code api_key}, V7). Salvo {@link #buscarPorPrefijo}, todas
 * las operaciones corren en una transacción con la empresa en el contexto, porque la tabla tiene RLS forzado.
 */
public interface RepositorioApiKeys {

    /**
     * Inserta una clave nueva salvo que su prefijo ya exista.
     *
     * @param id UUID v7 de la clave
     * @param empresaId empresa activa (la política RLS exige que coincida con el contexto)
     * @param nombre nombre normalizado
     * @param prefijo prefijo generado
     * @param hashSecreto hash Argon2id del secreto
     * @param alcances alcances concedidos
     * @param expiraEn vencimiento, o nulo
     * @param creadoPor usuario que la crea
     * @return el momento de creación si se insertó; vacío si el prefijo ya existía (el llamador reintenta con otro)
     */
    Optional<Instant> guardarSiPrefijoLibre(
            UUID id,
            UUID empresaId,
            String nombre,
            String prefijo,
            String hashSecreto,
            Set<AlcanceClave> alcances,
            Instant expiraEn,
            String creadoPor);

    /**
     * Lista claves de la empresa activa (vigentes y revocadas) ordenadas por {@code (creado_en DESC, id DESC)}, en una
     * sola consulta.
     *
     * @param maximo cantidad máxima de filas a devolver (el llamador pide una más para saber si hay otra página)
     * @param despuesDe posición tras la cual continuar; nulo para la primera página
     * @return las claves, sin secreto ni hash
     */
    List<ApiKeyRegistrada> listar(int maximo, CursorApiKey despuesDe);

    /**
     * Revoca la clave si estaba vigente.
     *
     * @param id clave a revocar
     * @return la clave ya revocada; vacío si no estaba vigente (no existe, es de otra empresa o ya estaba revocada)
     */
    Optional<ApiKeyRegistrada> revocarSiVigente(UUID id);

    /**
     * Indica si la clave existe en la empresa activa.
     *
     * @param id clave a buscar
     * @return {@code true} si existe (RLS oculta las de otras empresas)
     */
    boolean existe(UUID id);

    /**
     * Busca una clave por su prefijo con la función {@code api_key_por_prefijo} (ADR-026). Se llama en modo «sin
     * empresa», porque aún no se sabe a qué empresa pertenece.
     *
     * @param prefijo prefijo presentado
     * @return la clave con su hash, incluso si está revocada o vencida; vacío si el prefijo no existe
     */
    Optional<ApiKeyAutenticable> buscarPorPrefijo(String prefijo);

    /**
     * Registra el último uso, como mucho una vez por minuto por clave (evita escribir en cada petición).
     *
     * @param id clave usada
     * @return {@code true} si se actualizó; {@code false} si ya se había registrado hace menos de un minuto
     */
    boolean registrarUso(UUID id);
}
