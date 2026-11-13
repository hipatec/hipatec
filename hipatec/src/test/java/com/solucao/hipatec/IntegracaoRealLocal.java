package com.solucao.hipatec;

import com.solucao.hipatec.config.*;
import com.solucao.hipatec.controller.*;
import com.solucao.hipatec.service.*;
import com.google.firebase.*;
import com.google.auth.oauth2.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Exclusivo de testes: não carrega application.properties, credenciais ou recursos remotos. */
public class IntegracaoRealLocal {
    private static java.nio.file.Path pasta() {
        var raiz = java.nio.file.Path.of(java.util.Objects.requireNonNull(System.getenv("HIPATEC_TEST_SCRATCH"), "Defina HIPATEC_TEST_SCRATCH")).toAbsolutePath().normalize();
        if (!raiz.startsWith(java.nio.file.Path.of(System.getProperty("user.home"), ".hermes/cache/scratch")))
            throw new IllegalArgumentException("Scratch deve estar sob ~/.hermes/cache/scratch.");
        return raiz.resolve("uploads");
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration(exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
    @EntityScan("com.solucao.hipatec.model")
    @EnableJpaRepositories("com.solucao.hipatec.repository")
    @Import({SecurityConfig.class, SessaoController.class, EstudanteController.class, MentoraController.class,
        EstudanteService.class, MentoraService.class, PerfilController.class, PerfilService.class,
        SeguimentoController.class, EventoController.class, EventoService.class, PostController.class,
        MentoriaController.class, MentoriaService.class, CursoController.class, CursoService.class, DenunciaController.class, DenunciaService.class})
    static class Config {
        @Bean @org.springframework.core.annotation.Order(0)
        org.springframework.security.web.SecurityFilterChain media(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            return http.securityMatcher("/test-media/**").authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.GET,"/test-media/**").permitAll().anyRequest().denyAll()).build();
        }
        @Bean org.springframework.web.servlet.config.annotation.WebMvcConfigurer arquivos() {
            return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
                public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
                    registry.addResourceHandler("/test-media/**").addResourceLocations(pasta().toUri().toString()+"/");
                }
            };
        }
        @Bean BCryptPasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean PostService posts() { return new PostService(); }
        @Bean com.cloudinary.Cloudinary cloudinary() { return new com.cloudinary.Cloudinary(); }
        @Bean CloudinaryService cloudinaryService() {
            return new CloudinaryService() {
                @Override public String uploadFile(org.springframework.web.multipart.MultipartFile file) {
                    validarImagem(file);
                    try {
                        var pasta=pasta();
                        java.nio.file.Files.createDirectories(pasta);
                        var nome=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(file.getBytes()))+".png";
                        java.nio.file.Files.write(pasta.resolve(nome),file.getBytes());
                        return "http://127.0.0.1:18086/test-media/"+nome;
                    } catch(Exception e) { throw new IllegalStateException("Adapter local de upload falhou.",e); }
                }
            };
        }
    }
    public static void main(String[] args) {
        if (!"127.0.0.1:18900".equals(System.getenv("FIREBASE_DATABASE_EMULATOR_HOST")))
            throw new IllegalArgumentException("Exige RTDB local isolado18900.");
        FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.create(new AccessToken("owner", new java.util.Date(Long.MAX_VALUE))))
            .setDatabaseUrl("http://127.0.0.1:18900?ns=demo-hipatec-real")
            .setProjectId("demo-hipatec-real").build());
        new SpringApplication(Config.class).run(
            "--spring.config.location=optional:classpath:/isolated-real-test.properties",
            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:18432/hipatec_test",
            "--spring.datasource.username=postgres", "--spring.datasource.password=",
            "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.jpa.hibernate.ddl-auto=none",
            "--server.address=127.0.0.1", "--server.port=18086",
            "--server.servlet.session.cookie.secure=false", "--server.servlet.session.cookie.same-site=lax",
            "--hipatec.frontend-origin=http://localhost:14286,http://127.0.0.1:14286",
            "--hipatec.firebase.enabled=false", "--hipatec.posts.enabled=false");
    }
}
