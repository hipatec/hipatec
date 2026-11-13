package com.solucao.hipatec;

import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.PerfilRepository;
import com.solucao.hipatec.service.*;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Exclusivo de TEST: posts/interações sintéticos e voláteis; não restaura conteúdo real. */
final class SocialLocalFixture {
    private SocialLocalFixture() {}

    static void configurar(org.springframework.context.ApplicationContext context) throws Exception {
        var posts = context.getBean(PostService.class);
        var postsLocais = new CopyOnWriteArrayList<Post>();
        var perfis = context.getBean(PerfilRepository.class);
        when(posts.listarTodos()).thenAnswer(invocation -> new ArrayList<>(postsLocais));
        when(posts.listarPorAutor(anyInt())).thenAnswer(invocation -> postsLocais.stream()
                .filter(post -> invocation.getArgument(0, Integer.class).equals(post.getAutorId()))
                .toList());
        // Fixture stateful em memória: NÃO prova durabilidade Firebase/produção.
        var likesLocais = new java.util.concurrent.ConcurrentHashMap<String, java.util.Set<Integer>>();
        var comentariosLocais = new java.util.concurrent.ConcurrentHashMap<String, CopyOnWriteArrayList<Comentario>>();
        when(posts.listarComentarios(any())).thenAnswer(i -> new ArrayList<>(comentariosLocais.getOrDefault(i.getArgument(0), new CopyOnWriteArrayList<>())));
        when(posts.interacoes(any(), org.mockito.ArgumentMatchers.nullable(Integer.class))).thenAnswer(i -> {
            var likes = likesLocais.getOrDefault(i.getArgument(0), java.util.Set.of());
            var comentarios = comentariosLocais.getOrDefault(i.getArgument(0), new CopyOnWriteArrayList<>());
            Integer perfil = i.getArgument(1);
            return new com.solucao.hipatec.dto.InteracoesDTO(likes.size(), comentarios.size(), perfil != null && likes.contains(perfil));
        });
        doAnswer(i -> { likesLocais.computeIfAbsent(i.getArgument(0), key -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(i.getArgument(1)); return null; })
                .when(posts).curtir(any(), anyInt());
        doAnswer(i -> { likesLocais.computeIfAbsent(i.getArgument(0), key -> java.util.concurrent.ConcurrentHashMap.newKeySet()).remove(i.getArgument(1)); return null; })
                .when(posts).descurtir(any(), anyInt());
        doAnswer(i -> {
            Comentario comentario = i.getArgument(1);
            comentario.setId(java.util.UUID.randomUUID().toString());
            comentario.setDataCriacao(System.currentTimeMillis());
            comentariosLocais.computeIfAbsent(i.getArgument(0), key -> new CopyOnWriteArrayList<>()).add(comentario);
            return null;
        }).when(posts).comentar(any(), any());
        doAnswer(invocation -> {
            Post post = invocation.getArgument(0, Post.class);
            post.setId("local-" + System.nanoTime());
            post.setDataCriacao(System.currentTimeMillis());
            postsLocais.add(0, post);
            perfis.incrementarPublicacoes(post.getAutorId());
            return null;
        }).when(posts).criar(any(Post.class));

        // Repost sintético por perfil; contador local não comprova user_feed/Firebase.
        var repostsLocais = new java.util.concurrent.ConcurrentHashMap<String, java.util.Set<Integer>>();
        doAnswer(i -> {
            String id = i.getArgument(0);
            var autores = repostsLocais.computeIfAbsent(id, key -> java.util.concurrent.ConcurrentHashMap.newKeySet());
            autores.add(i.getArgument(1));
            postsLocais.stream().filter(post -> id.equals(post.getId())).forEach(post -> post.setRepostsCount(autores.size()));
            return null;
        }).when(posts).repostar(any(), anyInt());
        var fixture = new Post();
        fixture.setId("fixture-social-local");
        fixture.setAutorId(perfis.findAll().stream().sorted(java.util.Comparator.comparing(p -> p.getId_mentora() == null)).findFirst().orElseThrow(() -> new IllegalStateException("Fixture social exige um perfil existente; não cria contas no banco persistente.")).getId());
        fixture.setConteudo("Fixture local de teste — publicação sintética para validar interações. Não é conteúdo real.");
        fixture.setDataCriacao(System.currentTimeMillis());
        postsLocais.add(fixture);
        org.mockito.Mockito.when(context.getBean(CloudinaryService.class).uploadFile(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Upload externo não está disponível neste ambiente de teste."));
    }
}
