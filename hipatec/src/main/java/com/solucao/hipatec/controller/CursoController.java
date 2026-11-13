package com.solucao.hipatec.controller;

import com.solucao.hipatec.model.Curso;
import com.solucao.hipatec.service.CursoService;
import com.solucao.hipatec.dto.ConteudoEntrada;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/cursos")
public class CursoController {
    private final CursoService service;
    public CursoController(CursoService service) { this.service=service; }
    @GetMapping public List<Curso> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Curso criar(@Valid @RequestBody ConteudoEntrada entrada) { return service.criar(entrada); }
}
