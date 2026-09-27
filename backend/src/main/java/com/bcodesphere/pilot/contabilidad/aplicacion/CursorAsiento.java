package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Cursor opaco del Libro Diario: la llave (año, número) del último asiento entregado, en Base64 URL. El cliente no
 * debe interpretarlo; solo devolverlo tal cual.
 *
 * @param anio año del último asiento de la página
 * @param numero número del último asiento de la página
 */
record CursorAsiento(int anio, long numero) {

    /**
     * Codifica la llave como cadena opaca.
     *
     * @return cursor listo para la respuesta
     */
    String codificar() {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((anio + ":" + numero).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Interpreta un cursor recibido.
     *
     * @param cursor texto recibido en {@code ?cursor=}
     * @return la llave
     * @throws ExcepcionValidacion 422 {@code PLT-002} si el cursor no es uno emitido por esta API
     */
    static CursorAsiento decodificar(String cursor) {
        try {
            String[] partes = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":");
            if (partes.length != 2) {
                throw new IllegalArgumentException("Forma inesperada");
            }
            return new CursorAsiento(Integer.parseInt(partes[0]), Long.parseLong(partes[1]));
        } catch (IllegalArgumentException e) {
            // Base64 roto o números ilegibles: el cursor no lo emitimos nosotros
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("cursor", "Cursor inválido")));
        }
    }
}
