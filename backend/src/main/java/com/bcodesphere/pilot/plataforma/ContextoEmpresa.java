package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.compartido.EmpresaId;
import java.util.function.Supplier;

/**
 * Contexto de la empresa y el usuario activos, almacenado por hilo (CLAUDE.md 4.5).
 * El gestor de transacciones lo lee al iniciar cada transacción para fijar {@code app.empresa_id} y
 * {@code app.usuario_id} en PostgreSQL, y así Row-Level Security filtra los datos.
 *
 * <p>En F0 nadie lo llena desde la petición: la empresa de un header {@code X-Empresa-Id} sin validar contra las
 * membresías sería una fuga entre empresas. F1 lo establecerá después de validar la membresía.
 */
public final class ContextoEmpresa {

    /** Usuario que se registra cuando la operación no la inicia una persona (tareas internas, pruebas). */
    public static final String USUARIO_SISTEMA = "sistema";

    /** Valores del hilo actual; se limpia siempre en {@code finally} porque los hilos del servidor se reutilizan. */
    private static final ThreadLocal<Valores> ACTUAL = new ThreadLocal<>();

    private ContextoEmpresa() {}

    /** Empresa y usuario del contexto; el usuario nulo se traduce a {@link #USUARIO_SISTEMA} al leerlo. */
    private record Valores(EmpresaId empresaId, String usuarioId) {}

    /**
     * Empresa activa del hilo.
     *
     * @return la empresa activa
     * @throws ContextoEmpresaAusenteException si no hay contexto establecido
     */
    public static EmpresaId empresaRequerida() {
        // 1. Sin empresa no se puede continuar: fallar aquí evita consultas sin aislamiento
        Valores valores = ACTUAL.get();
        if (valores == null) {
            throw new ContextoEmpresaAusenteException();
        }
        return valores.empresaId();
    }

    /**
     * Usuario activo del hilo, o {@link #USUARIO_SISTEMA} si no hay usuario o no hay contexto.
     *
     * @return identificador de usuario para auditoría y para {@code app.usuario_id}
     */
    public static String usuarioOSistema() {
        Valores valores = ACTUAL.get();
        return valores == null || valores.usuarioId() == null ? USUARIO_SISTEMA : valores.usuarioId();
    }

    /**
     * Indica si hay una empresa establecida en el hilo actual.
     *
     * @return {@code true} si {@link #empresaRequerida()} no lanzaría error
     */
    public static boolean hayEmpresa() {
        return ACTUAL.get() != null;
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
        // 2. Guarda el contexto previo para restaurarlo (permite anidar, p. ej. pruebas o tareas internas)
        Valores anterior = ACTUAL.get();
        ACTUAL.set(new Valores(empresaId, usuarioId));
        try {
            return accion.get();
        } finally {
            // 3. Siempre se restaura o se limpia; nunca queda una empresa colgada en un hilo reutilizado
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
