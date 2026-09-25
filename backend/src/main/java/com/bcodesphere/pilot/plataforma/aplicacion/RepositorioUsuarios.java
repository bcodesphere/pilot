package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.dominio.DatosIdentidad;
import com.bcodesphere.pilot.plataforma.dominio.Usuario;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: persistencia de {@code usuario}, tabla global sin RLS (ADR-028). Todas sus operaciones exigen una
 * transacción abierta por el llamador, en el contexto que corresponda (sin empresa o con la empresa nueva).
 */
public interface RepositorioUsuarios {

    /**
     * Busca un usuario por el claim {@code sub} de su token.
     *
     * @param sub identificador de Keycloak
     * @return el usuario, o vacío si aún no se dio de alta
     */
    Optional<Usuario> buscarPorSub(String sub);

    /**
     * Busca un usuario por su identificador.
     *
     * @param id UUID del usuario
     * @return el usuario, o vacío si no existe
     */
    Optional<Usuario> buscarPorId(UUID id);

    /**
     * Inserta un usuario nuevo.
     *
     * @param id UUID v7 ya generado
     * @param datos identidad tomada del token; si {@code recomendacionesAceptadas} es verdadero se fija
     *     {@code recomendaciones_aceptadas_en} con la hora de la base de datos
     * @throws org.springframework.dao.DataIntegrityViolationException si el {@code sub} o el correo ya existen
     */
    void crear(UUID id, DatosIdentidad datos);

    /**
     * Actualiza correo, nombre y teléfono desde Keycloak. Nunca toca el consentimiento (ADR-028).
     *
     * @param id usuario a actualizar
     * @param datos identidad actual del token
     */
    void actualizarPerfil(UUID id, DatosIdentidad datos);

    /**
     * Da o retira el consentimiento de recomendaciones.
     *
     * @param id usuario
     * @param aceptado {@code true} fija {@code recomendaciones_aceptadas_en = now()}; {@code false} fija
     *     {@code recomendaciones_retiradas_en = now()}
     */
    void fijarConsentimiento(UUID id, boolean aceptado);
}
