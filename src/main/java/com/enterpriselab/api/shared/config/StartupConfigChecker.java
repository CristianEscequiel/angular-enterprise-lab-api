package com.enterpriselab.api.shared.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Spec 06, REQ-6 y REQ-7: valida {@code JWT_SECRET} y {@code DB_PASSWORD} sobre los valores
 * ya resueltos del entorno, antes de crear ningún bean. No se apoya en el placeholder del
 * {@code application.yml}: Spring deja un {@code ${JWT_SECRET}} sin resolver como texto literal
 * al enlazar {@code @ConfigurationProperties} y la aplicación arrancaría firmando con esa clave.
 * Tampoco pasa por el binder, que podría volcar el valor del secreto en su mensaje. Los mensajes
 * nombran la variable y nunca incluyen su valor.
 */
public class StartupConfigChecker implements EnvironmentPostProcessor, Ordered {

    static final int JWT_SECRET_MIN_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        check(environment);
    }

    /** Corre después de la carga de {@code application.yml} y de los perfiles. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void check(ConfigurableEnvironment environment) {
        String secret = environment.getProperty("app.jwt.secret");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET no está definida: es obligatoria fuera del perfil dev "
                    + "(mínimo " + JWT_SECRET_MIN_BYTES + " bytes)");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < JWT_SECRET_MIN_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + JWT_SECRET_MIN_BYTES
                    + " bytes (UTF-8); tiene " + bytes);
        }

        String password = environment.getProperty("spring.datasource.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD no está definida: es obligatoria fuera del perfil dev");
        }
    }
}
