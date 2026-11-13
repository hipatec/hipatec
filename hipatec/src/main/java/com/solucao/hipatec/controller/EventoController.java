package com.solucao.hipatec.controller;

import com.solucao.hipatec.dto.*;
import com.solucao.hipatec.service.EventoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/eventos")
public class EventoController {
    private final EventoService service;
    public EventoController(EventoService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EventoDTO criar(@RequestBody EventoEntrada entrada) { return service.criar(entrada); }
    @GetMapping
    public List<EventoDTO> listar(@RequestParam String de, @RequestParam String ate) { return service.listar(de, ate); }
    @GetMapping("/{id}")
    public EventoDTO recuperar(@PathVariable Long id) { return service.recuperar(id); }
    @ExceptionHandler(EventoService.EntradaInvalida.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalido(EventoService.EntradaInvalida e) {
        return Map.of("mensagem", "Revise os dados do evento.", "campos", Map.of(e.campo, e.getMessage()));
    }
}
