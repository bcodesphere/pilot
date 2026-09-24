package com.bcodesphere.pilot.plataforma.infraestructura;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registra {@link TenantAwareTransactionManager} como el {@code transactionManager} de la aplicación. Al existir
 * un gestor definido, la autoconfiguración de Spring Boot no crea el suyo (ADR-002).
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionTransacciones {

    /**
     * Gestor de transacciones que fija el contexto de empresa en PostgreSQL.
     *
     * @param entityManagerFactory fábrica JPA de la aplicación
     * @return el gestor principal de transacciones
     */
    @Bean(name = "transactionManager")
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareTransactionManager(entityManagerFactory);
    }
}
