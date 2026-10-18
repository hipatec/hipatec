package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.solucao.hipatec.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EmailServiceTests {
    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final String appId = "cc0a54d1-66a8-4d9d-a18a-6874c88aa231";

    private SimpleMailMessage mensagem() {
        var message = new SimpleMailMessage();
        message.setFrom("teste@hipatec.example");
        message.setTo("estudante@example.test");
        message.setSubject("Hipatec — recuperação de senha");
        message.setText("Abra <este> link:\nhttps://hipatec.example/redefinir-senha#token=teste");
        return message;
    }

    @Test
    void smtpLocalNaoPrecisaDeCredenciaisOneSignal() {
        var service = new EmailService(smtp, RestClient.builder(), "smtp", "", "");
        var message = mensagem();
        service.send(message);
        verify(smtp).send(message);
    }

    @Test
    void enviaPelaApiERejeitaFalhasSemExporDadosOuUsarSmtp() {
        var builder = RestClient.builder();
        var service = new EmailService(smtp, builder, "onesignal", appId, "chave-ficticia");
        var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "client", builder.build());

        server.expect(requestTo("https://api.onesignal.com/notifications?c=email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Key chave-ficticia"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "app_id": "cc0a54d1-66a8-4d9d-a18a-6874c88aa231",
                          "email_to": ["estudante@example.test"],
                          "email_from_address": "teste@hipatec.example",
                          "email_subject": "Hipatec — recuperação de senha",
                          "email_body": "<p>Abra &lt;este&gt; link:<br><a href=\\\"https://hipatec.example/redefinir-senha#token=teste\\\">Redefinir senha</a></p>",
                          "include_unsubscribed": true,
                          "disable_email_click_tracking": true
                        }
                        """))
                .andRespond(withSuccess("{\"id\":\"aceito\"}", MediaType.APPLICATION_JSON));
        service.send(mensagem(), "https://hipatec.example/redefinir-senha#token=teste");
        server.verify();

        for (String body : new String[]{"{\"id\":\"\",\"errors\":[\"dado privado\"]}", "{}", "invalid-json"}) {
            server.reset();
            server.expect(requestTo("https://api.onesignal.com/notifications?c=email"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var error = assertThrows(MailSendException.class, () -> service.send(mensagem()));
            assertFalse(error.getMessage().contains("dado privado"));
            assertNull(error.getCause());
            server.verify();
        }
        for (HttpStatus status : new HttpStatus[]{HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR}) {
            server.reset();
            server.expect(requestTo("https://api.onesignal.com/notifications?c=email"))
                    .andRespond(withStatus(status).body("dado privado"));
            var error = assertThrows(MailSendException.class, () -> service.send(mensagem()));
            assertFalse(error.getMessage().contains("dado privado"));
            assertNull(error.getCause());
            server.verify();
        }
        verifyNoInteractions(smtp);
    }

    @Test
    void exigeConfiguracaoExplicitaAntesDeEnviar() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailService(smtp, RestClient.builder(), "invalido", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailService(smtp, RestClient.builder(), "onesignal", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailService(smtp, RestClient.builder(), "onesignal", appId, ""));
        verifyNoInteractions(smtp);
    }
}
