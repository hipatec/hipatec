package com.solucao.hipatec;

import com.solucao.hipatec.config.*;
import com.solucao.hipatec.dto.*;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.*;
import com.solucao.hipatec.service.PerfilService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerfilPrivacidadeTests {
    @Test
    void mesmoIdNumericoEmRolesDiferentesNaoEhDono() {
        var estudantes = mock(EstudanteRepository.class);
        var mentoras = mock(MentoraRepository.class);
        var perfis = mock(PerfilRepository.class);
        var service = new PerfilService(perfis);
        ReflectionTestUtils.setField(service,"posts",mock(org.springframework.beans.factory.ObjectProvider.class));
        ReflectionTestUtils.setField(service, "acesso", new Acesso(estudantes, mentoras, perfis));
        ReflectionTestUtils.setField(service, "perfilRepository", perfis);
        ReflectionTestUtils.setField(service, "estudanteRepository", estudantes);
        ReflectionTestUtils.setField(service, "mentoraRepository", mentoras);
        ReflectionTestUtils.setField(service, "seguimentos", mock(SeguimentoRepository.class));
        var p = new Perfil(); p.setId(42);
        when(perfis.buscarPorEstudante(1)).thenReturn(p);
        when(perfis.buscarPorMentora(1)).thenReturn(p);
        var e = new Estudante(); e.setEmail("aluna@example.test");
        var m = new Mentora(); m.setEmail("mentora@example.test");
        when(estudantes.findById(1)).thenReturn(java.util.Optional.of(e));
        when(mentoras.findById(1)).thenReturn(java.util.Optional.of(m));
        try {
            for (String role : new String[]{"estudantes", "mentoras"}) {
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new UsuarioSessao(1, role, "hash"), null));
                assertInstanceOf(PerfilConfiguracoesDTO.class, service.buscarPerfil(role, 1));
                String outroRole = role.equals("estudantes") ? "mentoras" : "estudantes";
                var publico = service.buscarPerfil(outroRole, 1);
                assertEquals(PerfilDTO.class, publico.getClass());
                assertFalse(new tools.jackson.databind.ObjectMapper().valueToTree(publico).has("email"));
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
}
