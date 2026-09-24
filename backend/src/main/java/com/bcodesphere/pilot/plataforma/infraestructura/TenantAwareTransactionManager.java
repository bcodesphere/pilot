package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.MDC;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Gestor de transacciones que fija la empresa y el usuario activos en la sesión de PostgreSQL al iniciar cada
 * transacción, para que Row-Level Security filtre los datos (CLAUDE.md 4.5, ADR-002).
 * También publica {@code empresaId} y {@code usuarioId} en el MDC mientras dura la transacción (CLAUDE.md 1.1.12).
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private static final long serialVersionUID = 1L;

    /**
     * Crea el gestor sobre la fábrica de EntityManager de la aplicación.
     *
     * @param entityManagerFactory fábrica JPA
     */
    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    /**
     * Abre la transacción y luego establece {@code app.empresa_id} y {@code app.usuario_id} solo para ella.
     * Sin empresa en el contexto lanza {@code ContextoEmpresaAusenteException} antes de abrir nada.
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 1. Empresa y usuario del contexto; se leen ANTES de abrir la transacción para que, si falta la empresa,
        //    no quede ninguna conexión ni EntityManager abiertos
        EmpresaId empresaId = ContextoEmpresa.empresaRequerida();
        String usuarioId = ContextoEmpresa.usuarioOSistema();

        // 2. Abre la transacción con el comportamiento estándar de Spring
        super.doBegin(transaction, definition);

        try {
            // 3. Recupera el EntityManager ligado a la transacción recién abierta
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
            if (em == null) {
                throw new IllegalStateException("La transacción abierta no tiene EntityManager asociado");
            }

            // 4. set_config(..., true) limita los valores a esta transacción (equivale a SET LOCAL)
            em.createNativeQuery("SELECT set_config('app.empresa_id', :e, true),"
                            + " set_config('app.usuario_id', :u, true)")
                    .setParameter("e", empresaId.toString())
                    .setParameter("u", usuarioId)
                    .getSingleResult();
        } catch (RuntimeException e) {
            // 5. Si no se pudo fijar el contexto, la transacción no debe quedar abierta ni con la conexión tomada
            abortarInicio(transaction);
            throw e;
        }

        // 6. Los logs de la transacción llevan empresa y usuario
        MDC.put(ClavesMdc.EMPRESA_ID, empresaId.toString());
        MDC.put(ClavesMdc.USUARIO_ID, usuarioId);
    }

    /** Revierte y libera una transacción que se abrió pero no pudo fijar su contexto. */
    private void abortarInicio(Object transaction) {
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
        if (em != null && em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        doCleanupAfterCompletion(transaction);
    }

    /** Al terminar la transacción se limpian empresa y usuario del MDC: los hilos del servidor se reutilizan. */
    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            super.doCleanupAfterCompletion(transaction);
        } finally {
            MDC.remove(ClavesMdc.EMPRESA_ID);
            MDC.remove(ClavesMdc.USUARIO_ID);
        }
    }

    /**
     * Al reanudar una transacción externa suspendida (p. ej. tras un REQUIRES_NEW) se republican empresa y usuario,
     * porque la limpieza de la transacción interna los borró del MDC.
     */
    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        super.doResume(transaction, suspendedResources);
        if (ContextoEmpresa.hayEmpresa()) {
            MDC.put(ClavesMdc.EMPRESA_ID, ContextoEmpresa.empresaRequerida().toString());
            MDC.put(ClavesMdc.USUARIO_ID, ContextoEmpresa.usuarioOSistema());
        }
    }
}
