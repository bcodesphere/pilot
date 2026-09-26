package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.plataforma.dominio.ApiKeyRegistrada;
import com.bcodesphere.pilot.plataforma.dominio.ClaveApi;
import com.bcodesphere.pilot.plataforma.dominio.CursorApiKey;
import com.bcodesphere.pilot.plataforma.dominio.ReglasNuevaClave;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de {@code GET}, {@code POST} y {@code DELETE /api-keys}: gestión de las API keys de la empresa activa
 * (CLAUDE.md 2.1, 13 y 14). Rol mínimo {@code admin_empresa}. El secreto en claro solo existe dentro de {@link #crear}
 * y en su respuesta: no se guarda, no se audita y no se registra en logs.
 */
@Service
public class GestionarApiKeys {

    /** Intentos máximos de generar un prefijo que no choque con el {@code UNIQUE} (probabilidad de choque ínfima). */
    private static final int INTENTOS_PREFIJO = 3;

    private final RepositorioApiKeys claves;
    private final HashSecreto hash;
    private final RegistroAuditoria auditoria;

    /**
     * Crea el caso de uso.
     *
     * @param claves puerto de persistencia de API keys
     * @param hash puerto de hash del secreto (Argon2id)
     * @param auditoria puerto de auditoría
     */
    public GestionarApiKeys(RepositorioApiKeys claves, HashSecreto hash, RegistroAuditoria auditoria) {
        this.claves = claves;
        this.hash = hash;
        this.auditoria = auditoria;
    }

    /**
     * Una clave recién creada y su secreto completo; es el único lugar donde este último sale del servidor.
     *
     * @param clave datos públicos de la clave
     * @param secretoCompleto {@code pk_xxxxxxxx.secreto}, visible solo en el 201
     */
    public record Creada(ApiKeyRegistrada clave, String secretoCompleto) {

        @Override
        public String toString() {
            return "Creada[clave=" + clave + ", secretoCompleto=***]";
        }
    }

    /**
     * Página de claves.
     *
     * @param elementos claves de la página, sin secreto ni hash
     * @param siguienteCursor cursor opaco de la página siguiente; nulo si no hay más
     */
    public record Pagina(List<ApiKeyRegistrada> elementos, String siguienteCursor) {

        /** Copia defensiva de los elementos: la página es inmutable. */
        public Pagina {
            elementos = List.copyOf(elementos);
        }
    }

    /**
     * Crea una API key: valida, genera prefijo y secreto, guarda solo el hash y audita.
     *
     * @param nombre nombre recibido
     * @param codigosAlcance alcances recibidos
     * @param expiraEn vencimiento recibido, o nulo
     * @return la clave y su secreto completo
     * @throws com.bcodesphere.pilot.compartido.ExcepcionValidacion 422 {@code PLT-002} si los datos son inválidos
     */
    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @Transactional
    public Creada crear(String nombre, List<String> codigosAlcance, Instant expiraEn) {
        // 1. Reglas de forma: nombre, alcances sin repetidos y vencimiento futuro (422 PLT-002)
        var datos = ReglasNuevaClave.validar(nombre, codigosAlcance, expiraEn, Instant.now());
        UUID empresaId = ContextoEmpresa.empresaRequerida().valor();
        String usuario = ContextoEmpresa.usuarioOSistema();
        UUID id = GeneradorId.nuevo();

        // 2. Genera y guarda; si el prefijo ya existe (ON CONFLICT DO NOTHING) se reintenta con otro, hasta 3 veces.
        //    El hash se calcula por cada intento porque depende solo del secreto, que se regenera con el prefijo
        for (int intento = 0; intento < INTENTOS_PREFIJO; intento++) {
            ClaveApi.Generada generada = ClaveApi.generar();
            Optional<Instant> creadaEn = claves.guardarSiPrefijoLibre(
                    id,
                    empresaId,
                    datos.nombre(),
                    generada.prefijo(),
                    hash.hashear(generada.secreto()),
                    datos.alcances(),
                    datos.expiraEn(),
                    usuario);
            if (creadaEn.isPresent()) {
                var registrada = new ApiKeyRegistrada(
                        id,
                        datos.nombre(),
                        generada.prefijo(),
                        datos.alcances().stream().map(a -> a.codigo()).toList(),
                        datos.expiraEn(),
                        null,
                        null,
                        creadaEn.get());
                // 3. Auditoría SIN secreto ni hash: solo lo que identifica la clave
                auditoria.registrar("api_key", id.toString(), "CREAR", null, valorAuditado(registrada));
                return new Creada(registrada, generada.completa());
            }
        }
        // 4. Tres choques seguidos de un espacio de 36^8 no ocurren en la práctica: error interno (500 PLT-500)
        throw new IllegalStateException("No se pudo generar un prefijo de API key único");
    }

    /**
     * Lista las claves de la empresa activa, vigentes y revocadas, con paginación por cursor.
     *
     * @param limite tamaño de página (1 a 200; el contrato lo valida)
     * @param cursor cursor de la página anterior, o nulo
     * @return la página
     * @throws com.bcodesphere.pilot.compartido.ExcepcionValidacion 422 {@code PLT-002} si el cursor es inválido
     */
    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @Transactional(readOnly = true)
    public Pagina listar(int limite, String cursor) {
        // 1. Cursor alterado = 422 antes de tocar la base
        CursorApiKey desde = cursor == null || cursor.isEmpty() ? null : CursorApiKey.decodificar(cursor);
        // 2. Una sola consulta: se pide una fila de más para saber si existe otra página
        List<ApiKeyRegistrada> filas = claves.listar(limite + 1, desde);
        if (filas.size() <= limite) {
            return new Pagina(filas, null);
        }
        List<ApiKeyRegistrada> pagina = filas.subList(0, limite);
        ApiKeyRegistrada ultima = pagina.get(limite - 1);
        return new Pagina(List.copyOf(pagina), new CursorApiKey(ultima.creadaEn(), ultima.id()).codificar());
    }

    /**
     * Revoca una clave. Es idempotente: si ya estaba revocada no escribe nada.
     *
     * @param id clave a revocar
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si no existe o es de otra empresa
     */
    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @Transactional
    public void revocar(UUID id) {
        // 1. UPDATE ... WHERE revocada_en IS NULL: solo la primera revocación escribe (y audita)
        Optional<ApiKeyRegistrada> revocada = claves.revocarSiVigente(id);
        if (revocada.isPresent()) {
            ApiKeyRegistrada r = revocada.get();
            Map<String, Object> anterior = new LinkedHashMap<>();
            anterior.put("prefijo", r.prefijo());
            anterior.put("revocadaEn", null);
            Map<String, Object> nuevo = new LinkedHashMap<>();
            nuevo.put("prefijo", r.prefijo());
            nuevo.put("revocadaEn", r.revocadaEn().toString());
            auditoria.registrar("api_key", id.toString(), "REVOCAR", anterior, nuevo);
            return;
        }
        // 2. No estaba vigente: si existe, ya estaba revocada (204 sin escribir); si no, 404 (RLS oculta otras
        // empresas)
        if (!claves.existe(id)) {
            throw ExcepcionPlataforma.noEncontrado();
        }
    }

    /** Valor auditado de una clave: identifica la clave y sus permisos, nunca el secreto ni el hash. */
    private static Map<String, Object> valorAuditado(ApiKeyRegistrada k) {
        Map<String, Object> valor = new LinkedHashMap<>();
        valor.put("id", k.id().toString());
        valor.put("nombre", k.nombre());
        valor.put("prefijo", k.prefijo());
        valor.put("alcances", k.alcances());
        valor.put("expiraEn", k.expiraEn() == null ? null : k.expiraEn().toString());
        return valor;
    }
}
