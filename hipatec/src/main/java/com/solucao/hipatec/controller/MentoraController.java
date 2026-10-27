package com.solucao.hipatec.controller;

import com.solucao.hipatec.model.Mentora;
import com.solucao.hipatec.service.MentoraService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mentoras")
public class MentoraController {

    private final MentoraService service;

    public MentoraController(MentoraService service) {
        this.service = service;
    }

    @GetMapping
    public List<Mentora> listar() {
        return service.listar();
    }

     // SALVAR MENTORA
    @PostMapping
    public Mentora salvar(@RequestBody Mentora mentora) {
        return service.salvar(mentora);
    }

    @GetMapping("/{id}")
    public Mentora buscarPorId(@PathVariable Integer id) {    
        return service.buscarPorId(id);
    }

    // LOGIN
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public void cadastroConflitante() {}
}
