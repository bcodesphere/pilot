package com.bcodesphere.pilot.plataforma;

/**
 * Se lanza cuando se necesita la empresa activa y no hay ninguna en el contexto del hilo.
 * Es un error de programación o de configuración (nunca del cliente): el manejador global lo responde como PLT-500,
 * y así una consulta sin empresa falla de forma cerrada en vez de leer o escribir datos sin aislamiento (CLAUDE.md 4.5).
 */
public class ContextoEmpresaAusenteException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** Crea el error con un mensaje que indica cómo establecer el contexto. */
    public ContextoEmpresaAusenteException() {
        super(
                "No hay empresa activa en el contexto: use ContextoEmpresa.ejecutarCon(...) antes de abrir una transacción");
    }
}
