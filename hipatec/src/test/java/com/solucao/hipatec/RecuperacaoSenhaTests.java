package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.solucao.hipatec.config.RecuperacaoSenhaConfig;
import com.solucao.hipatec.controller.RecuperacaoSenhaController;
import com.solucao.hipatec.model.Estudante;
import com.solucao.hipatec.model.Mentora;
import com.solucao.hipatec.repository.*;
import com.solucao.hipatec.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.StoredProcedureQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = RecuperacaoSenhaTests.Config.class, properties = {
        "spring.config.location=optional:classpath:/application-recuperacao-test.properties",
        "spring.datasource.url=jdbc:h2:mem:recuperacao;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "hipatec.recuperacao-senha.url=http://localhost:8100/redefinir-senha",
        "hipatec.mail.from=teste@hipatec.local",
        "logging.level.org.hibernate.SQL=OFF"
})
class RecuperacaoSenhaTests {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.solucao.hipatec.model")
    @EnableJpaRepositories("com.solucao.hipatec.repository")
    @Import({RecuperacaoSenhaConfig.class, RecuperacaoSenhaService.class, EmailService.class,
            EstudanteService.class, MentoraService.class})
    static class Config {
        @Bean JavaMailSender mail() { return mock(JavaMailSender.class); }
    }

    @Autowired RecuperacaoSenhaService service;
    @Autowired RecuperacaoSenhaRepository tokens;
    @Autowired EstudanteRepository estudantes;
    @Autowired MentoraRepository mentoras;
    @Autowired EstudanteService estudanteService;
    @Autowired MentoraService mentoraService;
    @Autowired JavaMailSender mail;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ThreadPoolTaskExecutor recuperacaoSenhaExecutor;
    private MockMvc mvc;

    @BeforeEach
    void preparar() throws Exception {
        aguardarProcessamento();
        tokens.deleteAll();
        estudantes.deleteAll();
        mentoras.deleteAll();
        reset(mail);
        
        mvc = MockMvcBuilders.standaloneSetup(new RecuperacaoSenhaController(service)).build();
        
        var estudante = new Estudante();
        estudante.setNome("Estudante de teste");
        estudante.setEmail("estudante@example.test");
        estudante.setSenha("senha-legada");
        estudantes.save(estudante);

        var mentora = new Mentora();
        mentora.setNome("Mentora de teste");
        mentora.setEmail("mentora@example.test");
        mentora.setSenha("senha-legada");
        mentoras.save(mentora);
    }

    private void aguardarProcessamento() throws Exception {
        long prazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (recuperacaoSenhaExecutor.getActiveCount() != 0 || recuperacaoSenhaExecutor.getQueueSize() != 0) {
            if (System.nanoTime() > prazo) {
                fail("Processamento assíncrono não terminou.");
            }

            Thread.sleep(20);
        }
    }

    private String solicitar(String email) throws Exception {
        service.solicitar(email);
        aguardarProcessamento();
        
        var mensagem = ArgumentCaptor.forClass(SimpleMailMessage.class);
        
        verify(mail, atLeastOnce()).send(mensagem.capture());
        
        return mensagem.getValue().getText().split("#token=")[1].split("\\s")[0];
    }

    @Test
    void redefineEstudanteEImpedeReusoESenhaAntiga() throws Exception {
        String token = solicitar("ESTUDANTE@example.test");
        assertNotEquals(token, tokens.findAll().get(0).getTokenHash());
        assertEquals(64, tokens.findAll().get(0).getTokenHash().length());
        assertEquals("senha-legada", estudantes.findAll().get(0).getSenha());
        
        service.redefinir(token, "NovaSenha123!");
        
        var conta = estudantes.findAll().get(0);
        assertTrue(encoder.matches("NovaSenha123!", conta.getSenha().substring(8)));
        assertEquals(conta.getId(), estudanteService.login(conta.getEmail(), "NovaSenha123!"));
        assertThrows(ResponseStatusException.class, () -> estudanteService.login(conta.getEmail(), "senha-legada"));
        assertThrows(ResponseStatusException.class, () -> service.redefinir(token, "OutraSenha123!"));
        assertEquals(0, tokens.count());
    }

