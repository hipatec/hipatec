package com.solucao.hipatec.config;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
@Import(Acesso.class)
public class SecurityConfig {
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, Acesso acesso, SecurityContextRepository repository,
            @Value("${hipatec.frontend-origin:http://localhost:8100}") String origin) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        http.cors(c -> c.configurationSource(source))
            .securityContext(c -> c.securityContextRepository(repository))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/csrf", "/posts", "/posts/autor/*", "/perfil").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/recuperacao-senha", "/auth/redefinir-senha", "/estudantes", "/mentoras").permitAll()
                .requestMatchers("/auth/session", "/perfil/estudantes/*", "/perfil/mentoras/*").authenticated()
                .requestMatchers(HttpMethod.GET, "/mentorias").authenticated()
                .requestMatchers(HttpMethod.POST, "/mentorias").hasRole("MENTORA")
                .requestMatchers("/posts/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
                .accessDeniedHandler((req, res, ex) -> res.setStatus(403)))
            .logout(l -> l.logoutUrl("/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .addFilterAfter(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getPrincipal() instanceof UsuarioSessao usuario && !acesso.valida(usuario)) {
                        new SecurityContextLogoutHandler().logout(request, response, auth);
                        response.setStatus(401);
                        return;
                    }
                    chain.doFilter(request, response);
                }
            }, SecurityContextHolderFilter.class);
        return http.build();
    }
}
