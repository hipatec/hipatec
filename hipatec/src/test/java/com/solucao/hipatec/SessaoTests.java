package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.solucao.hipatec.config.*;
import com.solucao.hipatec.controller.*;
import com.solucao.hipatec.service.*;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SessaoTests.Config.class, properties = {
    "spring.config.location=optional:classpath:/application-sessao-test.properties",
    "spring.datasource.url=jdbc:h2:mem:sessao;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "hipatec.firebase.enabled=false", "hipatec.posts.enabled=false",
    "hipatec.recuperacao-senha.url=http://localhost:8100/redefinir-senha",
    "hipatec.mail.from=teste@hipatec.local"
})
class SessaoTests {
  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan("com.solucao.hipatec.model")
  // @EnableJpaRepositories("com.solucao.hipatec.repository")
  @Import({ SecurityConfig.class, CloudinaryConfig.class, SessaoController.class, EstudanteController.class, MentoraController.class,
      EstudanteService.class, MentoraService.class, PerfilController.class, PerfilService.class,
      PostController.class, MentoriaController.class, MentoriaService.class, HomeController.class,
      SeguimentoController.class })
  static class Config {
    @Bean
    BCryptPasswordEncoder encoder() {
      return new BCryptPasswordEncoder(4);
    }

    // @Bean
    // com.cloudinary.Cloudinary cloudinaryClient() {
    // return mock(com.cloudinary.Cloudinary.class);
    // }

    @Bean
    CloudinaryService cloudinaryService() {
      return mock(CloudinaryService.class);
    }

    @Bean
    PostService posts() {
      return mock(PostService.class);
    }

    @Bean
    JavaMailSender mail() {
      return mock(JavaMailSender.class);
    }
  }

  @org.springframework.boot.test.web.server.LocalServerPort
  int port;
  @Autowired
  WebApplicationContext context;
  @Autowired
  EstudanteRepository estudantes;
  @Autowired
  MentoraRepository mentoras;
  @Autowired
  PerfilRepository perfis;
  @Autowired
  SeguimentoRepository seguimentos;
  @Autowired
  BCryptPasswordEncoder encoder;
  @Autowired
  PostService posts;
  MockMvc mvc;
  Estudante estudante;
  static int ip;
  String endereco;