    @Test
    void redefineMentoraSemAlterarEstudante() throws Exception {
        String token = solicitar("mentora@example.test");
        
        service.redefinir(token, "NovaSenha123!");
        
        var conta = mentoras.findAll().get(0);
        assertEquals(conta.getId(), mentoraService.login(conta.getEmail(), "NovaSenha123!"));
        assertThrows(ResponseStatusException.class, () -> mentoraService.login(conta.getEmail(), "senha-legada"));
        assertEquals("senha-legada", estudantes.findAll().get(0).getSenha());
    }

    @Test
    void rejeitaLinkExpiradoOuAleatorioSemAlterarSenha() throws Exception {
        String token = solicitar("estudante@example.test");
        
        var recuperacao = tokens.findAll().get(0);
        
        recuperacao.setExpiraEm(Instant.now().minusSeconds(1));
        tokens.save(recuperacao);
        
        assertThrows(ResponseStatusException.class, () -> service.redefinir(token, "NovaSenha123!"));
        assertThrows(ResponseStatusException.class, () -> service.redefinir("z".repeat(43), "NovaSenha123!"));
        assertEquals("senha-legada", estudantes.findAll().get(0).getSenha());
    }

    @Test
    void limitaReenvioESubstituiLinkAnterior() throws Exception {
        String anterior = solicitar("estudante@example.test");
        
        service.solicitar("estudante@example.test");
        
        aguardarProcessamento();
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        
        var recuperacao = tokens.findAll().get(0);
        
        recuperacao.setCriadoEm(Instant.now().minusSeconds(61));
        tokens.save(recuperacao);
        
        String novo = solicitar("estudante@example.test");
        
        assertNotEquals(anterior, novo);
        assertThrows(ResponseStatusException.class, () -> service.redefinir(anterior, "NovaSenha123!"));
        
        service.redefinir(novo, "NovaSenha123!");
    }

    @Test
    void falhaNoEnvioPreservaLinkAnterior() throws Exception {
        String anterior = solicitar("estudante@example.test");
        
        var recuperacao = tokens.findAll().get(0);
        
        recuperacao.setCriadoEm(Instant.now().minusSeconds(61));
        tokens.save(recuperacao);
        
        doThrow(new MailSendException("SMTP local indisponível")).when(mail).send(any(SimpleMailMessage.class));
        
        service.solicitar("estudante@example.test");
        
        aguardarProcessamento();
        // O aviso de confirmação também falhará; a senha ainda deve ser alterada.
        service.redefinir(anterior, "NovaSenha123!");
        assertEquals(0, tokens.count());
    }

    @Test
    void respostasIguaisParaContaConhecidaEDesconhecida() throws Exception {
        for (String email : List.of("estudante@example.test", "desconhecida@example.test")) {
            var resposta = mvc.perform(post("/auth/recuperacao-senha").contentType("application/json")
                    .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted()).andReturn().getResponse();
            assertEquals("", resposta.getContentAsString());
        }
        aguardarProcessamento();
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void naoEscolheContaQuandoEmailEstaDuplicado() throws Exception {
        var duplicada = new Estudante();
        
        duplicada.setEmail("estudante@example.test");
        estudantes.save(duplicada);
        service.solicitar("estudante@example.test");
        
        aguardarProcessamento();
        verifyNoInteractions(mail);
        assertEquals(0, tokens.count());
    }

    @Test
    void naoEscolheContaQuandoEmailExisteNosDoisPerfis() throws Exception {
        var mentora = mentoras.findAll().get(0);
        mentora.setEmail("ESTUDANTE@example.test");
        mentoras.save(mentora);
        mvc.perform(post("/auth/recuperacao-senha").contentType("application/json")
                .content("{\"email\":\"estudante@example.test\"}"))
                .andExpect(status().isAccepted());
        aguardarProcessamento();
        verifyNoInteractions(mail);
        assertEquals(0, tokens.count());
        assertEquals("senha-legada", estudantes.findAll().get(0).getSenha());
        assertEquals("senha-legada", mentoras.findAll().get(0).getSenha());
    }

    @Test
    void validaEntradaELimitaTentativasHttp() throws Exception {
        mvc.perform(post("/auth/recuperacao-senha").contentType("application/json")
                .content("{\"email\":\"inválido\"}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/recuperacao-senha").contentType("application/json")
                    .content("{\"email\":\"nao-existe@example.test\"}"))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/auth/recuperacao-senha").contentType("application/json")
                .content("{\"email\":\"nao-existe@example.test\"}"))
                .andExpect(status().isTooManyRequests());
        aguardarProcessamento();
    }

