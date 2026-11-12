package com.solucao.hipatec.controller;

import com.solucao.hipatec.config.Acesso;
import com.solucao.hipatec.model.Seguimento;
import com.solucao.hipatec.repository.PerfilRepository;
import com.solucao.hipatec.repository.SeguimentoRepository;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class SeguimentoController {
    private final SeguimentoRepository seguimentos;
    private final PerfilRepository perfis;
    private final Acesso acesso;

    @GetMapping("/{id}/seguindo")
    public Map<String, Boolean> status(@PathVariable Integer id) {
        Integer atual = acesso.perfil().getId();
        return Map.of("seguindo", seguimentos.existsBySeguidorPerfilIdAndSeguidoPerfilId(atual, id));
    }

    @PostMapping("/{id}/seguir")
    public Map<String, Boolean> seguir(@PathVariable Integer id) {
        Integer atual = acesso.perfil().getId();
        validarDestino(atual, id);
        if (!seguimentos.existsBySeguidorPerfilIdAndSeguidoPerfilId(atual, id)) {
            Seguimento seguimento = new Seguimento(null, atual, id, LocalDateTime.now());
            seguimentos.save(seguimento);
        }
        return Map.of("seguindo", true);
    }

    @DeleteMapping("/{id}/seguir")
    public Map<String, Boolean> deixarDeSeguir(@PathVariable Integer id) {
        Integer atual = acesso.perfil().getId();
        seguimentos.deleteBySeguidorPerfilIdAndSeguidoPerfilId(atual, id);
        return Map.of("seguindo", false);
    }

    private void validarDestino(Integer atual, Integer destino) {
        if (atual.equals(destino)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível seguir o próprio perfil.");
        }
        if (!perfis.existsById(destino)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado.");
        }
    }
}
