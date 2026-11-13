
package com.solucao.hipatec.config;

import java.io.IOException;
import java.util.Arrays;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
@EnableMethodSecurity
@Import(Acesso.class)
public class SecurityConfig {

        @Bean
        SecurityContextRepository securityContextRepository() {
                return new HttpSessionSecurityContextRepository();
        }

        @Bean
        CsrfTokenRepository csrfTokenRepository(
                        @Value("${server.servlet.session.cookie.secure:true}") boolean secure,
                        @Value("${server.servlet.session.cookie.same-site:None}") String sameSite) {
                var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

                repository.setCookieCustomizer(cookie -> cookie
                                .sameSite(secure ? "None" : sameSite)
                                .secure(secure)
                                .path("/"));

                return repository;
        }

        @Bean
        SecurityFilterChain filterChain(
                        HttpSecurity http,
                        Acesso acesso,
                        SecurityContextRepository repository,
                        CsrfTokenRepository csrfTokenRepository,
                        @Value("${hipatec.frontend-origin:http://localhost:8100}") String origin) throws Exception {

                var cors = new CorsConfiguration();

                cors.setAllowedOrigins(Arrays.stream(origin.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList());
                cors.setAllowedMethods(List.of(
                                "GET", "POST", "PUT", "DELETE", "OPTIONS"));
                cors.setAllowedHeaders(List.of(
                                "Content-Type",
                                "X-CSRF-TOKEN",
                                "X-XSRF-TOKEN"));
                cors.setAllowCredentials(true);

                var source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", cors);

                http
                                .cors(c -> c.configurationSource(source))

                                .securityContext(c -> c.securityContextRepository(repository))

                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(csrfTokenRepository)
                                                .csrfTokenRequestHandler(
                                                                new CsrfTokenRequestAttributeHandler()))

                                .authorizeHttpRequests(auth -> auth

                                                .dispatcherTypeMatchers(
                                                                jakarta.servlet.DispatcherType.ERROR)
                                                .permitAll()

                                                // Página inicial da API
                                                .requestMatchers(HttpMethod.GET, "/").permitAll()

                                                // Rotas públicas GET
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/auth/csrf",
                                                                "/posts",
                                                                "/posts/autor/*",
                                                                "/posts/*/interacoes",
                                                                "/perfil")
                                                .permitAll()

                                                // Rotas públicas POST
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/auth/login",
                                                                "/auth/recuperacao-senha",
                                                                "/auth/validar-redefinicao",
                                                                "/auth/redefinir-senha",
                                                                "/estudantes",
                                                                "/mentoras")
                                                .permitAll()

                                                // Rotas que exigem autenticação
                                                .requestMatchers(
                                                                "/auth/session",
                                                                "/perfil/estudantes/*",
                                                                "/perfil/mentoras/*",
                                                                "/perfil/*/seguindo",
                                                                "/perfil/*/seguir")
                                                .authenticated()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/mentorias", "/cursos")
                                                .authenticated()

                                                // Apenas mentoras podem criar mentorias
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/mentorias", "/cursos")
                                                .hasRole("MENTORA")

                                                // Eventos são restritos a sessões válidas, inclusive os públicos.
                                                .requestMatchers("/eventos", "/eventos/**").authenticated()

                                                // Demais operações de publicações exigem login
                                                .requestMatchers("/posts/**").authenticated()

                                                // Bloqueia qualquer rota não declarada
                                                .anyRequest().denyAll())

                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint((req, res, ex) -> res
                                                                .setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                                                .accessDeniedHandler((req, res, ex) -> res
                                                                .setStatus(HttpServletResponse.SC_FORBIDDEN)))

                                .logout(l -> l
                                                .logoutUrl("/auth/logout")
                                                .deleteCookies("JSESSIONID")
                                                .logoutSuccessHandler((req, res, auth) -> res
                                                                .setStatus(HttpServletResponse.SC_NO_CONTENT)))

                                // Validação da sessão do usuário
                                .addFilterAfter(new OncePerRequestFilter() {

                                        @Override
                                        protected void doFilterInternal(
                                                        HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        FilterChain chain) throws ServletException, IOException {

                                                var auth = SecurityContextHolder
                                                                .getContext()
                                                                .getAuthentication();

                                                if (auth != null
                                                                && auth.getPrincipal() instanceof UsuarioSessao usuario
                                                                && !acesso.valida(usuario)) {

                                                        new SecurityContextLogoutHandler()
                                                                        .logout(request, response, auth);

                                                        // Prossegue anônimo: CORS/CSRF e endpoints públicos continuam
                                                        // disponíveis; a autorização rejeita os recursos protegidos.

                                                }

                                                chain.doFilter(request, response);
                                        }

                                }, SecurityContextHolderFilter.class);

                return http.build();
        }
}
