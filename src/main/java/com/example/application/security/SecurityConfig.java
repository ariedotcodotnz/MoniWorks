package com.example.application.security;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfig {

    private final CompanyPermissionEvaluator permissionEvaluator;
    private final AuditLogoutHandler auditLogoutHandler;

    public SecurityConfig(CompanyPermissionEvaluator permissionEvaluator,
                          AuditLogoutHandler auditLogoutHandler) {
        this.permissionEvaluator = permissionEvaluator;
        this.auditLogoutHandler = auditLogoutHandler;
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Allow access to H2 console in development
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/h2-console/**").permitAll()
        );

        // Allow frames for H2 console
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        // Allow CSRF for H2 console
        http.csrf(csrf -> csrf
            .ignoringRequestMatchers("/h2-console/**")
        );

        // Configure logout with audit logging
        http.logout(logout -> logout
            .addLogoutHandler(auditLogoutHandler)
            .logoutSuccessUrl("/login")
        );

        // Configure Vaadin security
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.loginView("/login");
        });

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
