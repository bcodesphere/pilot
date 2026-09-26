package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.AlcanceApiKey;
import com.bcodesphere.pilot.compartido.api.contrato.ApiKey;
import com.bcodesphere.pilot.compartido.api.contrato.ApiKeyCreada;
import com.bcodesphere.pilot.compartido.api.contrato.PaginaApiKeys;
import com.bcodesphere.pilot.plataforma.aplicacion.GestionarApiKeys;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyRegistrada;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Mapeo entre el dominio de API keys y los DTO generados desde el contrato (CLAUDE.md 4.3, 8.3): sin lógica de negocio.
 * Los instantes salen en UTC; el secreto solo se mapea en {@link #aCreada}.
 */
final class MapeadorApiKeys {

    private MapeadorApiKeys() {}

    /** Datos públicos de una clave, sin secreto ni hash. */
    static ApiKey aDto(ApiKeyRegistrada k) {
        return new ApiKey(
                k.id(),
                k.nombre(),
                k.prefijo(),
                alcances(k),
                utc(k.expiraEn()),
                utc(k.revocadaEn()),
                utc(k.ultimoUsoEn()),
                utc(k.creadaEn()));
    }

    /** Respuesta del 201: los datos de la clave más el secreto completo, que es la única vez que se entrega. */
    static ApiKeyCreada aCreada(GestionarApiKeys.Creada c) {
        ApiKeyRegistrada k = c.clave();
        return new ApiKeyCreada(
                k.id(),
                k.nombre(),
                k.prefijo(),
                alcances(k),
                utc(k.expiraEn()),
                utc(k.revocadaEn()),
                utc(k.ultimoUsoEn()),
                utc(k.creadaEn()),
                c.secretoCompleto());
    }

    /** Página del listado. */
    static PaginaApiKeys aPagina(GestionarApiKeys.Pagina p) {
        return new PaginaApiKeys(
                        p.elementos().stream().map(MapeadorApiKeys::aDto).toList())
                .siguienteCursor(p.siguienteCursor());
    }

    private static List<AlcanceApiKey> alcances(ApiKeyRegistrada k) {
        return k.alcances().stream().map(AlcanceApiKey::fromValue).toList();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}
