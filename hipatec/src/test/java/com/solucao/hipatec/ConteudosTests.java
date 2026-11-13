package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.solucao.hipatec.model.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ConteudosTests.Config.class, properties = {
    "spring.config.location=optional:classpath:/application-sessao-test.properties",
    "spring.datasource.url=jdbc:h2:mem:conteudos;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "hipatec.firebase.enabled=false", "hipatec.posts.enabled=false",
    "hipatec.recuperacao-senha.url=http://localhost:8100/redefinir-senha",
    "hipatec.mail.from=teste@hipatec.local"
})
class ConteudosTests extends EventosTests {
  @Import({EventosTests.Config.class, com.solucao.hipatec.controller.CursoController.class, com.solucao.hipatec.service.CursoService.class})
  static class Config {}

  @Test void criaConteudoComDonoDaSessaoERecuperaEmNovaSessao() throws Exception {
    var mentora = new Mentora(); mentora.setEmail("criadora@example.test");
    mentora.setSenha(estudante.getSenha()); mentora.setAprovada(true); mentora = mentoras.save(mentora);
    var perfil = new Perfil(); perfil.setId_mentora(mentora.getId()); perfil.setUsuario("Criadora"); perfis.save(perfil);
    var sessao = entrar("mentoras", mentora.getEmail());
    for (String rota : new String[]{"/cursos", "/mentorias"}) {
      String corpo = "{\"titulo\":\"Conteúdo real\",\"descricao\":\"Descrição\",\"formato\":\"Online\",\"area\":\"Dados\",\"instituicao\":\"Hipatec\",\"idMentora\":999,\"requisicaoId\":\""+java.util.UUID.randomUUID()+"\"}";
      mvc.perform(post(rota).session(sessao).contentType("application/json").content(corpo)).andExpect(status().isForbidden());
      var resposta = mvc.perform(post(rota).session(sessao).with(csrf()).contentType("application/json").content(corpo))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.idMentora").value(mentora.getId())).andExpect(jsonPath("$.formato").value("Online"))
        .andReturn().getResponse().getContentAsString();
      var repetido = mvc.perform(post(rota).session(sessao).with(csrf()).contentType("application/json").content(corpo)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
      assertEquals(resposta, repetido);
      mvc.perform(get(rota).session(entrar("mentoras",mentora.getEmail()))).andExpect(status().isOk()).andExpect(jsonPath("$[0].titulo").value("Conteúdo real"));
      mvc.perform(post(rota).session(entrar("estudantes",estudante.getEmail())).with(csrf()).contentType("application/json").content(corpo)).andExpect(status().isForbidden());
      mvc.perform(post(rota).session(sessao).with(csrf()).contentType("application/json").content(corpo.replace("Conteúdo real", "   "))).andExpect(status().isBadRequest());
    }
  }
}
