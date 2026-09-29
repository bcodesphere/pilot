package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.contabilidad.aplicacion.RegistrarAsientoManual;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaSolicitud;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.SolicitudAsiento;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Criterio de F3: la mayorización es atómica con el asiento (CLAUDE.md 4.1, ADR-018). Si algo falla después de insertar
 * las líneas y de acumular los saldos, no queda ni asiento, ni líneas, ni saldos, ni correlativo, y la clave de
 * idempotencia tampoco se consume. El fallo se simula en el serializador de la respuesta, que corre dentro de la
 * transacción como último paso (después de numerar, insertar, mayorizar y auditar). Se llama al caso de uso
 * directamente, con el contexto de empresa y un contador autenticado, para no crear un contexto de Spring nuevo (cada
 * contexto cacheado retiene un pool de conexiones).
 */
class MayorizacionAtomicaIT extends BaseContabilidadIT {

    @Autowired
    private RegistrarAsientoManual registrar;

    /** Limpia la autenticación de prueba del hilo. */
    @AfterEach
    void limpiarSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /** Un fallo al final de la transacción revierte cabecera, líneas, saldos y correlativo: los saldos no cambian. */
    @Test
    void siFallaDespuesDeMayorizarNingunSaldoCambia() throws Exception {
        Sesion s = sesionConContabilidad();
        SolicitudAsiento solicitud = new SolicitudAsiento(
                LocalDate.of(2026, 1, 15),
                "Asiento que falla al final",
                null,
                List.of(
                        new LineaSolicitud(cuentaId(s.empresa(), "11010101"), null, "100.00", "0", false),
                        new LineaSolicitud(cuentaId(s.empresa(), "51010101"), null, "0", "100.00", false)));
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("prueba", "x", "ROLE_CONTADOR"));

        assertThatThrownBy(() -> ContextoEmpresa.ejecutarCon(
                        new EmpresaId(s.empresa()),
                        s.usuario().toString(),
                        () -> registrar.registrar("clave-fallida", "{}", solicitud, asiento -> {
                            throw new IllegalStateException("fallo simulado al serializar");
                        })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallo simulado");

        // Ni cabecera, ni líneas, ni saldos, ni correlativo, ni respuesta de idempotencia guardada
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM correlativo_asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM idempotencia WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }
}
