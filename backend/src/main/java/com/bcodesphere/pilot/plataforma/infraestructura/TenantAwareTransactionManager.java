package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.MDC;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Gestor de transacciones que fija la empresa y el usuario activos en la sesión de PostgreSQL al iniciar cada
 * transacción, para que Row-Level Security filtre los datos (CLAUDE.md 4.5, ADR-002).
 * También publica {@code empresaId} y {@code usuarioId} en el MDC mientras dura la transacción (CLAUDE.md 1.1.12).
 *
 * <p>Distingue tres estados del {@link ContextoEmpresa}: con empresa (fija ambos parámetros), en modo explícito «sin
 * empresa» (fija solo {@code app.usuario_id}, ADR-026) y sin contexto (falla con
 * {@code ContextoEmpresaAusenteException}, como en F0).
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private static final long serialVersionUID = 1L;

    /** Posición de {@code empresaId} en las copias del MDC guardadas al abrir cada transacción. */
    private static final int MDC_EMPRESA = 0;

    /** Posición de {@code usuarioId} en las copias del MDC guardadas al abrir cada transacción. */
    private static final int MDC_USUARIO = 1;

    /**
     * Valores del MDC previos a cada transacción abierta en el hilo (pila: las transacciones se anidan con
     * REQUIRES_NEW). Al terminar cada una se restauran en vez de borrarse, para no perder el {@code usuarioId} que el
     * filtro de empresa activa publicó para toda la petición. Estático porque el gestor es serializable.
     */
    private static final ThreadLocal<Deque<String[]>> MDC_PREVIO = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Crea el gestor sobre la fábrica de EntityManager de la aplicación.
     *
     * @param entityManagerFactory fábrica JPA
     */
    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    /**
     * Abre la transacción y luego establece {@code app.empresa_id} y {@code app.usuario_id} solo para ella; en modo
     * «sin empresa» establece únicamente {@code app.usuario_id}. Sin contexto alguno lanza
     * {@code ContextoEmpresaAusenteException} antes de abrir nada.
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 1. Estado del contexto, leído ANTES de abrir la transacción para que, si no hay contexto, no quede ninguna
        //    conexión ni EntityManager abiertos. Sin empresa y fuera del modo explícito: falla (ADR-026 punto 1)
        boolean sinEmpresa = ContextoEmpresa.enModoSinEmpresa();
        EmpresaId empresaId = sinEmpresa ? null : ContextoEmpresa.empresaRequerida();
        String usuarioId = ContextoEmpresa.usuarioOSistema();

        // 2. Abre la transacción con el comportamiento estándar de Spring
        super.doBegin(transaction, definition);

        // 3. Guarda el MDC previo para restaurarlo al terminar (el filtro pudo publicar ya el usuario de la petición)
        MDC_PREVIO.get().push(new String[] {MDC.get(ClavesMdc.EMPRESA_ID), MDC.get(ClavesMdc.USUARIO_ID)});

        try {
            // 4. Recupera el EntityManager ligado a la transacción recién abierta
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
            if (em == null) {
                throw new IllegalStateException("La transacción abierta no tiene EntityManager asociado");
            }

            // 5. set_config(..., true) limita los valores a esta transacción (equivale a SET LOCAL). En modo sin
            // empresa
            //    NO se fija app.empresa_id: así cualquier tabla con RLS falla en vez de mostrar filas
            if (sinEmpresa) {
                em.createNativeQuery("SELECT set_config('app.usuario_id', :u, true)")
                        .setParameter("u", usuarioId)
                        .getSingleResult();
            } else {
                em.createNativeQuery("SELECT set_config('app.empresa_id', :e, true),"
                                + " set_config('app.usuario_id', :u, true)")
                        .setParameter("e", empresaId.toString())
                        .setParameter("u", usuarioId)
                        .getSingleResult();
            }
        } catch (RuntimeException e) {
            // 6. Si no se pudo fijar el contexto, la transacción no debe quedar abierta ni con la conexión tomada
            abortarInicio(transaction);
            throw e;
        }

        // 7. Los logs de la transacción llevan empresa (si hay) y usuario
        publicarMdc();
    }

    /** Publica en el MDC lo que corresponde al estado actual: empresa y usuario, solo usuario, o nada. */
    private static void publicarMdc() {
        if (ContextoEmpresa.hayEmpresa()) {
            MDC.put(ClavesMdc.EMPRESA_ID, ContextoEmpresa.empresaRequerida().toString());
            MDC.put(ClavesMdc.USUARIO_ID, ContextoEmpresa.usuarioOSistema());
        } else if (ContextoEmpresa.enModoSinEmpresa()) {
            MDC.remove(ClavesMdc.EMPRESA_ID);
            MDC.put(ClavesMdc.USUARIO_ID, ContextoEmpresa.usuarioOSistema());
        }
    }

    /** Revierte y libera una transacción que se abrió pero no pudo fijar su contexto. */
    private void abortarInicio(Object transaction) {
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
        if (em != null && em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        doCleanupAfterCompletion(transaction);
    }

    /**
     * Al terminar la transacción se restaura el MDC previo (los hilos del servidor se reutilizan, así que nada queda
     * de una petición en otra; y el usuario de la petición sigue en los logs posteriores a la transacción).
     */
    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            super.doCleanupAfterCompletion(transaction);
        } finally {
            Deque<String[]> pila = MDC_PREVIO.get();
            String[] previo = pila.poll();
            restaurar(ClavesMdc.EMPRESA_ID, previo == null ? null : previo[MDC_EMPRESA]);
            restaurar(ClavesMdc.USUARIO_ID, previo == null ? null : previo[MDC_USUARIO]);
            // Sin transacciones abiertas se libera el ThreadLocal para no retener nada en hilos reutilizados
            if (pila.isEmpty()) {
                MDC_PREVIO.remove();
            }
        }
    }

    /** Devuelve una clave del MDC a su valor previo; nulo significa que no existía. */
    private static void restaurar(String clave, String valor) {
        if (valor == null) {
            MDC.remove(clave);
        } else {
            MDC.put(clave, valor);
        }
    }

    /**
     * Al reanudar una transacción externa suspendida (p. ej. tras un REQUIRES_NEW) se republica el MDC según el
     * estado actual: con empresa (empresa y usuario), en modo sin empresa (solo usuario) o sin contexto (nada).
     */
    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        super.doResume(transaction, suspendedResources);
        if (ContextoEmpresa.hayEmpresa() || ContextoEmpresa.enModoSinEmpresa()) {
            publicarMdc();
        } else {
            // Sin contexto no hay empresa que publicar: se retira lo que pudiera haber dejado la transacción interna
            MDC.remove(ClavesMdc.EMPRESA_ID);
        }
    }
}