  @BeforeEach
  void preparar() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    seguimentos.deleteAll();
    perfis.deleteAll();
    estudantes.deleteAll();
    mentoras.deleteAll();
    reset(posts);
    estudante = new Estudante();
    estudante.setEmail("aluna@example.test");
    estudante.setNome("Aluna");
    estudante.setSenha("{bcrypt}" + encoder.encode("TesteLocal123!"));
    estudante = estudantes.save(estudante);
    var perfil = new Perfil();
    perfil.setId_estudante(estudante.getId());
    perfil.setUsuario("aluna");
    perfis.save(perfil);
    endereco = "192.0.2." + (++ip);
  }

  MockHttpSession entrar(String role, String email) throws Exception {
    return (MockHttpSession) mvc.perform(post("/auth/login").with(csrf()).with(r -> {
      r.setRemoteAddr(endereco);
      return r;
    })
        .contentType("application/json")
        .content("{\"role\":\"" + role + "\",\"email\":\"" + email + "\",\"senha\":\"TesteLocal123!\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.senhaHash").doesNotExist()).andReturn().getRequest()
        .getSession(false);
  }

  @Test
  void visitanteNaoPodeListarDadosDeContas() throws Exception {
    mvc.perform(get("/estudantes")).andExpect(status().isUnauthorized());
    mvc.perform(get("/perfil/estudantes/" + estudante.getId())).andExpect(status().isUnauthorized());
    mvc.perform(get("/posts")).andExpect(status().isOk());
  }

  @Test
  void sessaoPersisteLogoutRevogaEExigeCsrf() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    mvc.perform(get("/auth/session").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(estudante.getId()));
    mvc.perform(post("/auth/logout").session(session)).andExpect(status().isForbidden());
    mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
    assertTrue(session.isInvalid());
    mvc.perform(get("/auth/session")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejeitaCredenciaisInvalidasELoginAntigoPorUrl() throws Exception {
    mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
        .content("{\"role\":\"estudantes\",\"email\":\"aluna@example.test\",\"senha\":\"errada\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
        post("/estudantes/login").with(csrf()).param("email", estudante.getEmail()).param("senha", "TesteLocal123!"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void estudanteNaoPodeEditarOutraContaNemCriarMentoria() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    mvc.perform(put("/perfil/estudantes/9999").session(session).with(csrf())
        .param("nome", "Intrusa").param("usuario", "intrusa").param("biografia", "")).andExpect(status().isForbidden());
    mvc.perform(put("/perfil/mentoras/" + estudante.getId()).session(session).with(csrf())
        .param("nome", "Intrusa").param("usuario", "intrusa").param("biografia", "")).andExpect(status().isForbidden());
    mvc.perform(post("/mentorias").session(session).with(csrf()).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/perfil/estudantes/" + estudante.getId()).session(session).with(csrf())
        .param("nome", "Nome novo").param("usuario", "aluna").param("biografia", "Bio")).andExpect(status().isOk());
    assertEquals("Nome novo", estudantes.findById(estudante.getId()).orElseThrow().getNome());
  }

  @Test
  void mentoraPendenteNaoEntraEAprovacaoRevogadaEncerraSessao() throws Exception {
    var mentora = new Mentora();
    mentora.setEmail("mentora@example.test");
    mentora.setSenha(estudante.getSenha());
    mentora = mentoras.save(mentora);
    mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
        .content("{\"role\":\"mentoras\",\"email\":\"mentora@example.test\",\"senha\":\"TesteLocal123!\"}"))
        .andExpect(status().isForbidden());
    mentora.setAprovada(true);
    mentoras.save(mentora);
    var session = entrar("mentoras", mentora.getEmail());
    mvc.perform(get("/auth/session").session(session)).andExpect(status().isOk());
    mentora.setAprovada(false);
    mentoras.save(mentora);
    mvc.perform(get("/auth/session").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void trocarSenhaRevogaSessaoAnterior() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    estudante.setSenha("{bcrypt}" + encoder.encode("OutraSenha123!"));
    estudantes.save(estudante);
    mvc.perform(get("/auth/session").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void cadastroGeraHashEIgnoraTentativaDeAutoAprovacao() throws Exception {
    for (String role : new String[] { "estudantes", "mentoras" }) {
      mvc.perform(post("/" + role).with(csrf()).contentType("application/json").content(
          """
              {"nome":"Nova","email":"nova@example.test","usuario":"nova","senha":"TesteLocal123!","dataNascimento":"2000-01-01","aprovada":true}
              """))
          .andExpect(status().isOk()).andExpect(jsonPath("$.senha").doesNotExist());
    }
    assertTrue(encoder.matches("TesteLocal123!",
        estudantes.findByEmailIgnoreCase("nova@example.test").get(0).getSenha().substring(8)));
    assertFalse(mentoras.findByEmailIgnoreCase("nova@example.test").get(0).getAprovada());
  }

  @Test
  void autoriaDePostVemDaSessao() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    mvc.perform(post("/posts").session(session).with(csrf()).contentType("application/json")
        .content("{\"autorId\":9999,\"conteudo\":\"Teste\",\"autorFoto\":\"falsa\"}")).andExpect(status().isOk());
    verify(posts).criar(argThat(
        p -> p.getAutorId().equals(perfis.buscarPorEstudante(estudante.getId()).getId()) && p.getAutorFoto() == null));
  }

  String perfilDeOutraEstudante() {
    var outra = new Estudante();
    outra.setEmail("outra@example.test");
    outra.setNome("Outra");
    outra.setSenha(estudante.getSenha());
    outra = estudantes.save(outra);
    var perfilOutra = new Perfil();
    perfilOutra.setId_estudante(outra.getId());
    perfilOutra.setUsuario("outra");
    return "/perfil/" + perfis.save(perfilOutra).getId();
  }

  @Test
  void seguirUsaSessaoCsrfEStatusRelidoDoServidor() throws Exception {
    var alvo = perfilDeOutraEstudante();

    mvc.perform(get(alvo + "/seguindo")).andExpect(status().isUnauthorized());
    var session = entrar("estudantes", estudante.getEmail());
    mvc.perform(get(alvo + "/seguindo").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.seguindo").value(false));
    mvc.perform(post(alvo + "/seguir").session(session)).andExpect(status().isForbidden());

    mvc.perform(post(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk())
        .andExpect(jsonPath("$.seguindo").value(true));
    mvc.perform(get(alvo + "/seguindo").session(session)).andExpect(jsonPath("$.seguindo").value(true));
    mvc.perform(post(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk());
    assertEquals(1, seguimentos.count());

    var proprio = perfis.buscarPorEstudante(estudante.getId()).getId();
    mvc.perform(post("/perfil/" + proprio + "/seguir").session(session).with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/perfil/999999/seguir").session(session).with(csrf())).andExpect(status().isNotFound());
  }

  @Test
  void deixarDeSeguirRemoveEStatusRelidoVoltaFalso() throws Exception {
    var alvo = perfilDeOutraEstudante();
    mvc.perform(delete(alvo + "/seguir").with(csrf())).andExpect(status().isUnauthorized());
    var session = entrar("estudantes", estudante.getEmail());
    mvc.perform(post(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk());
    mvc.perform(delete(alvo + "/seguir").session(session)).andExpect(status().isForbidden());
    assertEquals(1, seguimentos.count());

    mvc.perform(delete(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk())
        .andExpect(jsonPath("$.seguindo").value(false));
    mvc.perform(get(alvo + "/seguindo").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.seguindo").value(false));
    assertEquals(0, seguimentos.count());

    // Idempotente: desfazer de novo continua 200, false e sem registros.
    mvc.perform(delete(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk())
        .andExpect(jsonPath("$.seguindo").value(false));
    assertEquals(0, seguimentos.count());
  }

  @Test
  void corsPermiteSomenteOrigemDaAplicacao() throws Exception {
    mvc.perform(options("/auth/login").header("Origin", "https://intrusa.example")
        .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
    mvc.perform(options("/auth/login").header("Origin", "http://localhost:8100").header("Access-Control-Request-Method",
        "POST"))
        .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  void httpRealPreservaRespostaDeMentoraPendente() throws Exception {
    var mentora = new Mentora();
    mentora.setEmail("pendente@example.test");
    mentora.setSenha(estudante.getSenha());
    mentoras.save(mentora);
    var client = java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
    var base = "http://localhost:" + port;
    var csrfResponse = client.send(
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/auth/csrf")).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
    var token = new tools.jackson.databind.ObjectMapper().readTree(csrfResponse.body()).get("token").asText();
    var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/auth/login"))
        .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
            {"role":"mentoras","email":"pendente@example.test","senha":"TesteLocal123!"}
            """)).build();
    assertEquals(403, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  @Test
  void validaEntradaSemExporCredenciaisELimitaTentativas() throws Exception {
    mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
        .content("{\"role\":\"admin\",\"email\":\"invalido\",\"senha\":\"segredo\"}"))
        .andExpect(status().isBadRequest()).andExpect(content().string(""));
    for (int i = 0; i < 21; i++) {
      mvc.perform(post("/auth/login").with(csrf()).with(r -> {
        r.setRemoteAddr(endereco);
        return r;
      })
          .contentType("application/json")
          .content("{\"role\":\"estudantes\",\"email\":\"aluna@example.test\",\"senha\":\"errada\"}"))
          .andExpect(status().is(i < 20 ? 401 : 429));
    }
  }


  @Test
  void totaisDerivamDasRelacoesPersistidasSemDuplicar() throws Exception {
    var alvo = perfilDeOutraEstudante();
    var destinoId = Integer.valueOf(alvo.substring(alvo.lastIndexOf('/') + 1));
    var destino = perfis.findById(destinoId).orElseThrow();
    var session = entrar("estudantes", estudante.getEmail());
    for (int i = 0; i < 2; i++) mvc.perform(post(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/perfil/estudantes/" + estudante.getId()).session(session))
        .andExpect(jsonPath("$.num_seguindo").value(1));
    mvc.perform(get("/perfil/estudantes/" + destino.getId_estudante()).session(session))
        .andExpect(jsonPath("$.num_seguidores").value(1));
    mvc.perform(delete(alvo + "/seguir").session(session).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/perfil/estudantes/" + estudante.getId()).session(session))
        .andExpect(jsonPath("$.num_seguindo").value(0));
    mvc.perform(get("/perfil/estudantes/" + destino.getId_estudante()).session(session))
        .andExpect(jsonPath("$.num_seguidores").value(0));
  }

  @Test
  void configuracoesPreservamSenhaOmitidaENuncaDevolvemHash() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    var original = estudantes.findById(estudante.getId()).orElseThrow().getSenha();
    mvc.perform(put("/perfil/estudantes/" + estudante.getId()).session(session).with(csrf())
        .param("nome", "Nome confirmado").param("usuario", "aluna").param("biografia", "Bio preservada")
        .param("email", "novo@example.test").param("novaSenha", ""))
        .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Nome confirmado"))
        .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.senhaHash").doesNotExist())
        .andExpect(jsonPath("$.novaSenha").doesNotExist());
    assertEquals(original, estudantes.findById(estudante.getId()).orElseThrow().getSenha());
    mvc.perform(get("/perfil/estudantes/" + estudante.getId()).session(session))
        .andExpect(jsonPath("$.email").value("novo@example.test"))
        .andExpect(jsonPath("$.biografia").value("Bio preservada"))
        .andExpect(jsonPath("$.senha").doesNotExist());
    var outraSessao = entrar("estudantes", "novo@example.test");
    mvc.perform(put("/perfil/estudantes/" + estudante.getId()).session(session).with(csrf())
        .param("nome", "Nome confirmado").param("usuario", "aluna").param("biografia", "Bio preservada")
        .param("novaSenha", "NovaFixture123!"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.senha").doesNotExist());
    assertTrue(encoder.matches("NovaFixture123!", estudantes.findById(estudante.getId()).orElseThrow().getSenha().substring(8)));
    mvc.perform(get("/auth/csrf").session(session).header("Origin", "http://localhost:8100"))
        .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8100"))
        .andExpect(jsonPath("$.token").isNotEmpty());
    mvc.perform(get("/auth/session").session(outraSessao)).andExpect(status().isUnauthorized());
  }

  @Test
  void configuracoesInvalidasRevertemTodosOsCampos() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    mvc.perform(put("/perfil/estudantes/" + estudante.getId()).session(session).with(csrf())
        .param("nome", "Não salvar").param("usuario", "aluna").param("biografia", "")
        .param("novaSenha", "curta"))
        .andExpect(status().isBadRequest());
    assertEquals("Aluna", estudantes.findById(estudante.getId()).orElseThrow().getNome());
  }

  @Test
  void interacoesUsamPerfilDaSessaoENaoContaInformada() throws Exception {
    var session = entrar("estudantes", estudante.getEmail());
    var perfilId = perfis.buscarPorEstudante(estudante.getId()).getId();
    when(posts.interacoes("fixture", perfilId)).thenReturn(new com.solucao.hipatec.dto.InteracoesDTO(2, 1, true));
    when(posts.interacoes("fixture", null)).thenReturn(new com.solucao.hipatec.dto.InteracoesDTO(2, 1, false));
    mvc.perform(post("/posts/fixture/like").session(session).with(csrf()).param("usuarioId", "999"))
        .andExpect(status().isOk());
    verify(posts).curtir("fixture", perfilId);
    mvc.perform(delete("/posts/fixture/like").session(session).with(csrf())).andExpect(status().isOk());
    verify(posts).descurtir("fixture", perfilId);
    mvc.perform(get("/posts/fixture/interacoes").session(session)).andExpect(jsonPath("$.curtidoUsuario").value(true));
    mvc.perform(get("/posts/fixture/interacoes")).andExpect(jsonPath("$.curtidoUsuario").value(false));
    mvc.perform(post("/posts/fixture/comment").session(session).with(csrf()).contentType("application/json")
        .content("{\"texto\":\"Comentário fixture\",\"autorId\":999}"))
        .andExpect(status().isOk());
    verify(posts).comentar(eq("fixture"), argThat(c -> c.getAutorId().equals(perfilId) && c.getAutorNome().equals("aluna")));
    mvc.perform(post("/posts/fixture/comment").session(session).with(csrf()).contentType("application/json")
        .content("{\"texto\":\" \"}"))
        .andExpect(status().isBadRequest());
  }

  Mentora criarMentora() {
    var m = new Mentora();
    m.setNome("Mentora"); m.setEmail("mentora@example.test");
    m.setSenha(estudante.getSenha()); m.setAprovada(true);
    m = mentoras.save(m);
    var p = new Perfil(); p.setId_mentora(m.getId()); p.setUsuario("mentora"); perfis.save(p);
    return m;
  }

  int atualizarEmail(String role, Integer id, MockHttpSession session, String email) throws Exception {
    return mvc.perform(put("/perfil/" + role + "/" + id).session(session).with(csrf())
        .param("nome", "Nome").param("usuario", "usuario").param("biografia", "")
        .param("email", email)).andReturn().getResponse().getStatus();
  }

  @Test
  void emailSomenteParaDonoExatoDoPerfil() throws Exception {
    var m = criarMentora();
    var aluna = entrar("estudantes", estudante.getEmail());
    var mentora = entrar("mentoras", m.getEmail());
    perfilDeOutraEstudante();
    var outra = estudantes.findByEmailIgnoreCase("outra@example.test").get(0);
    mvc.perform(get("/perfil/estudantes/" + estudante.getId()).session(aluna))
        .andExpect(jsonPath("$.email").value(estudante.getEmail()));
    mvc.perform(get("/perfil/mentoras/" + m.getId()).session(mentora))
        .andExpect(jsonPath("$.email").value(m.getEmail()));
    for (var session : new MockHttpSession[]{aluna, mentora}) {
      var role = session == aluna ? "mentoras" : "estudantes";
      var id = session == aluna ? m.getId() : estudante.getId();
      var body = mvc.perform(get("/perfil/" + role + "/" + id).session(session))
          .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      assertFalse(new tools.jackson.databind.ObjectMapper().readTree(body).has("email"));
    }
    mvc.perform(get("/perfil/estudantes/" + outra.getId()).session(aluna))
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  void emailColideEntreRolesMasPermitePropriaConta() throws Exception {
    var m = criarMentora();
    var aluna = entrar("estudantes", estudante.getEmail());
    var mentora = entrar("mentoras", m.getEmail());
    assertEquals(409, atualizarEmail("estudantes", estudante.getId(), aluna, m.getEmail().toUpperCase()));
    assertEquals(409, atualizarEmail("mentoras", m.getId(), mentora, estudante.getEmail()));
    assertEquals(200, atualizarEmail("estudantes", estudante.getId(), aluna, estudante.getEmail()));
    assertEquals(200, atualizarEmail("mentoras", m.getId(), mentora, m.getEmail()));
  }

  @Test
  void emailConcorrenteEntreRolesTemUmUnicoVencedor() throws Exception {
    var m = criarMentora();
    var aluna = entrar("estudantes", estudante.getEmail());
    var mentora = entrar("mentoras", m.getEmail());
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CyclicBarrier(2);
    try {
      var a = executor.submit(() -> { start.await(); return atualizarEmail("estudantes", estudante.getId(), aluna, "disputa@example.test"); });
      var b = executor.submit(() -> { start.await(); return atualizarEmail("mentoras", m.getId(), mentora, "DISPUTA@example.test"); });
      assertEquals(java.util.List.of(200, 409), java.util.stream.Stream.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS)).sorted().toList());
      assertEquals(1, estudantes.findByEmailIgnoreCase("disputa@example.test").size() + mentoras.findByEmailIgnoreCase("disputa@example.test").size());
    } finally { executor.shutdownNow(); }
  }

  @Test
  void seguirConcorrenteRetornaSucessoSemDuplicar() throws Exception {
    var alvo = perfilDeOutraEstudante();
    var session = entrar("estudantes", estudante.getEmail());
    var executor = java.util.concurrent.Executors.newFixedThreadPool(6);
    var start = new java.util.concurrent.CyclicBarrier(6);
    try {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 6; i++) futures.add(executor.submit(() -> {
        start.await();
        mvc.perform(post(alvo + "/seguir").session(session).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.seguindo").value(true));
        return null;
      }));
      for (var f : futures) f.get(15, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(1, seguimentos.count());
    } finally { executor.shutdownNow(); }
  }

  @Test
  void emailConcorrenteNoMesmoRoleTemUmUnicoVencedor() throws Exception {
    perfilDeOutraEstudante();
    var outra = estudantes.findByEmailIgnoreCase("outra@example.test").get(0);
    var aSession = entrar("estudantes", estudante.getEmail());
    var bSession = entrar("estudantes", outra.getEmail());
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CyclicBarrier(2);
    try {
      var a = executor.submit(() -> { start.await(); return atualizarEmail("estudantes", estudante.getId(), aSession, "mesmo@example.test"); });
      var b = executor.submit(() -> { start.await(); return atualizarEmail("estudantes", outra.getId(), bSession, "MESMO@example.test"); });
      assertEquals(java.util.List.of(200, 409), java.util.stream.Stream.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS)).sorted().toList());
      assertEquals(1, estudantes.findByEmailIgnoreCase("mesmo@example.test").size());
    } finally { executor.shutdownNow(); }
  }

  @MockitoBean
  com.cloudinary.Cloudinary cloudinaryClient;
}
