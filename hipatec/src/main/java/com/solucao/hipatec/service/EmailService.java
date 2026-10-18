package com.solucao.hipatec.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailService {
    private final JavaMailSender smtp;
    private final boolean oneSignal;
    private final String appId;
    private final RestClient client;

    @Autowired
    public EmailService(JavaMailSender smtp,
            @Value("${hipatec.mail.provider:smtp}") String provider,
            @Value("${hipatec.onesignal.app-id:}") String appId,
            @Value("${hipatec.onesignal.api-key:}") String apiKey) {
        this(smtp, RestClient.builder(), provider, appId, apiKey);
    }

    public EmailService(JavaMailSender smtp, RestClient.Builder builder,
            String provider, String appId, String apiKey) {
        Assert.isTrue(List.of("smtp", "onesignal").contains(provider),
                "HIPATEC_MAIL_PROVIDER deve ser smtp ou onesignal.");
        this.smtp = smtp;
        this.oneSignal = "onesignal".equals(provider);
        this.appId = appId;
        if (oneSignal) {
            Assert.hasText(appId, "Configure ONESIGNAL_APP_ID.");
            Assert.hasText(apiKey, "Configure ONESIGNAL_API_KEY.");
            UUID.fromString(appId);
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder.requestFactory(factory)
                .baseUrl("https://api.onesignal.com")
                .defaultHeader("Authorization", "Key " + apiKey).build();
    }

    public void send(SimpleMailMessage message) {
        send(message, null);
    }

    public void send(SimpleMailMessage message, String link) {
        if (!oneSignal) {
            smtp.send(message);
            return;
        }
        String html = HtmlUtils.htmlEscape(message.getText(), "UTF-8").replace("\n", "<br>");
        if (link != null) {
            String escapedLink = HtmlUtils.htmlEscape(link, "UTF-8");
            html = html.replace(escapedLink, "<a href=\"" + escapedLink + "\">Redefinir senha</a>");
        }
        var body = Map.of(
                "app_id", appId,
                "email_to", List.of(message.getTo()),
                "email_from_address", message.getFrom(),
                "email_subject", message.getSubject(),
                "email_body", "<p>" + html + "</p>",
                "include_unsubscribed", true,
                "disable_email_click_tracking", true);
        try {
            var response = client.post().uri("/notifications?c=email")
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
            // HTTP 200 sem id também significa que o OneSignal não aceitou o envio.
            if (response == null || !(response.get("id") instanceof String id) || id.isBlank()) {
                throw new MailSendException("OneSignal não aceitou o envio.");
            }
        } catch (RestClientException error) {
            // A resposta remota pode conter endereço, token ou chave: não propagar seu corpo.
            throw new MailSendException("Falha ao enviar e-mail pelo OneSignal.");
        }
    }
}
