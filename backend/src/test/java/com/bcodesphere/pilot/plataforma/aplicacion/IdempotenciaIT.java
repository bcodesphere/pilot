package com.bcodesphere.pilot.plataforma.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.ExcepcionDominio;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import com.bcodesphere.pilot.plataforma.ServicioIdempotencia;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pruebas de idempotencia contra PostgreSQL real como {@code pilot_app} (CLAUDE.md 12.6): repetición, cuerpo distinto,
 * qué respuestas se guardan, aislamiento entre empresas y concurrencia con bloqueo consultivo.
 */
class IdempotenciaIT extends BasePlataformaIT {

    @Autowired
    private ServicioIdempotencia servicio;

    @Autowired
    private PlatformTransactionManager gestor;

    /** Ejecuta la operación idempotente en su propia transacción y con la empresa indicada. */
    private RespuestaIdempotente llamar(
            EmpresaId empresa, String clave, String cuerpo, Supplier<RespuestaIdempotente> operacion) {
        return ContextoEmpresa.ejecutarCon(
                empresa,
                "u",
                () -> new TransactionTemplate(gestor).execute(s -> servicio.ejecutar(clave, cuerpo, operacion)));
    }

    /** Cuenta las filas de una clave con el dueño (superusuario de pruebas, salta RLS) para ver todas las empresas. */
    private static int filas(EmpresaId empresa, String clave) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM idempotencia WHERE empresa_id = ?::uuid AND clave = ?")) {
            ps.setString(1, empresa.toString());
            ps.setString(2, clave);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static EmpresaId nuevaEmpresa() {
        return new EmpresaId(GeneradorId.nuevo());
    }

