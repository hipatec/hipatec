package com.solucao.hipatec.controller;

import java.time.Instant;
import java.util.*;
import com.solucao.hipatec.config.*;
import com.solucao.hipatec.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class SessaoController {
    private final EstudanteService estudantes;
    private final MentoraService mentoras;
    private final SecurityContextRepository repository;
    private final Acesso acesso;
    // shortcut: limite em uma instância; usar contador compartilhado ao escalar a API.
    private final Map<String, Janela> tentativas = new LinkedHashMap<>();
    private record Janela(Instant inicio, int quantidade) {}
    public record Login(@Pattern(regexp="estudantes|mentoras") @NotNull String role,
            @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String senha) {}
    public record Conta(Integer id, String role) {}

    public SessaoController(EstudanteService estudantes, MentoraService mentoras,
            SecurityContextRepository repository, Acesso acesso) {
        this.estudantes = estudantes; this.mentoras = mentoras; this.repository = repository; this.acesso = acesso;
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void entradaInvalida() {
        // Não registrar nem devolver os valores rejeitados de credenciais.
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrf) {
        return Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName());
    }

    @GetMapping("/session")
    public Conta sessao() {
        var usuario = acesso.atual();
        return new Conta(usuario.id(), usuario.role());
    }

    @PostMapping("/login")
    public Conta login(@Valid @RequestBody Login dados, HttpServletRequest request, HttpServletResponse response) {
        limitar(request.getRemoteAddr());
        var anterior = request.getSession(false);
        if (anterior != null) anterior.invalidate();
        SecurityContextHolder.clearContext();
        Integer id;
        String hash;
        if (dados.role().equals("estudantes")) {
            id = estudantes.login(dados.email(), dados.senha());
            hash = estudantes.buscarPorId(id).getSenha();
        } else {
            id = mentoras.login(dados.email(), dados.senha());
            var mentora = mentoras.buscarPorId(id);
            if (!Boolean.TRUE.equals(mentora.getAprovada()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro aguardando aprovação da equipe.");
            hash = mentora.getSenha();
        }
        var usuario = new UsuarioSessao(id, dados.role(), hash);
        var auth = UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                List.of(new SimpleGrantedAuthority(dados.role().equals("mentoras") ? "ROLE_MENTORA" : "ROLE_ESTUDANTE")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        return new Conta(id, dados.role());
    }

    private synchronized void limitar(String ip) {
        var agora = Instant.now();
        tentativas.values().removeIf(j -> j.inicio().plusSeconds(900).isBefore(agora));
        var janela = tentativas.get(ip);
        if (janela == null) {
            if (tentativas.size() >= 10000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            janela = new Janela(agora, 0);
        }
        if (janela.quantidade() >= 20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        tentativas.put(ip, new Janela(janela.inicio(), janela.quantidade() + 1));
    }
}
