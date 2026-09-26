package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.dominio.AlcanceClave;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyAutenticable;
import com.bcodesphere.pilot.plataforma.dominio.ClaveApi;
import com.bcodesphere.pilot.plataforma.dominio.VigenciaClave;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caso de uso «autenticar una API key» (CLAUDE.md 12.1 y 14, ADR-026): a partir de {@code pk_xxxx.secreto} decide si la
 * petición es válida y de qué empresa es.
 *
 * <p>Busca la clave por prefijo en el modo «sin empresa» (aún no se conoce la empresa; solo se usa la función
 * {@code api_key_por_prefijo}) y verifica el secreto con Argon2id. Prefijo inexistente, secreto incorrecto, clave
 * revocada y clave vencida son indistinguibles para quien llama: el resultado es siempre «vacío», y la verificación de
 * hash se ejecuta igual contra un hash ficticio cuando el prefijo no existe, para que el tiempo de respuesta no revele
 * qué prefijos existen.
 */
@Service
public class AutenticarApiKey {

    private final RepositorioApiKeys claves;
    private final HashSecreto hash;
    private final TransactionTemplate lectura;

    /** Hash de un secreto aleatorio descartado: sirve para igualar el trabajo cuando el prefijo no existe. */
    private final String hashFicticio;

    /**
     * Crea el caso de uso.
     *
     * @param claves puerto de persistencia de API keys
     * @param hash puerto de hash del secreto
     * @param gestor gestor de transacciones de la aplicación
     */
    public AutenticarApiKey(RepositorioApiKeys claves, HashSecreto hash, PlatformTransactionManager gestor) {
        this.claves = claves;
        this.hash = hash;
        this.lectura = new TransactionTemplate(gestor);
        this.lectura.setReadOnly(true);
        // El secreto ficticio es aleatorio y se descarta: nadie puede presentarlo
        this.hashFicticio = hash.hashear(ClaveApi.generar().secreto());
    }

    /**
     * Clave autenticada: lo único que necesita el resto de la petición.
     *
     * @param id identificador de la clave
     * @param empresaId empresa de la clave (el contexto de la petición)
     * @param alcances alcances concedidos
     */
    public record Autenticada(UUID id, UUID empresaId, Set<AlcanceClave> alcances) {

        /** Copia defensiva de los alcances: el registro es inmutable y no expone el conjunto recibido. */
        public Autenticada {
            alcances = Set.copyOf(alcances);
        }

        /**
         * Identificador que se fija como usuario del contexto y de la auditoría ({@code api_key:<id>}).
         *
         * @return el usuario técnico de la clave
         */
        public String usuarioContexto() {
            return "api_key:" + id;
        }
    }

    /**
     * Autentica una credencial.
     *
     * @param credencial texto tras {@code Bearer }; puede ser nulo
     * @return la clave autenticada, o vacío por CUALQUIER motivo de rechazo (sin distinguir cuál)
     */
    public Optional<Autenticada> autenticar(String credencial) {
        // 1. Formato: si no es pk_xxxxxxxx.secreto no hay nada que buscar (esto no revela qué prefijos existen)
        Optional<ClaveApi.Presentada> presentada = ClaveApi.interpretar(credencial);
        if (presentada.isEmpty()) {
            return Optional.empty();
        }

        // 2. Búsqueda por prefijo en modo «sin empresa» (ADR-026): función SECURITY DEFINER, tabla con RLS intacta
        Optional<ApiKeyAutenticable> encontrada = ContextoEmpresa.ejecutarSinEmpresa(
                null,
                () -> lectura.execute(
                        s -> claves.buscarPorPrefijo(presentada.get().prefijo())));

        // 3. Verifica SIEMPRE el secreto con Argon2id, contra el hash real o el ficticio, para tiempo constante
        String hashObjetivo = encontrada.map(ApiKeyAutenticable::hashSecreto).orElse(hashFicticio);
        boolean secretoCorrecto = hash.coincide(presentada.get().secreto(), hashObjetivo);

        // 4. Solo se acepta si existe, el secreto es correcto y no está revocada ni vencida
        if (encontrada.isEmpty() || !secretoCorrecto) {
            return Optional.empty();
        }
        ApiKeyAutenticable clave = encontrada.get();
        if (!VigenciaClave.estaVigente(clave.revocadaEn(), clave.expiraEn(), Instant.now())) {
            return Optional.empty();
        }

        // 5. Alcances conocidos; uno desconocido (imposible por el CHECK de V7) se ignora en vez de concederse
        Set<AlcanceClave> alcances = EnumSet.noneOf(AlcanceClave.class);
        clave.alcances().forEach(c -> AlcanceClave.deCodigo(c).ifPresent(alcances::add));
        return Optional.of(new Autenticada(clave.id(), clave.empresaId(), alcances));
    }

    /**
     * Registra el último uso de la clave, como mucho una vez por minuto. Debe llamarse con la empresa de la clave en el
     * contexto, porque {@code api_key} tiene RLS.
     *
     * @param id clave usada
     */
    @Transactional
    public void registrarUso(UUID id) {
        claves.registrarUso(id);
    }
}
