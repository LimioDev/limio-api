package com.limio.api.comum.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.limio.api.comum.seguranca.JwtAuthFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // públicas: quem chama ainda não tem token de acesso válido.
                        // Recuperação de senha (UC06) entra nesta lista quando for implementada.
                        .requestMatchers(HttpMethod.POST, "/auth/cadastro", "/auth/login", "/auth/refresh").permitAll()
                        // health check do Railway: sem isso todo deploy falha com 401
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtDecoder), UsernamePasswordAuthenticationFilter.class)
                // 401/403 saem pelo GlobalExceptionHandler como qualquer outro erro (ErroResponse + NAO_AUTENTICADO/ACESSO_NEGADO)
                .exceptionHandling(erro -> erro
                        .authenticationEntryPoint(
                                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler(
                                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex)));
        return http.build();
    }
}
