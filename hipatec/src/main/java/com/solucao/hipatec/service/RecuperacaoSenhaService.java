package com.solucao.hipatec.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import com.solucao.hipatec.model.RecuperacaoSenha;
import com.solucao.hipatec.repository.EstudanteRepository;
import com.solucao.hipatec.repository.MentoraRepository;
import com.solucao.hipatec.repository.RecuperacaoSenhaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {
    private final EstudanteRepository estudantes;
    private final MentoraRepository mentoras;
    private final RecuperacaoSenhaRepository recuperacoes;
    private final EmailService mail;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    @Value("${hipatec.recuperacao-senha.url}")
    private String resetUrl;

    @Value("${hipatec.mail.from}")
    private String remetente;

    @Async("recuperacaoSenhaExecutor")
    @Transactional
    public void solicitar(String email) {
        var contasEstudantes = estudantes.findByEmailIgnoreCase(email.trim());
        var contasMentoras = mentoras.findByEmailIgnoreCase(email.trim());
        // Até o cadastro garantir unicidade global, não escolher uma conta ambígua.
        if (contasEstudantes.size() + contasMentoras.size() != 1) return;

        String perfil;
        Integer contaId;
        String destinatario;
        if (!contasEstudantes.isEmpty()) {
            perfil = "estudantes";
            contaId = contasEstudantes.get(0).getId();
            destinatario = contasEstudantes.get(0).getEmail();
        } else {
            perfil = "mentoras";
            contaId = contasMentoras.get(0).getId();
            destinatario = contasMentoras.get(0).getEmail();
        }

        var agora = Instant.now();
        var id = perfil + ":" + contaId;
        var recuperacao = recuperacoes.findById(id).orElseGet(RecuperacaoSenha::new);
        if (recuperacao.getCriadoEm() != null && recuperacao.getCriadoEm()
                                                        .plusSeconds(60)
                                                        .isAfter(agora)) {
                    return;
                }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        recuperacao.setId(id);
        recuperacao.setPerfil(perfil);
        recuperacao.setContaId(contaId);
        recuperacao.setTokenHash(hash(token));
        recuperacao.setCriadoEm(agora);
        recuperacao.setExpiraEm(agora.plus(30, ChronoUnit.MINUTES));
        recuperacoes.saveAndFlush(recuperacao);

        var mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatario);
        mensagem.setSubject("Hipatec — recuperação de senha");
        mensagem.setText("Recebemos uma solicitação para redefinir sua senha na Hipatec.\n\n"
                + "Abra este link, válido por 30 minutos e para um único uso:\n"
                + resetUrl + "#token=" + token
                + "\n\nSe você não solicitou a alteração, ignore este e-mail. Sua senha continua a mesma.");
        // Uma falha no provedor desfaz a troca do token, preservando o link anterior.
        mail.send(mensagem, resetUrl + "#token=" + token);
    }

    @Transactional
    public void redefinir(String token, String senha) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw linkInvalido();
        }
        if (senha == null || senha.isBlank() || senha.length() < 8 || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Senha fora dos limites permitidos.");
        }

        var recuperacao = recuperacoes.findByTokenHash(hash(token)).orElseThrow(this::linkInvalido);

        if (!recuperacao.getExpiraEm().isAfter(Instant.now())) {
            throw linkInvalido();
        }

        String senhaHash = "{bcrypt}" + encoder.encode(senha);
        String email;

        if ("estudantes".equals(recuperacao.getPerfil())) {
            var conta = estudantes.findById(recuperacao.getContaId()).orElseThrow(this::linkInvalido);
            conta.setSenha(senhaHash);
            email = conta.getEmail();
        } else {
            var conta = mentoras.findById(recuperacao.getContaId()).orElseThrow(this::linkInvalido);
            conta.setSenha(senhaHash);
            email = conta.getEmail();
        }
        // O bloqueio e a transação tornam a troca da senha e o consumo do link uma operação única.
        recuperacoes.delete(recuperacao);
        recuperacoes.flush();

        var aviso = new SimpleMailMessage();
        aviso.setFrom(remetente);
        aviso.setTo(email);
        aviso.setSubject("Hipatec — senha alterada");
        aviso.setText("Sua senha da Hipatec foi alterada. Se não foi você, procure a equipe responsável pelo projeto.");
        
        try {
            mail.send(aviso);
        } catch (MailException error) {
            // Não desfazer a alteração se apenas a notificação de confirmação falhar.
            LoggerFactory.getLogger(getClass()).warn("Não foi possível enviar o aviso de senha alterada.");
        }
    }

    private ResponseStatusException linkInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link inválido ou expirado.");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponível", error);
        }
    }
}
