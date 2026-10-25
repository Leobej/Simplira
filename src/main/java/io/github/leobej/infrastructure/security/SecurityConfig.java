package io.github.leobej.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Listed one by one rather than /auth/**, so endpoints added later are
                        // private until someone opts them in. refresh and logout are public
                        // because the refresh cookie is their credential - by the time they are
                        // called the access token has usually expired.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                // §6: the emailed token is verify-email's credential, and resend
                                // gives the same answer for every address, so neither needs a session.
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                // §6: the same reasoning, for the reset link.
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                // The emailed link again: it may be opened on another device.
                                "/api/v1/auth/confirm-email-change").permitAll()
                        // The container HEALTHCHECK curls this with no credentials.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // The WebSocket handshake: a browser cannot attach a bearer token to it, so
                        // the token comes on the STOMP CONNECT frame (StompAuthenticationInterceptor).
                        .requestMatchers(HttpMethod.GET, "/ws").permitAll()
                        // Avatars: what <img src> loads, which cannot carry a bearer token either.
                        .requestMatchers(HttpMethod.GET, "/api/v1/avatars/*/*").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
