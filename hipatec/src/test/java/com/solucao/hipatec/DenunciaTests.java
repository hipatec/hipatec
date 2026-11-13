package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.DenunciaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, classes=DenunciaTests.Config.class, properties={
    "spring.config.location=optional:classpath:/application-sessao-test.properties",
    "spring.datasource.url=jdbc:h2:mem:denuncias;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "hipatec.firebase.enabled=false", "hipatec.posts.enabled=false",
    "hipatec.recuperacao-senha.url=http://localhost:8100/redefinir-senha", "hipatec.mail.from=teste@hipatec.local"
})
class DenunciaTests extends EventosTests {
    @Import({EventosTests.Config.class, com.solucao.hipatec.controller.DenunciaController.class,
        com.solucao.hipatec.service.DenunciaService.class})
    static class Config {}
    String enviar(org.springframework.mock.web.MockHttpSession sessao, String id, String body, int status) throws Exception {
        return mvc.perform(post("/posts/"+id+"/denuncias").session(sessao).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
    Post publicacao(String id) {
        var p=new Post(); p.setId(id); p.setAutorId(987); p.setConteudo("Texto visual");
        p.setImagemUrl("https://example.test/imagem.png"); p.setImagemHash("sha256-imagem");
        p.setAutorFoto("NAO_COPIAR"); p.setRequisicaoId("NAO_COPIAR"); return p;
    }
    @Test void evidenciaVisualEOriginalSobrevivemAoTombstoneNoSql() throws Exception {
        var id="imagem-"+java.util.UUID.randomUUID(); var original=publicacao("original-"+id);
        var p=publicacao(id); p.setRepostOriginalId(original.getId());
        p.setRepostOriginal(publicacao("forjado"));
        when(posts.recuperar(id)).thenReturn(p); when(posts.recuperar(original.getId())).thenReturn(original);
        var sessao=entrar("estudantes",estudante.getEmail());
        var resposta=enviar(sessao,id,"{\"motivo\":\"spam\"}",201);
        when(posts.recuperar(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(posts.recuperar(original.getId())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var json=jdbc.queryForObject("select evidencia from denuncia where post_id=?",String.class,id);
        var evidence=new tools.jackson.databind.ObjectMapper().readTree(json);
        assertEquals(p.getImagemUrl(),evidence.path("publicacao").path("imagemUrl").asText());
        assertEquals(p.getImagemHash(),evidence.path("publicacao").path("imagemHash").asText());
        assertEquals(original.getId(),evidence.path("original").path("id").asText());
        assertEquals(original.getImagemUrl(),evidence.path("original").path("imagemUrl").asText());
        assertFalse(json.contains("NAO_COPIAR")); assertFalse(json.contains("forjado"));
        assertEquals(resposta,enviar(entrar("estudantes",estudante.getEmail()),id,"{\"motivo\":\"spam\"}",201));
    }
    @Test void respostaPerdidaComentarioExcluidoNovoLoginConflitosEOutroDono() throws Exception {
        var id="retry-"+java.util.UUID.randomUUID(); when(posts.recuperar(id)).thenReturn(publicacao(id));
        var c=new Comentario(); c.setId("comentario"); c.setTexto("Texto denunciado"); c.setAutorId(456);
        when(posts.listarComentarios(id)).thenReturn(java.util.List.of(c));
        var body="{\"motivo\":\"assedio\",\"comentarioId\":\"comentario\"}";
        var sessao=entrar("estudantes",estudante.getEmail());
        enviar(sessao,id,body,201); // resposta descartada: reconciliar pelo estado SQL confirmado
        var repo=context.getBean(DenunciaRepository.class);
        var protocolo=repo.findAll().stream().filter(d->d.getPostId().equals(id)).findFirst().orElseThrow().getId();
        when(posts.listarComentarios(id)).thenReturn(java.util.List.of());
        var retry=enviar(sessao,id,body,201); assertTrue(retry.contains("\"protocolo\":"+protocolo));
        when(posts.recuperar(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(retry,enviar(entrar("estudantes",estudante.getEmail()),id,body,201));
        enviar(sessao,id,body.replace("assedio","spam"),409);
        enviar(sessao,id,body.replace("assedio","invalido"),400);
        enviar(sessao,id,body.replace("comentario\"","outro\""),404);
        var outra=new Mentora(); outra.setEmail("outra-denuncia@example.test"); outra.setSenha(estudante.getSenha()); outra.setAprovada(true); outra=mentoras.save(outra);
        var perfil=new Perfil(); perfil.setId_mentora(outra.getId()); perfil.setUsuario("outra"); perfis.save(perfil);
        var negada=enviar(entrar("mentoras",outra.getEmail()),id,body,404);
        assertFalse(negada.contains("protocolo"));
        assertEquals(1,repo.findAll().stream().filter(d->d.getPostId().equals(id)).count());
    }
    @Test void snapshotCabeNaColunaExistenteComTextoMaximoEEscapes() throws Exception {
        var id="longo-"+java.util.UUID.randomUUID(); var p=publicacao(id); p.setConteudo("\"\n".repeat(2500));
        var original=publicacao("orig-"+id); original.setConteudo("x".repeat(5000)); p.setRepostOriginalId(original.getId());
        when(posts.recuperar(id)).thenReturn(p); when(posts.recuperar(original.getId())).thenReturn(original);
        enviar(entrar("estudantes",estudante.getEmail()),id,"{\"motivo\":\"spam\"}",201);
        var json=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject("select evidencia from denuncia where post_id=?",String.class,id);
        assertTrue(json.length()<=5000);
        var tree=new tools.jackson.databind.ObjectMapper().readTree(json);
        assertEquals(p.getImagemHash(),tree.path("publicacao").path("imagemHash").asText());
        assertTrue(tree.path("publicacao").path("conteudoTruncado").asBoolean());
        assertEquals(64,tree.path("publicacao").path("conteudoHash").asText().length());
    }
}
