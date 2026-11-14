package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.solucao.hipatec.service.EmailService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class BrevoEmailServiceTests {
    private static final String KEY = "synthetic-private-key";
    private static final String LINK = "https://hipatec.invalid/redefinir#token=synthetic-private-token&x=\"<tag>";
    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String response = "{\"messageId\":\"synthetic-accepted\"}";
    private volatile boolean delay;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            path.set(exchange.getRequestURI().toString());
            method.set(exchange.getRequestMethod());
            headers.set(exchange.getRequestHeaders());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delay) release.await(20, TimeUnit.SECONDS);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
        verifyNoInteractions(smtp);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3";
    }

    private EmailService brevo(String key) {
        return new EmailService(smtp, RestClient.builder(), "brevo", "", "", "", "", "",
                key, baseUrl());
    }

    private SimpleMailMessage message() {
        var message = new SimpleMailMessage();
        message.setFrom("Hipatec <sender@hipatec.invalid>");
        message.setTo("private-recipient@hipatec.invalid");
        message.setSubject("Recuperação de senha | Hipatec");
        message.setText("Olá <script> & \"pessoa\".\n\n" + LINK + "\n\nIgnore se não solicitou.");
        return message;
    }

    @Test
    void sendsJsonWithPrivateHeaderAndPreservesMailgunRendering(CapturedOutput output) {
        var message = message();
        brevo(KEY).send(message, LINK);
        assertEquals("POST", method.get());
        assertEquals("/v3/smtp/email", path.get());
        assertEquals(KEY, headers.get().getFirst("api-key"));
        assertEquals("application/json", headers.get().getFirst("Content-Type"));
        assertNull(headers.get().getFirst("Authorization"));
        var payload = json.readTree(body.get());
        assertEquals(5, payload.size());
        assertEquals("sender@hipatec.invalid", payload.at("/sender/email").asText());
        assertEquals("Hipatec", payload.at("/sender/name").asText());
        assertEquals(message.getTo()[0], payload.at("/to/0/email").asText());
        assertEquals(1, payload.get("to").size());
        assertEquals(message.getSubject(), payload.get("subject").asText());
        assertEquals(message.getText(), payload.get("textContent").asText());
        String html = payload.get("htmlContent").asText();
        assertTrue(html.contains("&lt;script&gt; &amp; &quot;pessoa&quot;"));
        assertFalse(html.contains("<script>"));
        String escapedLink = LINK.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
        assertTrue(html.contains("href=\"" + escapedLink + "\""));
        assertTrue(html.contains(">" + escapedLink + "</a>"));
        assertTrue(html.contains("https://hipatec.app/assets/logo_hipatec_roxo.png"));
        assertTrue(html.contains("#472b7a"));
        assertTrue(html.contains("Redefinir minha senha"));
        assertTrue(html.contains("© 2025–2026 Hipatec. Todos os direitos reservados."));
        assertFalse(html.contains("Projeto iniciado em 2025"));
        assertFalse(html.contains("—"));
        assertMailgunParity(message, LINK, html);
        assertPrivate(output.getAll());
    }

    private void assertMailgunParity(SimpleMailMessage message, String link, String html) {
        response = "{\"id\":\"synthetic-queued\"}";
        new EmailService(smtp, RestClient.builder(), "mailgun", "", "", "hipatec.invalid", "synthetic-mailgun-key", baseUrl())
                .send(message, link);
        Map<String, String> form = new java.util.HashMap<>();
        for (String field : body.get().split("&")) {
            String[] pair = field.split("=", 2);
            form.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        assertEquals(html, form.get("html"));
        assertEquals(message.getText(), form.get("text"));
        assertEquals(message.getFrom(), form.get("from"));
    }

    @Test
    void preservesPasswordChangedTemplateAndBareSender() {
        var message = message();
        message.setFrom("sender@hipatec.invalid");
        message.setText("Sua senha foi alterada.\n\nTexto <privado>.");
        brevo(KEY).send(message);
        var payload = json.readTree(body.get());
        assertEquals("Hipatec", payload.at("/sender/name").asText());
        assertEquals("sender@hipatec.invalid", payload.at("/sender/email").asText());
        String html = payload.get("htmlContent").asText();
        assertTrue(html.contains("Sua senha foi alterada"));
        assertFalse(html.contains("Redefinir minha senha"));
        assertMailgunParity(message, null, html);
    }

    @ParameterizedTest
    @CsvSource({"400", "401", "403", "429", "500", "302", "200", "202"})
    void rejectsNon201WithoutLeakingOrRetrying(int code, CapturedOutput output) {
        status = code;
        response = "{\"messageId\":\"synthetic-accepted\",\"error\":\"" + KEY + " private-recipient@hipatec.invalid synthetic-private-token\"}";
        assertSafeFailure(output);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"messageId\":\"\"}", "{\"messageId\":\"   \"}", "{\"messageId\":null}", "{\"messageId\":42}", "{\"messageIds\":[\"id\"]}", "null", "invalid-json synthetic-private-key", ""})
    void rejectsMissingOrMalformedMessageId(String reply, CapturedOutput output) {
        response = reply;
        assertSafeFailure(output);
    }

    @Test
    void timesOutWithoutRetryOrSecretInException(CapturedOutput output) {
        delay = true;
        assertTimeout(java.time.Duration.ofSeconds(15), () -> assertSafeFailure(output));
    }

    private void assertSafeFailure(CapturedOutput output) {
        var error = assertThrows(MailSendException.class, () -> brevo(KEY).send(message(), LINK));
        assertNull(error.getCause());
        assertTrue(error.getFailedMessages().isEmpty());
        var trace = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(trace));
        assertPrivate(trace.toString());
        assertPrivate(output.getAll());
        assertEquals(1, requests.get());
    }

    private void assertPrivate(String text) {
        for (String value : new String[]{KEY, "private-recipient@hipatec.invalid", "synthetic-private-token", LINK}) {
            assertFalse(text.contains(value), "Dados sintéticos privados expostos");
        }
    }

    @Test
    void springInjectsBrevoRuntimeSettings() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("synthetic-runtime", Map.of(
                            "hipatec.mail.provider", "brevo",
                            "hipatec.brevo.api-key", KEY,
                            "hipatec.brevo.base-url", baseUrl())));
            context.registerBean(JavaMailSender.class, () -> smtp);
            context.register(EmailService.class);
            context.refresh();
            context.getBean(EmailService.class).send(message(), LINK);
            assertEquals("/v3/smtp/email", path.get());
            assertEquals(KEY, headers.get().getFirst("api-key"));
        }
    }

    @Test
    void preservesQuotedSenderNameAndRecipients() {
        var message = message();
        message.setFrom("\"Equipe, Hipatec\" <sender@hipatec.invalid>");
        message.setTo("first@hipatec.invalid", "second@hipatec.invalid");
        brevo(KEY).send(message);
        var payload = json.readTree(body.get());
        assertEquals("Equipe, Hipatec", payload.at("/sender/name").asText());
        assertEquals(2, payload.get("to").size());
        assertEquals("second@hipatec.invalid", payload.at("/to/1/email").asText());
    }

    @Test
    void rejectsInvalidSenderWithoutNetworkOrLeaking(CapturedOutput output) {
        var message = message();
        message.setFrom("invalid <" + KEY);
        var error = assertThrows(MailSendException.class, () -> brevo(KEY).send(message));
        assertPrivate(error.toString());
        assertNull(error.getCause());
        assertPrivate(output.getAll());
        assertEquals(0, requests.get());
    }

    @Test
    void keyAloneDoesNotEnableBrevo() {
        var localSmtp = mock(JavaMailSender.class);
        var message = message();
        new EmailService(localSmtp, RestClient.builder(), "smtp", "", "", "", "", "",
                KEY, baseUrl()).send(message);
        verify(localSmtp).send(message);
        assertEquals(0, requests.get());
    }

    @Test
    void requiresExplicitProviderAndKey() {
        assertThrows(IllegalArgumentException.class, () -> brevo(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailService(smtp, RestClient.builder(), "brevo", "", ""));
        new EmailService(smtp, "smtp", "", "", "", "", "https://api.mailgun.net/v3");
        assertEquals(0, requests.get());
    }
}