    @Test
    void redefineViaHttpSemDevolverSenhaOuToken() throws Exception {
        String token = solicitar("estudante@example.test");

        mvc.perform(post("/auth/redefinir-senha").contentType("application/json")
                .content("{\"token\":\"" + token + "\",\"senha\":\"curta\"}"))
                .andExpect(status().is(422));
        
        String corpo = "{\"token\":\"" + token + "\",\"senha\":\"NovaSenha123!\"}";
        
        var resposta = mvc.perform(post("/auth/redefinir-senha").contentType("application/json").content(corpo))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        
        assertEquals("", resposta.getContentAsString());

        mvc.perform(post("/auth/redefinir-senha").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void naoConsomeTokenQuandoSenhaNaoAtendeLimites() throws Exception {
        String token = solicitar("estudante@example.test");
        
        for (String senha : List.of("curta", " ".repeat(8), "á".repeat(40))) {
            assertThrows(ResponseStatusException.class, () -> service.redefinir(token, senha));
        }
        
        assertEquals(1, tokens.count());
        service.redefinir(token, "NovaSenha123!");
    }

    @Test
    void somenteUmaTrocaConcorrentePodeUsarOMesmoLink() throws Exception {
        String token = solicitar("estudante@example.test");
        
        var inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        
        try {
            var tarefa = (java.util.concurrent.Callable<Boolean>) () -> {
                inicio.await();
                try { service.redefinir(token, "NovaSenha123!"); return true; }
                catch (ResponseStatusException error) { return false; }
            };
            var primeira = executor.submit(tarefa);
            var segunda = executor.submit(tarefa);
            inicio.countDown();
            assertNotEquals(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void mantemProcedimentoLegadoParaContasAindaNaoRecuperadas() {
        var em = mock(EntityManager.class);
        var query = mock(StoredProcedureQuery.class);
        
        when(em.createStoredProcedureQuery(any(String.class))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(17);
        
        var estudante = new EstudanteService(estudantes, encoder);
        var mentora = new MentoraService(mentoras, encoder);
        
        ReflectionTestUtils.setField(estudante, "entityManager", em);
        ReflectionTestUtils.setField(mentora, "entityManager", em);
        
        assertEquals(17, estudante.login("estudante@example.test", "senha-legada"));
        assertEquals(17, mentora.login("mentora@example.test", "senha-legada"));
        
        verify(em).createStoredProcedureQuery("login_estudante");
        verify(em).createStoredProcedureQuery("login_mentora");
    }

    @Test
    void respostasDeContasNaoExpoemSenha() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        
        assertFalse(mapper.writeValueAsString(estudantes.findAll().get(0)).contains("senha"));
        assertFalse(mapper.writeValueAsString(mentoras.findAll().get(0)).contains("senha"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HIPATEC_TEST_SMTP_PORT", matches = "[0-9]+")
    void enviaRecuperacaoParaCaixaSmtpLocal() {
        var smtp = new JavaMailSenderImpl();
        
        smtp.setHost("127.0.0.1");
        smtp.setPort(Integer.parseInt(System.getenv("HIPATEC_TEST_SMTP_PORT")));
        
        var email = new EmailService(smtp, org.springframework.web.client.RestClient.builder(), "smtp", "", "");
        var local = new RecuperacaoSenhaService(estudantes, mentoras, tokens, email, encoder);
        
        ReflectionTestUtils.setField(local, "resetUrl", "http://localhost:8100/redefinir-senha");
        ReflectionTestUtils.setField(local, "remetente", "teste@hipatec.local");
        
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                local.solicitar("estudante@example.test"));
        assertEquals(1, tokens.count());
    }
}
