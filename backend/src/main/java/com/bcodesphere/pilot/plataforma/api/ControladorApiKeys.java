package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.ApiKeyCreada;
import com.bcodesphere.pilot.compartido.api.contrato.ApiKeysApi;
import com.bcodesphere.pilot.compartido.api.contrato.NuevaApiKey;
import com.bcodesphere.pilot.compartido.api.contrato.PaginaApiKeys;
import com.bcodesphere.pilot.plataforma.aplicacion.GestionarApiKeys;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de {@code GET}, {@code POST} y {@code DELETE /api-keys} (interfaz {@code ApiKeysApi} generada desde el
 * contrato, ADR-003). No lleva lógica: llama al caso de uso y mapea; el rol mínimo {@code admin_empresa} lo exige el
 * caso de uso (CLAUDE.md 8.3).
 */
@RestController
class ControladorApiKeys implements ApiKeysApi {

    private final GestionarApiKeys claves;

    /**
     * Crea el controlador.
     *
     * @param claves caso de uso de gestión de API keys
     */
    ControladorApiKeys(GestionarApiKeys claves) {
        this.claves = claves;
    }

    @Override
    public ResponseEntity<ApiKeyCreada> crearApiKey(UUID xEmpresaId, NuevaApiKey nuevaApiKey, String xRequestId) {
        // Los alcances viajan como códigos: las reglas (repetidos, vencimiento) las aplica el dominio
        List<String> alcances =
                nuevaApiKey.getAlcances().stream().map(a -> a.getValue()).toList();
        var expiraEn = nuevaApiKey.getExpiraEn() == null
                ? null
                : nuevaApiKey.getExpiraEn().toInstant();
        var creada = claves.crear(nuevaApiKey.getNombre(), alcances, expiraEn);
        return ResponseEntity.status(HttpStatus.CREATED).body(MapeadorApiKeys.aCreada(creada));
    }

    @Override
    public ResponseEntity<PaginaApiKeys> listarApiKeys(
            UUID xEmpresaId, String xRequestId, Integer limite, String cursor) {
        return ResponseEntity.ok(MapeadorApiKeys.aPagina(claves.listar(limite, cursor)));
    }

    @Override
    public ResponseEntity<Void> revocarApiKey(UUID xEmpresaId, UUID apiKeyId, String xRequestId) {
        claves.revocar(apiKeyId);
        return ResponseEntity.noContent().build();
    }
}
