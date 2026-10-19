package com.solucao.hipatec.controller;

import com.solucao.hipatec.model.Mentoria;
import com.solucao.hipatec.service.MentoriaService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mentorias")
public class MentoriaController {

    private final MentoriaService service;
    private final com.solucao.hipatec.config.Acesso acesso;

    public MentoriaController(MentoriaService service, com.solucao.hipatec.config.Acesso acesso) {
        this.service = service;
        this.acesso = acesso;
    }

    @GetMapping
    public List<Mentoria> listar() {
        return service.listar();
    }

    @PostMapping
    public Mentoria salvar(@RequestBody Mentoria mentoria) {
        if (mentoria.getId() != null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        mentoria.setIdMentora(acesso.atual().id());
        return service.salvar(mentoria);
    }
}