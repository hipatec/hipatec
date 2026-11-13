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
    private final com.solucao.hipatec.service.CloudinaryService imagens;
    private final com.solucao.hipatec.config.Acesso acesso;

    @PostMapping(consumes="application/json")
    public ResponseEntity<Void> criar(
            @RequestBody Post post) throws Exception {

        post.setImagemUrl(null); post.setImagemHash(null);
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

    @GetMapping("/{id}/interacoes")
    public com.solucao.hipatec.dto.InteracoesDTO interacoes(@PathVariable String id) throws Exception {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Integer perfilId = auth != null && auth.getPrincipal() instanceof com.solucao.hipatec.config.UsuarioSessao
                ? acesso.perfil().getId() : null;
        return service.interacoes(id, perfilId);
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> curtir(
            @PathVariable String id,
            @RequestParam(required = false) Integer usuarioId) throws Exception {

        service.curtir(id, acesso.perfil().getId());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> descurtir(
            @PathVariable String id,
            @RequestParam(required = false) Integer usuarioId) throws Exception {

        service.descurtir(id, acesso.perfil().getId());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<Comentario>> listarComentarios(
            @PathVariable String id) throws Exception {
        return ResponseEntity.ok(service.listarComentarios(id));
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<Void> comentar(
            @PathVariable String id,
            @RequestBody ComentarioDTO dto) throws Exception {

        if (dto.getTexto() == null || dto.getTexto().isBlank() || dto.getTexto().length() > 500)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Comentário deve ter entre 1 e 500 caracteres.");
        Comentario comentario = new Comentario();

        var perfil = acesso.perfil();
        comentario.setAutorId(perfil.getId());
        comentario.setAutorNome(perfil.getUsuario());
        comentario.setAutorFoto(perfil.getPfp());
        comentario.setTexto(dto.getTexto());
        comentario.setRequisicaoId(dto.getRequisicaoId());

        service.comentar(id, comentario);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/repost")
    public ResponseEntity<Void> repostar(
            @PathVariable String id,
            @RequestParam(required = false) Integer usuarioId) throws Exception {

        service.repostar(id, acesso.perfil().getId());

        return ResponseEntity.ok().build();
    }

    @PostMapping(consumes="multipart/form-data")
    public ResponseEntity<Void> criarComImagem(@RequestParam String conteudo, @RequestParam String requisicaoId,
            @RequestParam org.springframework.web.multipart.MultipartFile imagem) throws Exception {
        var perfil=acesso.perfil();
        if(conteudo.isBlank() || conteudo.length()>5000 || !requisicaoId.matches("[0-9a-f-]{36}"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        var post=new Post(); post.setConteudo(conteudo); post.setRequisicaoId(requisicaoId);
        post.setAutorId(perfil.getId()); post.setAutorFoto(perfil.getPfp());
        if(imagem.getSize()>5*1024*1024) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        post.setImagemHash(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(imagem.getBytes())));
        try {
            var anterior=service.recuperar(perfil.getId()+"-"+requisicaoId);
            if(java.util.Objects.equals(anterior.getImagemHash(),post.getImagemHash()) && java.util.Objects.equals(anterior.getConteudo(),conteudo.strip())) return ResponseEntity.ok().build();
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        } catch(org.springframework.web.server.ResponseStatusException e) { if(e.getStatusCode().value()!=404) throw e; }
        post.setImagemUrl(imagens.uploadFile(imagem)); service.criar(post);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/republicados/{perfilId}")
    public List<Post> republicados(@PathVariable Integer perfilId) throws Exception { return service.republicados(perfilId); }
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public void editar(@PathVariable String id,@RequestBody Post entrada) throws Exception { service.editar(id,acesso.perfil().getId(),entrada.getConteudo(),entrada.getVersao()); }
    @DeleteMapping("/{id}")
    public void excluir(@PathVariable String id) throws Exception { service.excluir(id,acesso.perfil().getId()); }
    @DeleteMapping("/{id}/repost")
    public void removerRepost(@PathVariable String id) throws Exception { service.removerRepost(id,acesso.perfil().getId()); }
    @PostMapping("/{id}/repost-comentario")
    public void comentarRepost(@PathVariable String id,@RequestBody Post entrada) throws Exception {
        var perfil=acesso.perfil(); entrada.setAutorId(perfil.getId()); entrada.setAutorFoto(perfil.getPfp());
        service.comentarRepost(id,entrada);
    }
}
