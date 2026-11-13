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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EventosTests.Config.class, properties = {
    "spring.config.location=optional:classpath:/application-sessao-test.properties",
    "spring.datasource.url=jdbc:h2:mem:eventos;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "hipatec.firebase.enabled=false", "hipatec.posts.enabled=false",
    "hipatec.recuperacao-senha.url=http://localhost:8100/redefinir-senha",
    "hipatec.mail.from=teste@hipatec.local"
})
class EventosTests {
  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan("com.solucao.hipatec.model")
  // @EnableJpaRepositories("com.solucao.hipatec.repository")
  @Import({ SecurityConfig.class, CloudinaryConfig.class, SessaoController.class, EstudanteController.class, MentoraController.class,
      EstudanteService.class, MentoraService.class, PerfilController.class, PerfilService.class,
      PostController.class, MentoriaController.class, MentoriaService.class, HomeController.class,
      SeguimentoController.class, EventoController.class, EventoService.class })
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
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  EventoRepository eventos;
  MockMvc mvc;
  Estudante estudante;
  static int ip;
  String endereco;

  @BeforeEach
  void preparar() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    context.getBean(EventoRepository.class).deleteAll();
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
  void criaDiaCivilRecuperaENovaSessao() throws Exception {
    var sessao = entrar("estudantes", estudante.getEmail());
    String corpo = "{\"titulo\":\"Estudo\",\"data\":\"2026-12-01\",\"visibilidade\":\"PRIVADO\",\"requisicaoId\":\"11111111-1111-4111-8111-111111111111\"}";
    mvc.perform(post("/eventos").session(sessao).with(csrf()).contentType("application/json").content(corpo))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.data").value("2026-12-01"))
      .andExpect(jsonPath("$.horario").isEmpty()).andExpect(jsonPath("$.fuso").value("America/Sao_Paulo"));
    mvc.perform(post("/eventos").session(sessao).with(csrf()).contentType("application/json").content(corpo)).andExpect(status().isCreated());
    var novaSessao = entrar("estudantes", estudante.getEmail());
    mvc.perform(get("/eventos?de=2026-12-01&ate=2026-12-31").session(novaSessao))
      .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  }
  String evento(String visibilidade, String extra) {
    return "{\"titulo\":\"Encontro\",\"data\":\"2026-12-01\",\"visibilidade\":\""+visibilidade+"\",\"requisicaoId\":\""+java.util.UUID.randomUUID()+"\""+extra+"}";
  }
  String publicar(MockHttpSession sessao, String corpo) throws Exception {
    return mvc.perform(post("/eventos").session(sessao).with(csrf()).contentType("application/json").content(corpo))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }
  @Test void acessoPrivadoPublicoCsrfEIdentidadesColidentes() throws Exception {
    var dona = entrar("estudantes", estudante.getEmail());
    var mentora = new Mentora(); mentora.setEmail("mentora@example.test"); mentora.setSenha(estudante.getSenha()); mentora.setAprovada(true);
    mentora = mentoras.save(mentora);
    var perfil = new Perfil(); perfil.setId_mentora(mentora.getId()); perfil.setUsuario("mentora"); perfis.save(perfil);
    var outra = entrar("mentoras", mentora.getEmail());
    var privado = publicar(dona, evento("PRIVADO", ",\"proprietarioPerfilId\":9999"));
    var id = new tools.jackson.databind.ObjectMapper().readTree(privado).get("id").asLong();
    mvc.perform(get("/eventos/"+id).session(dona)).andExpect(status().isOk());
    mvc.perform(get("/eventos/"+id).session(outra)).andExpect(status().isNotFound());
    mvc.perform(get("/eventos/"+id)).andExpect(status().isUnauthorized());
    mvc.perform(get("/eventos?de=2026-12-01&ate=2026-12-31")).andExpect(status().isUnauthorized());
    mvc.perform(post("/eventos").session(dona).contentType("application/json").content(evento("PRIVADO", ""))).andExpect(status().isForbidden());
    mvc.perform(post("/eventos").session(dona).with(csrf()).contentType("application/json").content(evento("PUBLICO", ""))).andExpect(status().isForbidden());
    publicar(outra, evento("PUBLICO", ",\"horario\":\"14:30\""));
    mvc.perform(get("/eventos?de=2026-12-01&ate=2026-12-31").session(dona)).andExpect(jsonPath("$.length()").value(2));
    mvc.perform(get("/eventos?de=2026-12-01&ate=2026-12-31").session(outra)).andExpect(jsonPath("$.length()").value(1));
    mentora.setAprovada(false); mentoras.save(mentora);
    mvc.perform(get("/eventos/"+id).session(outra)).andExpect(status().isUnauthorized());
  }
  @Test void validaEntradasIntervalosELimitesSemGravar() throws Exception {
    var sessao = entrar("estudantes", estudante.getEmail());
    String valido = evento("PRIVADO", "");
    for (String corpo : new String[]{valido.replace("Encontro", " "), valido.replace("Encontro", "x".repeat(161)),
        valido.replace("2026-12-01", "2026-02-30"), valido.replace("2026-12-01", "1999-12-31"),
        evento("INVALIDO", ""), evento("PRIVADO", ",\"horario\":\"25:00\""),
        evento("PRIVADO", ",\"horario\":\"12:30:01\""),
        evento("PRIVADO", ",\"horario\":\"00:30\"").replace("2026-12-01", "2018-11-04"),
        evento("PRIVADO", ",\"descricao\":\""+"x".repeat(4001)+"\"")}) {
      mvc.perform(post("/eventos").session(sessao).with(csrf()).contentType("application/json").content(corpo)).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/eventos?de=2026-12-31&ate=2026-12-01").session(sessao)).andExpect(status().isBadRequest());
    mvc.perform(get("/eventos?de=2026-01-01&ate=2028-12-01").session(sessao)).andExpect(status().isBadRequest());
    assertEquals(0, context.getBean(EventoRepository.class).count());
  }
  @Test void repeticaoComDadosDiferentesNaoSobrescreve() throws Exception {
    var sessao = entrar("estudantes", estudante.getEmail()); var corpo = evento("PRIVADO", "");
    publicar(sessao, corpo);
    mvc.perform(post("/eventos").session(sessao).with(csrf()).contentType("application/json").content(corpo.replace("Encontro", "Outro")))
      .andExpect(status().isConflict());
    assertEquals(1, context.getBean(EventoRepository.class).count());
    assertEquals("Encontro", context.getBean(EventoRepository.class).findAll().get(0).getTitulo());
  }
  @Test void falhaDePersistenciaNaoConfirmaNemGrava() throws Exception {
    var sessao = entrar("estudantes", estudante.getEmail());
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("falha sintética"))
      .when(eventos).saveAndFlush(any(Evento.class));
    assertThrows(Exception.class, () -> mvc.perform(post("/eventos").session(sessao).with(csrf())
      .contentType("application/json").content(evento("PRIVADO", ""))));
    assertEquals(0, eventos.count());
  }
}
