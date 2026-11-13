package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.*;

/** Usa SOMENTE banco novo e exclusivo de teste; nunca abre o arquivo do preview. */
class EventosLocalTests {
    MockMvc mvc;
    MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
            .content("{\"role\":\"estudantes\",\"email\":\"aluna@example.test\",\"senha\":\"TesteLocal123!\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    void conectar(org.springframework.context.ConfigurableApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
    }
    @Test void feedComEstadoEEventosSobrevivemAoReabrirArquivo() throws Exception {
        Path banco = Files.createTempDirectory(Path.of("/home/rosa/.hermes/cache/scratch"), "feed-eventos-test-").resolve("eventos");
        String url = "jdbc:h2:file:" + banco + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";
        long eventoId;
        // Schema novo de teste, criado uma vez. Boots seguintes usam ddl-auto=none.
        try (var context = new SpringApplication(EventosTests.Config.class).run(
                "--spring.config.location=optional:classpath:/application-eventos-local.properties",
                "--spring.datasource.url=" + url, "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.jpa.hibernate.ddl-auto=create",
                "--server.address=127.0.0.1", "--server.port=0",
                "--hipatec.firebase.enabled=false", "--hipatec.posts.enabled=false")) {
            var conta = new Estudante(); conta.setNome("Aluna fixture"); conta.setEmail("aluna@example.test");
            conta.setSenha("{bcrypt}" + context.getBean(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class).encode("TesteLocal123!"));
            conta = context.getBean(EstudanteRepository.class).save(conta);
            var perfil = new Perfil(); perfil.setUsuario("aluna"); perfil.setId_estudante(conta.getId());
            context.getBean(PerfilRepository.class).save(perfil);
            conectar(context);
            String resposta = mvc.perform(post("/eventos").session(login()).with(csrf()).contentType("application/json")
                .content("{\"titulo\":\"Anterior ao boot combinado\",\"data\":\"2026-12-01\",\"visibilidade\":\"PRIVADO\",\"requisicaoId\":\"11111111-1111-4111-8111-111111111111\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            eventoId = new tools.jackson.databind.ObjectMapper().readTree(resposta).get("id").asLong();
        }
        for (int boot = 0; boot < 2; boot++) {
            try (var context = EventosLocalApplication.servir(banco, "0")) {
                conectar(context);
                var sessao = login();
                mvc.perform(get("/eventos/" + eventoId).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.titulo").value("Anterior ao boot combinado"));
                assertEquals(1, context.getBean(EventoRepository.class).count());
                assertEquals(1, context.getBean(EstudanteRepository.class).count());
                assertEquals(1, context.getBean(PerfilRepository.class).count());
                mvc.perform(get("/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value("fixture-social-local"))
                    .andExpect(jsonPath("$[0].conteudo").value(org.hamcrest.Matchers.containsString("Fixture local de teste")));
                mvc.perform(post("/posts").session(sessao).with(csrf()).contentType("application/json")
                    .content("{\"conteudo\":\"Post sintético criado no teste\",\"autorId\":99999}"))
                    .andExpect(status().isOk());
                String lista = mvc.perform(get("/posts")).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].conteudo").value("Post sintético criado no teste"))
                    .andReturn().getResponse().getContentAsString();
                var postCriado = new tools.jackson.databind.ObjectMapper().readTree(lista).get(0);
                String id = postCriado.get("id").asText();
                int autor = postCriado.get("autorId").asInt();
                assertNotEquals(99999, autor);
                mvc.perform(get("/posts/autor/" + autor)).andExpect(jsonPath("$.length()").value(2));
                for (int i = 0; i < 2; i++) mvc.perform(post("/posts/" + id + "/like").session(sessao).with(csrf())).andExpect(status().isOk());
                mvc.perform(post("/posts/" + id + "/comment").session(sessao).with(csrf()).contentType("application/json")
                    .content("{\"texto\":\"Comentário sintético\"}")).andExpect(status().isOk());
                var novaSessao = login();
                mvc.perform(get("/posts/" + id + "/interacoes").session(novaSessao))
                    .andExpect(jsonPath("$.likesCount").value(1)).andExpect(jsonPath("$.commentsCount").value(1))
                    .andExpect(jsonPath("$.curtidoUsuario").value(true));
                mvc.perform(get("/posts/" + id + "/comments").session(novaSessao))
                    .andExpect(jsonPath("$[0].texto").value("Comentário sintético"))
                    .andExpect(jsonPath("$[0].autorId").value(autor)).andExpect(jsonPath("$[0].id").isNotEmpty());
                mvc.perform(delete("/posts/" + id + "/like").session(novaSessao).with(csrf())).andExpect(status().isOk());
                mvc.perform(get("/posts/" + id + "/interacoes").session(novaSessao))
                    .andExpect(jsonPath("$.likesCount").value(0)).andExpect(jsonPath("$.curtidoUsuario").value(false));
                for (int i = 0; i < 2; i++) mvc.perform(post("/posts/" + id + "/repost").session(novaSessao).with(csrf())).andExpect(status().isOk());
                mvc.perform(get("/posts")).andExpect(jsonPath("$[0].repostsCount").value(1));
                mvc.perform(get("/eventos?de=2026-12-01&ate=2026-12-31").session(novaSessao))
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(eventoId));
            }
        }
    }
}
