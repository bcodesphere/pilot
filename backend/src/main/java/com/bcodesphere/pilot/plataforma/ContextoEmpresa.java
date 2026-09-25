package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.compartido.EmpresaId;
import java.util.function.Supplier;

/**
 * Contexto de la empresa y el usuario activos, almacenado por hilo (CLAUDE.md 4.5).
 * El gestor de transacciones lo lee al iniciar cada transacción para fijar {@code app.empresa_id} y
 * {@code app.usuario_id} en PostgreSQL, y así Row-Level Security filtra los datos.
 *
 * <p>El hilo puede estar en uno de tres estados: sin contexto, en modo explícito «sin empresa» (ADR-026, solo para
 * las búsquedas previas a conocer la empresa) o con empresa. La empresa de un header {@code X-Empresa-Id} solo se
 * establece después de validarla contra las membresías (filtro de empresa activa, F1-04); sin validar sería una
 * fuga entre empresas.
 */
public final class ContextoEmpresa {

    /** Usuario que se registra cuando la operación no la inicia una persona (tareas internas, pruebas). */
    public static final String USUARIO_SISTEMA = "sistema";

    /** Estado del hilo actual; se limpia siempre en {@code finally} porque los hilos del servidor se reutilizan. */
    private static final ThreadLocal<Estado> ACTUAL = new ThreadLocal<>();

    private ContextoEmpresa() {}

    /**
     * Estado del contexto. Es sellado para que «sin empresa» sea un caso propio y no una empresa nula: una empresa
     * nula dejaría a quien lee el contexto suponiendo que «hay valores» equivale a «hay empresa».
     */
    private sealed interface Estado {

        /** Usuario asociado; el nulo se traduce a {@link #USUARIO_SISTEMA} al leerlo. */
        String usuarioId();
    }

    /** Contexto con empresa validada: es el único que permite abrir transacciones con RLS. */
    private record ConEmpresa(EmpresaId empresaId, String usuarioId) implements Estado {}

    /** Modo explícito «sin empresa» (ADR-026): solo tablas globales y funciones de búsqueda. */
    private record SinEmpresa(String usuarioId) implements Estado {}

    /**
     * Empresa activa del hilo.
     *
     * @return la empresa activa
     * @throws ContextoEmpresaAusenteException si no hay contexto o el hilo está en modo sin empresa
     */
    public static EmpresaId empresaRequerida() {
        // 1. Sin empresa no se puede continuar: fallar aquí evita consultas sin aislamiento. El modo sin empresa
        //    tampoco tiene empresa: ADR-026 solo cambia lo que hace el gestor de transacciones, no esta regla
        if (ACTUAL.get() instanceof ConEmpresa conEmpresa) {
            return conEmpresa.empresaId();
        }
        throw new ContextoEmpresaAusenteException();
    }

    /**
     * Usuario activo del hilo, o {@link #USUARIO_SISTEMA} si no hay usuario o no hay contexto.
     *
     * @return identificador de usuario para auditoría y para {@code app.usuario_id}
     */
    public static String usuarioOSistema() {
        Estado estado = ACTUAL.get();
        return estado == null || estado.usuarioId() == null ? USUARIO_SISTEMA : estado.usuarioId();
    }

    /**
     * Indica si hay una empresa establecida en el hilo actual.
     *
     * @return {@code true} si {@link #empresaRequerida()} no lanzaría error
     */
    public static boolean hayEmpresa() {
        return ACTUAL.get() instanceof ConEmpresa;
    }

    /**
     * Indica si el hilo está en el modo explícito «sin empresa» de {@link #ejecutarSinEmpresa} (ADR-026).
     * El gestor de transacciones lo usa para abrir la transacción sin fijar {@code app.empresa_id}.
     *
     * @return {@code true} solo dentro de {@code ejecutarSinEmpresa}
     */
    public static boolean enModoSinEmpresa() {
        return ACTUAL.get() instanceof SinEmpresa;
    }

    /**
     * Ejecuta una acción con la empresa y el usuario indicados y restaura el contexto anterior al terminar,
     * incluso si la acción lanza una excepción.
     *
     * @param empresaId empresa activa; no puede ser nula
     * @param usuarioId usuario activo; nulo equivale a {@link #USUARIO_SISTEMA}
     * @param accion trabajo a ejecutar dentro del contexto
     * @param <T> tipo del resultado
     * @return lo que devuelva la acción
     */
    public static <T> T ejecutarCon(EmpresaId empresaId, String usuarioId, Supplier<T> accion) {
        // 1. Una empresa nula dejaría el contexto "a medias": se rechaza antes de tocar el hilo
        if (empresaId == null) {
            throw new IllegalArgumentException("La empresa del contexto no puede ser nula");
        }
        return ejecutarEn(new ConEmpresa(empresaId, usuarioId), accion);
    }

    /**
     * Ejecuta una acción en el modo explícito «sin empresa» (ADR-026) y restaura el contexto anterior al terminar.
     * En este modo el gestor de transacciones abre la transacción fijando solo {@code app.usuario_id}; cualquier
     * consulta a una tabla con RLS falla porque falta {@code app.empresa_id}. Solo lo pueden llamar las clases de la
     * lista de {@code ArquitecturaModulosTest}: resolución de identidad, validación de membresía y consulta de
     * membresías de {@code /me}.
     *
     * @param usuarioId usuario activo; nulo equivale a {@link #USUARIO_SISTEMA} (aún no se conoce al usuario)
     * @param accion trabajo a ejecutar sin empresa
     * @param <T> tipo del resultado
     * @return lo que devuelva la acción
     */
    public static <T> T ejecutarSinEmpresa(String usuarioId, Supplier<T> accion) {
        return ejecutarEn(new SinEmpresa(usuarioId), accion);
    }

    /**
     * Variante sin resultado de {@link #ejecutarSinEmpresa(String, Supplier)}.
     *
     * @param usuarioId usuario activo; nulo equivale a {@link #USUARIO_SISTEMA}
     * @param accion trabajo a ejecutar sin empresa
     */
    public static void ejecutarSinEmpresa(String usuarioId, Runnable accion) {
        ejecutarSinEmpresa(usuarioId, () -> {
            accion.run();
            return null;
        });
    }

    /** Fija el estado, ejecuta la acción y restaura el estado anterior aunque la acción falle. */
    private static <T> T ejecutarEn(Estado estado, Supplier<T> accion) {
        // 1. Guarda el contexto previo para restaurarlo (permite anidar, p. ej. pruebas o tareas internas)
        Estado anterior = ACTUAL.get();
        ACTUAL.set(estado);
        try {
            return accion.get();
        } finally {
            // 2. Siempre se restaura o se limpia; nunca queda una empresa colgada en un hilo reutilizado
            if (anterior == null) {
                ACTUAL.remove();
            } else {
                ACTUAL.set(anterior);
            }
        }
    }

    /**
     * Variante sin resultado de {@link #ejecutarCon(EmpresaId, String, Supplier)}.
     *
     * @param empresaId empresa activa; no puede ser nula
     * @param usuarioId usuario activo; nulo equivale a {@link #USUARIO_SISTEMA}
     * @param accion trabajo a ejecutar dentro del contexto
     */
    public static void ejecutarCon(EmpresaId empresaId, String usuarioId, Runnable accion) {
        ejecutarCon(empresaId, usuarioId, () -> {
            accion.run();
            return null;
        });
    }
}
