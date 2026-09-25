package com.bcodesphere.pilot.compartido.api;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionDominio;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controlador solo de prueba que dispara cada tipo de error para verificar el manejador global. */
@RestController
class ControladorDePrueba {

    /** Cuerpo con un campo obligatorio y un monto. */
    record Cuerpo(@NotBlank String nombre, Dinero monto) {}

    /** Lanza una ExcepcionValidacion. */
    @GetMapping("/prueba/validacion")
    void validacion() {
        throw new ExcepcionValidacion(
                "CON-001", "Asiento inválido", List.of(new ErrorCampo("lineas", "Mínimo dos líneas")));
    }

    /** Lanza un error de dominio con diferencia. */
    @GetMapping("/prueba/descuadre")
    void descuadre() {
        throw new ExcepcionDominio("CON-005", 422, "Asiento descuadrado", Dinero.de("10.5")) {
            private static final long serialVersionUID = 1L;
        };
    }

    /** Lanza una excepción inesperada con un mensaje secreto. */
    @GetMapping("/prueba/fallo")
    void fallo() {
        throw new RuntimeException("secreto-interno");
    }

    /** Recibe un cuerpo validado. */
    @PostMapping("/prueba/cuerpo")
    void cuerpo(@Valid @RequestBody Cuerpo cuerpo) {}

    /** Exige el header If-Match (edición con concurrencia optimista). */
    @PatchMapping("/prueba/if-match")
    void ifMatch(@RequestHeader("If-Match") String version) {}

    /** Exige el header Idempotency-Key. */
    @PostMapping("/prueba/idempotencia")
    void idempotencia(@RequestHeader("Idempotency-Key") String clave) {}

    /** Exige un header cualquiera (X-Empresa-Id). */
    @GetMapping("/prueba/empresa")
    void empresa(@RequestHeader("X-Empresa-Id") String empresa) {}

    /** Exige un parámetro de consulta. */
    @GetMapping("/prueba/parametro")
    void parametro(@RequestParam("desde") String desde) {}

    /** Recibe un UUID en la ruta. */
    @GetMapping("/prueba/uuid/{id}")
    void uuid(@PathVariable UUID id) {}
}
