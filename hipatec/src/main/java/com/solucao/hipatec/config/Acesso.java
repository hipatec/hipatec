package com.solucao.hipatec.config;

import com.solucao.hipatec.model.Perfil;
import com.solucao.hipatec.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component("acesso")
public class Acesso {
    private final EstudanteRepository estudantes;
    private final MentoraRepository mentoras;
    private final PerfilRepository perfis;

    public Acesso(EstudanteRepository estudantes, MentoraRepository mentoras, PerfilRepository perfis) {
        this.estudantes = estudantes; this.mentoras = mentoras; this.perfis = perfis;
    }

    public UsuarioSessao atual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioSessao usuario))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return usuario;
    }

    public boolean proprio(String role, Integer id) {
        var usuario = atual();
        return usuario.role().equals(role) && usuario.id().equals(id);
    }

    public boolean valida(UsuarioSessao usuario) {
        if (usuario.role().equals("estudantes"))
            return estudantes.findById(usuario.id()).map(c -> usuario.senhaHash().equals(c.getSenha())).orElse(false);
        return mentoras.findById(usuario.id()).map(c -> Boolean.TRUE.equals(c.getAprovada())
                && usuario.senhaHash().equals(c.getSenha())).orElse(false);
    }

    public Perfil perfil() {
        var usuario = atual();
        var perfil = usuario.role().equals("estudantes")
                ? perfis.buscarPorEstudante(usuario.id()) : perfis.buscarPorMentora(usuario.id());
        if (perfil == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Perfil indisponível.");
        return perfil;
    }
}
