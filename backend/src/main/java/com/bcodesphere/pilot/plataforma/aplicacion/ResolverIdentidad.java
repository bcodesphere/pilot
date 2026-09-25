package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.EnmascaradorDatosPersonales;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.plataforma.dominio.DatosIdentidad;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import com.bcodesphere.pilot.plataforma.dominio.Usuario;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caso de uso «resolver la identidad» (ADR-028, ADR-029): a partir de los datos del token busca al usuario por su
 * {@code sub}; si es el primer inicio de sesión lo da de alta junto con su empresa personal, y si ya existe sincroniza
 * correo, nombre y teléfono desde Keycloak.
 *
 * <p>La búsqueda por {@code sub} corre en el modo «sin empresa» (ADR-026) porque aún no se conoce la empresa: solo
 * toca la tabla global {@code usuario}. El alta corre en el contexto de la empresa NUEVA para que la política RLS de
 * {@code empresa}, que filtra por {@code id}, acepte el INSERT.
 */
@Service
public class ResolverIdentidad {

    private static final Logger LOG = LoggerFactory.getLogger(ResolverIdentidad.class);

    private final RepositorioUsuarios usuarios;
    private final RepositorioEmpresas empresas;
    private final RegistroAuditoria auditoria;
    private final TransactionTemplate lectura;
    private final TransactionTemplate escritura;

