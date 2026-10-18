package com.solucao.hipatec.controller;

import com.solucao.hipatec.model.Perfil;
import com.solucao.hipatec.service.PerfilService;
import com.solucao.hipatec.dto.PerfilDTO;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@CrossOrigin(origins = "http://localhost:8100")
@RestController
@RequestMapping("/perfil")
public class PerfilController {

    private final PerfilService service;

    public PerfilController(PerfilService service) {
        this.service = service;
    }

    @GetMapping
    public List<Perfil> listar() {
        return service.listar();
    }

    @PostMapping
    public Perfil salvar(@RequestBody Perfil perfil) {
        return service.salvar(perfil);
    }

    @GetMapping("/{role}/{id}")
    public PerfilDTO getPerfil(
            @PathVariable String role,
            @PathVariable Integer id) {

        return service.buscarPerfil(role, id);
    }

    @PutMapping("/{role}/{id}")
    public PerfilDTO atualizarPerfil(

            @PathVariable String role,

            @PathVariable Integer id,

            @RequestParam String nome,

            @RequestParam String usuario,

            @RequestParam String biografia,

            @RequestParam(required = false) MultipartFile pfp,

            @RequestParam(required = false) MultipartFile background

    ) {

        return service.atualizarPerfil(
                role,
                id,
                nome,
                usuario,
                biografia,
                pfp,
                background);
    }
}
