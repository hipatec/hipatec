package com.solucao.hipatec.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.solucao.hipatec.service.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "${hipatec.frontend-origin:http://localhost:8100}")
public class RecuperacaoSenhaController {
    private final RecuperacaoSenhaService service;
    // ponytail: limite local a uma instância; usar limite compartilhado no proxy ao escalar a API.
    private final Map<String, Janela> tentativas = new LinkedHashMap<>();
    private record Janela(Instant inicio, int quantidade) {}

    public record Solicitacao(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record Redefinicao(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min = 8, max = 72) String senha) {}

    public RecuperacaoSenhaController(RecuperacaoSenhaService service) {
        this.service = service;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Void> entradaInvalida(MethodArgumentNotValidException error) {
        // Não incluir nem registrar os valores rejeitados de senha e token.
        int status = error.getBindingResult().hasFieldErrors("senha") ? 422 : 400;
        return ResponseEntity.status(status).build();
    }

    @PostMapping("/recuperacao-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void solicitar(@Valid @RequestBody Solicitacao dados, HttpServletRequest request) {
        limitar("solicitar:" + request.getRemoteAddr(), 5);
        try {
            // A consulta e o envio ocorrem fora da resposta HTTP, inclusive para e-mails desconhecidos.
            service.solicitar(dados.email());
        } catch (TaskRejectedException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Tente novamente mais tarde.");
        }
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinir(@Valid @RequestBody Redefinicao dados, HttpServletRequest request) {
        limitar("redefinir:" + request.getRemoteAddr(), 10);
        service.redefinir(dados.token(), dados.senha());
    }

    private synchronized void limitar(String chave, int limite) {
        var agora = Instant.now();
        var iterator = tentativas.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().inicio().plusSeconds(900).isAfter(agora)) break;
            iterator.remove();
        }
        var janela = tentativas.get(chave);
        if (janela == null) {
            if (tentativas.size() >= 10000) throw excesso();
            janela = new Janela(agora, 0);
        }
        if (janela.quantidade() >= limite) throw excesso();
        tentativas.put(chave, new Janela(janela.inicio(), janela.quantidade() + 1));
    }

    private ResponseStatusException excesso() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde 15 minutos para tentar novamente.");
    }
}
