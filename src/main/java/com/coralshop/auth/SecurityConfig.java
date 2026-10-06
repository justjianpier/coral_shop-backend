package com.coralshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coralshop.user.UserRepository;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                    UserRepository userRepository) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf",
                        "/api/products", "/api/products/*", "/api/categories", "/api/brands").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/admin/**", "/api/users/**", "/api/orders/**", "/api/stats/**",
                        "/api/products/**", "/api/categories/**", "/api/brands/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        );

        http.formLogin(login -> login
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(),
                            AuthenticatedUserResponse.from(authentication, userRepository));
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), Map.of("message", "Invalid email or password"));
                })
        );

        http.exceptionHandling(errors -> errors.authenticationEntryPoint(
                (request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())
        ));

        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(HttpStatus.NO_CONTENT.value()))
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
        );

        // CSRF permanece activo: los POST necesitan el token de GET /api/auth/csrf.
        return http.build();
    }
}