    /** Caso: misma clave y mismo cuerpo -> el Supplier corre una vez y la segunda respuesta es la original, repetida. */
    @Test
    void mismaClaveYMismoCuerpoEjecutaUnaSolaVez() {
        EmpresaId empresa = nuevaEmpresa();
        AtomicInteger ejecuciones = new AtomicInteger();
        Supplier<RespuestaIdempotente> operacion = () -> {
            ejecuciones.incrementAndGet();
            return RespuestaIdempotente.de(201, "{\"id\":\"abc\"}");
        };

        RespuestaIdempotente primera = llamar(empresa, "k-1", "{\"x\":1}", operacion);
        RespuestaIdempotente segunda = llamar(empresa, "k-1", "{\"x\":1}", operacion);

        assertThat(ejecuciones).hasValue(1);
        assertThat(primera.repetida()).isFalse();
        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.estadoHttp()).isEqualTo(201);
        assertThat(segunda.cuerpoJson()).contains("\"id\"").contains("abc");
    }

    /** Caso: misma clave con otro cuerpo -> 422 PLT-005 y la operación no se ejecuta. */
    @Test
    void mismaClaveConOtroCuerpoEs422() {
        EmpresaId empresa = nuevaEmpresa();
        llamar(empresa, "k-2", "{\"x\":1}", () -> RespuestaIdempotente.de(200, "{}"));
        AtomicInteger ejecuciones = new AtomicInteger();

        assertThatThrownBy(() -> llamar(empresa, "k-2", "{\"x\":2}", () -> {
                    ejecuciones.incrementAndGet();
                    return RespuestaIdempotente.de(200, "{}");
                }))
                .isInstanceOfSatisfying(ExcepcionDominio.class, e -> {
                    assertThat(e.codigo()).isEqualTo("PLT-005");
                    assertThat(e.estadoHttp()).isEqualTo(422);
                });
        assertThat(ejecuciones).hasValue(0);
    }

    /** Caso: una respuesta 4xx no se guarda, así que el mismo cuerpo corregido o repetido vuelve a ejecutarse. */
    @Test
    void unaRespuesta4xxNoSeGuarda() throws SQLException {
        EmpresaId empresa = nuevaEmpresa();
        AtomicInteger ejecuciones = new AtomicInteger();
        Supplier<RespuestaIdempotente> rechaza = () -> {
            ejecuciones.incrementAndGet();
            return RespuestaIdempotente.de(422, "{\"codigo\":\"CON-005\"}");
        };

        llamar(empresa, "k-3", "{}", rechaza);
        RespuestaIdempotente segunda = llamar(empresa, "k-3", "{}", rechaza);

        assertThat(ejecuciones).hasValue(2);
        assertThat(segunda.repetida()).isFalse();
        assertThat(filas(empresa, "k-3")).isZero();
    }

    /** Caso: un estado no 2xx incluido en la lista explícita sí se guarda (409 INT-004 del webhook, 12.6 punto 5). */
    @Test
    void unEstadoDeLaListaExplicitaSeGuarda() {
        EmpresaId empresa = nuevaEmpresa();
        AtomicInteger ejecuciones = new AtomicInteger();
        Supplier<RespuestaIdempotente> conflicto = () -> {
            ejecuciones.incrementAndGet();
            return RespuestaIdempotente.de(409, "{\"codigo\":\"INT-004\"}");
        };
        Callable<RespuestaIdempotente> llamada = () -> ContextoEmpresa.ejecutarCon(
                empresa,
                "u",
                () -> new TransactionTemplate(gestor)
                        .execute(s -> servicio.ejecutar("k-4", "{}", Set.of(409), conflicto)));

        RespuestaIdempotente primera = ejecutar(llamada);
        RespuestaIdempotente segunda = ejecutar(llamada);

        assertThat(ejecuciones).hasValue(1);
        assertThat(primera.repetida()).isFalse();
        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.estadoHttp()).isEqualTo(409);
    }

    /** Caso: la misma clave en las empresas A y B no colisiona; cada una ejecuta y guarda lo suyo. */
    @Test
    void laMismaClaveEnDosEmpresasNoColisiona() throws SQLException {
        EmpresaId a = nuevaEmpresa();
        EmpresaId b = nuevaEmpresa();
        AtomicInteger ejecuciones = new AtomicInteger();
        Supplier<RespuestaIdempotente> operacion = () -> {
            ejecuciones.incrementAndGet();
            return RespuestaIdempotente.de(201, "{}");
        };

        RespuestaIdempotente deA = llamar(a, "misma", "{}", operacion);
        RespuestaIdempotente deB = llamar(b, "misma", "{\"otro\":true}", operacion);

        assertThat(ejecuciones).hasValue(2);
        assertThat(deA.repetida()).isFalse();
        assertThat(deB.repetida()).isFalse();
        assertThat(filas(a, "misma")).isEqualTo(1);
        assertThat(filas(b, "misma")).isEqualTo(1);
    }

    /** Caso: un cuerpo de respuesta nulo se guarda y se devuelve como nulo. */
    @Test
    void unaRespuestaSinCuerpoSeRepiteSinCuerpo() {
        EmpresaId empresa = nuevaEmpresa();

        llamar(empresa, "k-5", "", () -> RespuestaIdempotente.de(204, null));
        RespuestaIdempotente segunda = llamar(empresa, "k-5", "", () -> RespuestaIdempotente.de(204, "{}"));

        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.cuerpoJson()).isNull();
    }

    /** Caso: dos hilos con la misma clave y cuerpo -> el Supplier corre una sola vez y ambos reciben lo mismo. */
    @Test
    void dosHilosConLaMismaClaveEjecutanUnaSolaVez() throws Exception {
        EmpresaId empresa = nuevaEmpresa();
        AtomicInteger ejecuciones = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(2);
        // El Supplier tarda para que el segundo hilo llegue mientras el primero aún no confirma
        Supplier<RespuestaIdempotente> lenta = () -> {
            ejecuciones.incrementAndGet();
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return RespuestaIdempotente.de(201, "{\"n\":1}");
        };
        Callable<RespuestaIdempotente> tarea = () -> {
            salida.countDown();
            salida.await(5, TimeUnit.SECONDS);
            return llamar(empresa, "k-conc", "{\"x\":1}", lenta);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<RespuestaIdempotente>> futuros =
                    new ArrayList<>(List.of(pool.submit(tarea), pool.submit(tarea)));
            RespuestaIdempotente r1 = futuros.get(0).get(30, TimeUnit.SECONDS);
            RespuestaIdempotente r2 = futuros.get(1).get(30, TimeUnit.SECONDS);

            assertThat(ejecuciones).hasValue(1);
            assertThat(r1.estadoHttp()).isEqualTo(201);
            assertThat(r2.estadoHttp()).isEqualTo(201);
            assertThat(r1.cuerpoJson()).isEqualTo(r2.cuerpoJson());
            // Exactamente una de las dos es la repetición
            assertThat(List.of(r1.repetida(), r2.repetida())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Caso: sin transacción activa el servicio falla (MANDATORY): guardar y ejecutar deben ir juntos. */
    @Test
    void sinTransaccionActivaFalla() {
        EmpresaId empresa = nuevaEmpresa();

        assertThatThrownBy(() -> ContextoEmpresa.ejecutarCon(
                        empresa, "u", () -> servicio.ejecutar("k-6", "{}", () -> RespuestaIdempotente.de(200, "{}"))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Caso: clave en blanco -> 428 PLT-006; clave de más de 100 caracteres -> 422 PLT-002. */
    @Test
    void validaLaClave() {
        EmpresaId empresa = nuevaEmpresa();

        assertThatThrownBy(() -> llamar(empresa, " ", "{}", () -> RespuestaIdempotente.de(200, "{}")))
                .isInstanceOfSatisfying(
                        ExcepcionDominio.class, e -> assertThat(e.codigo()).isEqualTo("PLT-006"));
        assertThatThrownBy(() -> llamar(empresa, "k".repeat(101), "{}", () -> RespuestaIdempotente.de(200, "{}")))
                .isInstanceOfSatisfying(
                        ExcepcionDominio.class, e -> assertThat(e.codigo()).isEqualTo("PLT-002"));
    }

    /** Ejecuta una llamada que puede lanzar excepciones comprobadas (solo para la prueba). */
    private static RespuestaIdempotente ejecutar(Callable<RespuestaIdempotente> llamada) {
        try {
            return llamada.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
