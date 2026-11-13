package com.solucao.hipatec.controller;

import com.solucao.hipatec.model.Mentoria;
import com.solucao.hipatec.service.MentoriaService;
import com.solucao.hipatec.dto.ConteudoEntrada;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/mentorias")
public class MentoriaController {
    private final MentoriaService service;
    public MentoriaController(MentoriaService service) { this.service=service; }
    @GetMapping public List<Mentoria> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Mentoria criar(@Valid @RequestBody ConteudoEntrada entrada) { return service.criar(entrada); }
}
