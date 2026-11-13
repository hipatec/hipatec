package com.solucao.hipatec.controller;
import com.solucao.hipatec.service.DenunciaService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/posts/{postId}/denuncias")
public class DenunciaController {
    private final DenunciaService service;
    public DenunciaController(DenunciaService service) { this.service=service; }
    public record Entrada(String motivo,String comentarioId) {}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Object> registrar(@PathVariable String postId,@RequestBody Entrada entrada) throws Exception {
        return Map.of("protocolo",service.registrar(postId,entrada.comentarioId(),entrada.motivo()),"status","PENDENTE");
    }
}
