package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
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
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SessaoTests.Config.class, properties = {
 "spring.config.location=optional:classpath:/application-sessao-test.properties",
 "spring.datasource.url=jdbc:h2:mem:sessao;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SessaoTests {
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
 @EntityScan("com.solucao.hipatec.model") @EnableJpaRepositories("com.solucao.hipatec.repository")
 @Import({SecurityConfig.class, SessaoController.class, EstudanteController.class, MentoraController.class,
   EstudanteService.class, MentoraService.class, PerfilController.class, PerfilService.class,
   PostController.class, MentoriaController.class, MentoriaService.class})
 static class Config {
  @Bean BCryptPasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
  @Bean com.cloudinary.Cloudinary cloudinaryClient() { return mock(com.cloudinary.Cloudinary.class); }
  @Bean CloudinaryService cloudinary() { return mock(CloudinaryService.class); }
  @Bean PostService posts() { return mock(PostService.class); }
 }
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired WebApplicationContext context;
 @Autowired EstudanteRepository estudantes;
 @Autowired MentoraRepository mentoras;
 @Autowired PerfilRepository perfis;
 @Autowired BCryptPasswordEncoder encoder;
 @Autowired PostService posts;
 MockMvc mvc;
 Estudante estudante;
 static int ip;
 String endereco;
 @BeforeEach void preparar() {
  mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  perfis.deleteAll(); estudantes.deleteAll(); mentoras.deleteAll(); reset(posts);
  estudante = new Estudante(); estudante.setEmail("aluna@example.test"); estudante.setNome("Aluna");
  estudante.setSenha("{bcrypt}" + encoder.encode("TesteLocal123!")); estudante = estudantes.save(estudante);
  var perfil = new Perfil(); perfil.setId_estudante(estudante.getId()); perfil.setUsuario("aluna"); perfis.save(perfil);
  endereco = "192.0.2." + (++ip);
 }
 MockHttpSession entrar(String role, String email) throws Exception {
  return (MockHttpSession) mvc.perform(post("/auth/login").with(csrf()).with(r -> {r.setRemoteAddr(endereco); return r;})
   .contentType("application/json").content("{\"role\":\""+role+"\",\"email\":\""+email+"\",\"senha\":\"TesteLocal123!\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.senhaHash").doesNotExist()).andReturn().getRequest().getSession(false);
 }
 @Test void visitanteNaoPodeListarDadosDeContas() throws Exception {
  mvc.perform(get("/estudantes")).andExpect(status().isUnauthorized());
  mvc.perform(get("/perfil/estudantes/"+estudante.getId())).andExpect(status().isUnauthorized());
  mvc.perform(get("/posts")).andExpect(status().isOk());
 }
 @Test void sessaoPersisteLogoutRevogaEExigeCsrf() throws Exception {
  var session = entrar("estudantes", estudante.getEmail());
  mvc.perform(get("/auth/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(estudante.getId()));
  mvc.perform(post("/auth/logout").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
  assertTrue(session.isInvalid());
  mvc.perform(get("/auth/session")).andExpect(status().isUnauthorized());
 }
 @Test void rejeitaCredenciaisInvalidasELoginAntigoPorUrl() throws Exception {
  mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
   .content("{\"role\":\"estudantes\",\"email\":\"aluna@example.test\",\"senha\":\"errada\"}"))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/estudantes/login").with(csrf()).param("email", estudante.getEmail()).param("senha", "TesteLocal123!"))
   .andExpect(status().isUnauthorized());
 }
 @Test void estudanteNaoPodeEditarOutraContaNemCriarMentoria() throws Exception {
  var session = entrar("estudantes", estudante.getEmail());
  mvc.perform(put("/perfil/estudantes/9999").session(session).with(csrf())
   .param("nome","Intrusa").param("usuario","intrusa").param("biografia","")) .andExpect(status().isForbidden());
  mvc.perform(put("/perfil/mentoras/"+estudante.getId()).session(session).with(csrf())
   .param("nome","Intrusa").param("usuario","intrusa").param("biografia","")) .andExpect(status().isForbidden());
  mvc.perform(post("/mentorias").session(session).with(csrf()).contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
  mvc.perform(put("/perfil/estudantes/"+estudante.getId()).session(session).with(csrf())
   .param("nome","Nome novo").param("usuario","aluna").param("biografia","Bio")) .andExpect(status().isOk());
  assertEquals("Nome novo", estudantes.findById(estudante.getId()).orElseThrow().getNome());
 }
 @Test void mentoraPendenteNaoEntraEAprovacaoRevogadaEncerraSessao() throws Exception {
  var mentora = new Mentora(); mentora.setEmail("mentora@example.test"); mentora.setSenha(estudante.getSenha()); mentora = mentoras.save(mentora);
  mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
   .content("{\"role\":\"mentoras\",\"email\":\"mentora@example.test\",\"senha\":\"TesteLocal123!\"}"))
   .andExpect(status().isForbidden());
  mentora.setAprovada(true); mentoras.save(mentora);
  var session = entrar("mentoras", mentora.getEmail());
  mvc.perform(get("/auth/session").session(session)).andExpect(status().isOk());
  mentora.setAprovada(false); mentoras.save(mentora);
  mvc.perform(get("/auth/session").session(session)).andExpect(status().isUnauthorized());
 }
 @Test void trocarSenhaRevogaSessaoAnterior() throws Exception {
  var session = entrar("estudantes", estudante.getEmail());
  estudante.setSenha("{bcrypt}"+encoder.encode("OutraSenha123!")); estudantes.save(estudante);
  mvc.perform(get("/auth/session").session(session)).andExpect(status().isUnauthorized());
 }
 @Test void cadastroGeraHashEIgnoraTentativaDeAutoAprovacao() throws Exception {
  for (String role : new String[]{"estudantes","mentoras"}) {
   mvc.perform(post("/"+role).with(csrf()).contentType("application/json").content("""
    {"nome":"Nova","email":"nova@example.test","usuario":"nova","senha":"TesteLocal123!","dataNascimento":"2000-01-01","aprovada":true}
    """)).andExpect(status().isOk()).andExpect(jsonPath("$.senha").doesNotExist());
  }
  assertTrue(encoder.matches("TesteLocal123!", estudantes.findByEmailIgnoreCase("nova@example.test").get(0).getSenha().substring(8)));
  assertFalse(mentoras.findByEmailIgnoreCase("nova@example.test").get(0).getAprovada());
 }
 @Test void autoriaDePostVemDaSessao() throws Exception {
  var session = entrar("estudantes", estudante.getEmail());
  mvc.perform(post("/posts").session(session).with(csrf()).contentType("application/json")
   .content("{\"autorId\":9999,\"conteudo\":\"Teste\",\"autorFoto\":\"falsa\"}")) .andExpect(status().isOk());
  verify(posts).criar(argThat(p -> p.getAutorId().equals(perfis.buscarPorEstudante(estudante.getId()).getId()) && p.getAutorFoto()==null));
 }
 @Test void corsPermiteSomenteOrigemDaAplicacao() throws Exception {
  mvc.perform(options("/auth/login").header("Origin","https://intrusa.example").header("Access-Control-Request-Method","POST"))
   .andExpect(status().isForbidden());
  mvc.perform(options("/auth/login").header("Origin","http://localhost:8100").header("Access-Control-Request-Method","POST"))
   .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials","true"));
 }
 @Test void httpRealPreservaRespostaDeMentoraPendente() throws Exception {
  var mentora = new Mentora(); mentora.setEmail("pendente@example.test"); mentora.setSenha(estudante.getSenha()); mentoras.save(mentora);
  var client = java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
  var base = "http://localhost:" + port;
  var csrfResponse = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/auth/csrf")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
  var token = new tools.jackson.databind.ObjectMapper().readTree(csrfResponse.body()).get("token").asText();
  var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/auth/login"))
   .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
   .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
    {"role":"mentoras","email":"pendente@example.test","senha":"TesteLocal123!"}
    """)).build();
  assertEquals(403, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
 }
 @Test void validaEntradaSemExporCredenciaisELimitaTentativas() throws Exception {
  mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
    .content("{\"role\":\"admin\",\"email\":\"invalido\",\"senha\":\"segredo\"}"))
    .andExpect(status().isBadRequest()).andExpect(content().string(""));
  for (int i=0;i<21;i++) {
   mvc.perform(post("/auth/login").with(csrf()).with(r -> {r.setRemoteAddr(endereco); return r;})
    .contentType("application/json").content("{\"role\":\"estudantes\",\"email\":\"aluna@example.test\",\"senha\":\"errada\"}"))
    .andExpect(status().is(i<20 ? 401 : 429));
  }
 }
}
