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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailService {
    private final JavaMailSender smtp;
    private final String provider;
    private final String appId;
    private final RestClient client;

    @Autowired
    public EmailService(JavaMailSender smtp,
            @Value("${hipatec.mail.provider:smtp}") String provider,
            @Value("${hipatec.onesignal.app-id:}") String appId,
            @Value("${hipatec.onesignal.api-key:}") String oneSignalApiKey,
            @Value("${hipatec.mailgun.domain:}") String mailgunDomain,
            @Value("${hipatec.mailgun.api-key:}") String mailgunApiKey,
            @Value("${hipatec.mailgun.base-url:https://api.mailgun.net/v3}") String mailgunBaseUrl) {
        this(smtp, RestClient.builder(), provider, appId, oneSignalApiKey,
                mailgunDomain, mailgunApiKey, mailgunBaseUrl);
    }

    public EmailService(JavaMailSender smtp, RestClient.Builder builder,
            String provider, String appId, String apiKey) {
        this(smtp, builder, provider, appId, apiKey, "", "", "https://api.mailgun.net/v3");
    }

    public EmailService(JavaMailSender smtp, RestClient.Builder builder,
            String provider, String appId, String oneSignalApiKey,
            String mailgunDomain, String mailgunApiKey, String mailgunBaseUrl) {
        Assert.isTrue(List.of("smtp", "onesignal", "mailgun").contains(provider),
                "HIPATEC_MAIL_PROVIDER deve ser smtp, mailgun ou onesignal.");
        this.smtp = smtp;
        this.provider = provider;
        this.appId = appId;

        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        var http = builder.requestFactory(factory);
        if ("onesignal".equals(provider)) {
            Assert.hasText(appId, "Configure ONESIGNAL_APP_ID.");
            Assert.hasText(oneSignalApiKey, "Configure ONESIGNAL_API_KEY.");
            UUID.fromString(appId);
            this.client = http.baseUrl("https://api.onesignal.com")
                    .defaultHeader("Authorization", "Key " + oneSignalApiKey).build();
        } else if ("mailgun".equals(provider)) {
            Assert.hasText(mailgunDomain, "Configure MAILGUN_DOMAIN.");
            Assert.hasText(mailgunApiKey, "Configure MAILGUN_API_KEY.");
            this.client = http.baseUrl(mailgunBaseUrl + "/" + mailgunDomain)
                    .defaultHeaders(headers -> headers.setBasicAuth("api", mailgunApiKey))
                    .build();
        } else {
            this.client = null;
        }
    }

    public void send(SimpleMailMessage message) {
        send(message, null);
    }

    public void send(SimpleMailMessage message, String link) {
        if ("smtp".equals(provider)) {
            smtp.send(message);
            return;
        }
        if ("mailgun".equals(provider)) {
            sendMailgun(message, link);
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

    private void sendMailgun(SimpleMailMessage message, String link) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("from", message.getFrom());
        for (String recipient : message.getTo()) {
            form.add("to", recipient);
        }
        form.add("subject", message.getSubject());
        form.add("text", message.getText());
        form.add("html", renderMailgunHtml(message, link));
        try {
            var response = client.post().uri("/messages")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            if (response == null || !(response.get("id") instanceof String id) || id.isBlank()) {
                throw new MailSendException("Mailgun não aceitou o envio.");
            }
        } catch (RestClientException error) {
            throw new MailSendException("Falha ao enviar e-mail pelo Mailgun.");
        }
    }

    private String renderMailgunHtml(SimpleMailMessage message, String link) {
        boolean recovery = link != null && !link.isBlank();
        String title = recovery ? "Recupere o acesso à sua conta" : "Sua senha foi alterada";
        String intro = recovery
                ? "Recebemos uma solicitação para redefinir sua senha na Hipatec."
                : "Sua senha da Hipatec foi alterada com sucesso.";
        String escapedLink = recovery ? HtmlUtils.htmlEscape(link, "UTF-8") : "";
        StringBuilder paragraphs = new StringBuilder();
        for (String paragraph : message.getText().split("\\n\\s*\\n")) {
            String escaped = HtmlUtils.htmlEscape(paragraph.trim(), "UTF-8").replace("\n", "<br>");
            if (!escaped.isBlank() && (!recovery || !escaped.equals(escapedLink))) {
                paragraphs.append("<p style=\"margin:0 0 16px;font-size:16px;line-height:26px;color:#3f3b46;\">")
                        .append(escaped).append("</p>");
            }
        }
        String action = recovery
                ? "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px auto 18px\"><tr><td bgcolor=\"#472b7a\" style=\"border-radius:8px\"><a href=\""
                        + escapedLink
                        + "\" style=\"display:inline-block;padding:14px 24px;color:#ffffff;text-decoration:none;font-weight:bold;font-size:16px\">Redefinir minha senha</a></td></tr></table>"
                + "<p style=\"margin:0 0 16px;font-size:13px;line-height:20px;color:#6e6878;word-break:break-word\">Se o botão não abrir, copie e cole este endereço no navegador:<br><a href=\""
                        + escapedLink + "\" style=\"color:#472b7a\">" + escapedLink + "</a></p>"
                : "<p style=\"margin:0 0 16px;font-size:16px;line-height:26px;color:#3f3b46\">Se você não fez essa alteração, entre em contato com a equipe da Hipatec.</p>";
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>body{margin:0}a{overflow-wrap:anywhere}@media(max-width:600px){.pad{padding:24px 20px!important}.logo{width:150px!important}h1{font-size:26px!important;line-height:34px!important}}</style></head>"
                + "<body style=\"margin:0;background:#f7f5fb;color:#3f3b46;font-family:Arial,Helvetica,sans-serif\"><div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all\">"
                + HtmlUtils.htmlEscape(intro, "UTF-8")
                + "</div><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#ffffff;border:1px solid #e8e2ef;border-radius:14px\"><tr><td class=\"pad\" style=\"padding:30px 32px 26px\"><p style=\"margin:0 0 28px;text-align:center\"><img class=\"logo\" src=\"https://hipatec.app/assets/logo_hipatec_roxo.png\" width=\"170\" alt=\"Hipatec\" style=\"display:inline-block;width:170px;height:auto\"></p><h1 style=\"margin:0 0 18px;color:#472b7a;font-size:30px;line-height:38px;text-align:center\">"
                + title + "</h1><p style=\"margin:0 0 20px;font-size:16px;line-height:26px;color:#3f3b46\">"
                + HtmlUtils.htmlEscape(intro, "UTF-8") + "</p>"
                + paragraphs + action
                + "<hr style=\"border:0;border-top:1px solid #e8e2ef;margin:28px 0 18px\"><p style=\"margin:0;color:#777080;font-size:13px;line-height:20px;text-align:center\">Equipe Hipatec<br>Projeto iniciado em 2025</p></td></tr></table></td></tr></table></body></html>";
    }
}