    /**
     * Crea el caso de uso.
     *
     * @param usuarios persistencia de usuarios
     * @param empresas persistencia de empresas y membresías
     * @param auditoria puerto de auditoría (global para el usuario, por empresa para lo demás)
     * @param gestor gestor de transacciones de la aplicación (el que fija el contexto en PostgreSQL)
     */
    public ResolverIdentidad(
            RepositorioUsuarios usuarios,
            RepositorioEmpresas empresas,
            RegistroAuditoria auditoria,
            PlatformTransactionManager gestor) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.auditoria = auditoria;
        this.lectura = new TransactionTemplate(gestor);
        this.lectura.setReadOnly(true);
        this.escritura = new TransactionTemplate(gestor);
    }

    /**
     * Devuelve al usuario de la petición, creándolo (con su empresa personal) si es su primer inicio de sesión.
     *
     * @param datos identidad ya validada del token
     * @return el usuario, con los datos sincronizados
     * @throws ExcepcionPlataforma 403 {@code PLT-010} si el usuario está bloqueado; 401 {@code PLT-009} si su correo
     *     ya pertenece a otra cuenta
     */
    public Usuario resolver(DatosIdentidad datos) {
        // 1. Usuario ya registrado: se comprueba el bloqueo y se sincroniza
        Optional<Usuario> existente = buscarPorSub(datos.sub());
        if (existente.isPresent()) {
            return sincronizar(existente.get(), datos);
        }
        // 2. Primer inicio de sesión: alta en una sola transacción
        try {
            return darDeAlta(datos);
        } catch (DataIntegrityViolationException e) {
            // 3. Otra petición del mismo usuario ganó la carrera (UNIQUE de sub_keycloak o uq_empresa_personal):
            //    la transacción ya se revirtió completa, así que solo queda leer lo que creó la ganadora
            LOG.info("Alta concurrente del mismo usuario; se reutiliza el usuario ya creado");
            Usuario ganador = buscarPorSub(datos.sub()).orElseThrow(() -> {
                // 4. Sigue sin existir: el correo ya lo tiene otra cuenta. No se revela cuál
                LOG.warn("No se pudo dar de alta al usuario: el correo ya pertenece a otra cuenta");
                return ExcepcionPlataforma.noAutenticado("No fue posible identificar al usuario");
            });
            return verificarNoBloqueado(ganador);
        }
    }

    /** Busca por {@code sub} en modo sin empresa: solo lee la tabla global {@code usuario}. */
    private Optional<Usuario> buscarPorSub(String sub) {
        return ContextoEmpresa.ejecutarSinEmpresa(null, () -> lectura.execute(s -> usuarios.buscarPorSub(sub)));
    }

    /** Un usuario bloqueado no entra a ninguna operación (PLT-010). */
    private static Usuario verificarNoBloqueado(Usuario usuario) {
        if (usuario.bloqueado()) {
            throw ExcepcionPlataforma.sinPermiso("El usuario está bloqueado");
        }
        return usuario;
    }

    /** Actualiza correo, nombre y teléfono si cambiaron en Keycloak; nunca toca el consentimiento (ADR-028). */
    private Usuario sincronizar(Usuario actual, DatosIdentidad datos) {
        // 1. Bloqueado: no se escribe nada
        verificarNoBloqueado(actual);
        // 2. Sin cambios en Keycloak no hay escritura (esto corre en cada petición)
        if (!actual.difiereDe(datos)) {
            return actual;
        }
        // 3. Cambió el perfil: se actualiza y se audita en auditoria_global con correo y teléfono enmascarados
        try {
            ContextoEmpresa.ejecutarSinEmpresa(
                    actual.id().toString(),
                    () -> escritura.execute(s -> {
                        usuarios.actualizarPerfil(actual.id(), datos);
                        auditoria.registrarGlobal(
                                "usuario",
                                actual.id().toString(),
                                "ACTUALIZAR",
                                perfilEnmascarado(
                                        actual.correo(),
                                        actual.nombre(),
                                        actual.telefono().valor()),
                                perfilEnmascarado(
                                        datos.correo(),
                                        datos.nombre(),
                                        datos.telefono().valor()));
                        return null;
                    }));
        } catch (DataIntegrityViolationException e) {
            // 4. El correo nuevo ya lo usa otra cuenta de Pilot: se conserva el perfil guardado y la sesión sigue
            LOG.warn("No se pudo sincronizar el perfil del usuario {}: correo en conflicto", actual.id());
            return actual;
        }
        return new Usuario(
                actual.id(),
                actual.sub(),
                datos.correo(),
                datos.nombre(),
                datos.telefono(),
                actual.bloqueado(),
                actual.aceptadasEn(),
                actual.retiradasEn());
    }

    /**
     * Crea usuario, empresa personal y membresía de administrador, con su auditoría, en una sola transacción y en el
     * contexto de la empresa nueva (ADR-029). No instala ninguna app (ADR-030).
     */
    private Usuario darDeAlta(DatosIdentidad datos) {
        UUID idUsuario = GeneradorId.nuevo();
        UUID idEmpresa = GeneradorId.nuevo();
        // 1. El contexto lleva la empresa nueva y al usuario nuevo como autor: así RLS deja insertar la empresa y la
        //    auditoría queda a nombre de quien se registra
        return ContextoEmpresa.ejecutarCon(
                new EmpresaId(idEmpresa),
                idUsuario.toString(),
                () -> escritura.execute(s -> {
                    // 2. Usuario (tabla global), empresa PERSONAL con el nombre del usuario y membresía admin_empresa
                    // ACTIVA
                    usuarios.crear(idUsuario, datos);
                    empresas.crearPersonal(idEmpresa, idUsuario, datos.nombre());
                    empresas.crearMembresia(idEmpresa, idUsuario, Rol.ADMIN_EMPRESA);

                    // 3. Auditoría: el usuario en auditoria_global; la empresa y la membresía en auditoria (con
                    // empresa_id)
                    auditoria.registrarGlobal(
                            "usuario",
                            idUsuario.toString(),
                            "CREAR",
                            null,
                            perfilEnmascarado(
                                    datos.correo(),
                                    datos.nombre(),
                                    datos.telefono().valor()));
                    auditoria.registrar(
                            "empresa",
                            idEmpresa.toString(),
                            "CREAR",
                            null,
                            Map.of(
                                    "tipo",
                                    "PERSONAL",
                                    "propietarioId",
                                    idUsuario.toString(),
                                    "nombre",
                                    datos.nombre()));
                    auditoria.registrar(
                            "empresa_usuario",
                            idEmpresa + ":" + idUsuario,
                            "CREAR",
                            null,
                            Map.of("rol", Rol.ADMIN_EMPRESA.codigo(), "estado", "ACTIVA"));

                    // 4. Lo que se lee de vuelta es lo guardado (incluye las marcas de tiempo del consentimiento)
                    return usuarios.buscarPorId(idUsuario)
                            .orElseThrow(() -> new IllegalStateException("Usuario no creado"));
                }));
    }

    /** Perfil para la auditoría: correo y teléfono enmascarados (CLAUDE.md 1.1.12; columna documentada en V4). */
    private static Map<String, String> perfilEnmascarado(String correo, String nombre, String telefono) {
        Map<String, String> perfil = new LinkedHashMap<>();
        perfil.put("correo", EnmascaradorDatosPersonales.enmascarar(correo));
        perfil.put("nombre", nombre);
        perfil.put("telefono", enmascararTelefono(telefono));
        return perfil;
    }

    /** Oculta el teléfono salvo sus dos últimos dígitos (el enmascarador de logs solo cubre los que empiezan en 2, 6 o 7). */
    private static String enmascararTelefono(String telefono) {
        return "*".repeat(telefono.length() - 2) + telefono.substring(telefono.length() - 2);
    }
}
