package com.solucao.hipatec.service;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.solucao.hipatec.model.Post;
import com.solucao.hipatec.model.FeedItem;
import com.solucao.hipatec.model.Repost;
import com.solucao.hipatec.model.Comentario;
import com.solucao.hipatec.repository.PerfilRepository;

import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "hipatec.posts.enabled", havingValue = "true", matchIfMissing = true)
public class PostService {

    private final FirebaseDatabase database = FirebaseDatabase.getInstance();

    public List<Post> listarTodos() throws Exception {

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("posts");

        CompletableFuture<List<Post>> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            DataSnapshot snapshot) {

                        List<Post> posts = new ArrayList<>();

                        for (DataSnapshot child : snapshot.getChildren()) {

                            Post post = child.getValue(Post.class);

                            posts.add(post);
                        }

                        posts.sort(
                                Comparator.comparing(
                                        Post::getDataCriacao)
                                        .reversed());

                        future.complete(posts);
                    }

                    @Override
                    public void onCancelled(
                            DatabaseError error) {

                        future.completeExceptionally(
                                error.toException());
                    }
                });

        return future.get();
    }

    public List<Post> listarPorAutor(
            Integer autorId) throws Exception {

        List<Post> posts = listarTodos();

        return posts.stream()
                .filter(p -> autorId.equals(
                        p.getAutorId()))
                .toList();
    }

    @Autowired
    private PerfilRepository perfilRepository;

    @Transactional
    public void criar(Post post) {

        DatabaseReference ref = database.getReference("posts");

        String id = ref.push().getKey();

        post.setId(id);
        post.setDataCriacao(System.currentTimeMillis());

        perfilRepository.incrementarPublicacoes(
                post.getAutorId());

        ref.child(id)
                .setValueAsync(post);
    }

    public void curtir(
            String postId,
            Integer usuarioId) {

        database.getReference("likes")
                .child(postId)
                .child(usuarioId.toString())
                .setValueAsync(true);

    }

    public void descurtir(
            String postId,
            Integer usuarioId) {

        database.getReference("likes")
                .child(postId)
                .child(usuarioId.toString())
                .removeValueAsync();
    }

    public void comentar(
            String postId,
            Comentario comentario) {

        DatabaseReference ref = database.getReference("comments")
                .child(postId);

        String id = ref.push().getKey();

        comentario.setId(id);
        comentario.setDataCriacao(
                System.currentTimeMillis());

        ref.child(id).setValueAsync(comentario);
    }

    public void repostar(
            String postId,
            Integer usuarioId) {

        database.getReference("reposts")
                .child(postId)
                .child(usuarioId.toString())
                .setValueAsync(
                        new Repost(
                                usuarioId,
                                System.currentTimeMillis()));

        FeedItem feedItem = new FeedItem(
                "REPOST",
                postId,
                System.currentTimeMillis());

        database.getReference("user_feed")
                .child(usuarioId.toString())
                .child(postId)
                .setValueAsync(feedItem);
    }
}