package com.solucao.hipatec.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solucao.hipatec.service.PostService;
import com.solucao.hipatec.model.Post;
import com.solucao.hipatec.dto.ComentarioDTO;
import com.solucao.hipatec.model.Comentario;

// import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService service;
    private final com.solucao.hipatec.config.Acesso acesso;

    @PostMapping
    public ResponseEntity<Void> criar(
            @RequestBody Post post) {

        var perfil = acesso.perfil();
        post.setAutorId(perfil.getId());
        post.setAutorFoto(perfil.getPfp());

        service.criar(post);

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<Post>> listarTodos() throws Exception {

        return ResponseEntity.ok(
                service.listarTodos());
    }

    @GetMapping("/autor/{autorId}")
    public ResponseEntity<List<Post>> listarPorAutor(
            @PathVariable Integer autorId)
            throws Exception {

        return ResponseEntity.ok(
                service.listarPorAutor(
                        autorId));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> curtir(
            @PathVariable String id,
            @RequestParam Integer usuarioId) {

        service.curtir(id, acesso.perfil().getId());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> descurtir(
            @PathVariable String id,
            @RequestParam Integer usuarioId) {

        service.descurtir(id, acesso.perfil().getId());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<Void> comentar(
            @PathVariable String id,
            @RequestBody ComentarioDTO dto) {

        Comentario comentario = new Comentario();

        var perfil = acesso.perfil();
        comentario.setAutorId(perfil.getId());
        comentario.setAutorNome(perfil.getUsuario());
        comentario.setAutorFoto(perfil.getPfp());
        comentario.setTexto(dto.getTexto());

        service.comentar(id, comentario);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/repost")
    public ResponseEntity<Void> repostar(
            @PathVariable String id,
            @RequestParam Integer usuarioId) {

        service.repostar(id, acesso.perfil().getId());

        return ResponseEntity.ok().build();
    }
}