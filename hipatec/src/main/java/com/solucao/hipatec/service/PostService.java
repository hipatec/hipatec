package com.solucao.hipatec.service;

import com.google.firebase.database.*;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.dto.InteracoesDTO;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name="hipatec.posts.enabled", havingValue="true", matchIfMissing=true)
public class PostService {
    private final FirebaseDatabase database = FirebaseDatabase.getInstance();
    private static ResponseStatusException erro(HttpStatus status, String mensagem) { return new ResponseStatusException(status,mensagem); }
    private static String chave(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,150}")) throw erro(HttpStatus.BAD_REQUEST,"Identificador inválido.");
        return id;
    }
    private DataSnapshot ler(DatabaseReference ref) throws Exception {
        var future=new CompletableFuture<DataSnapshot>();
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(DataSnapshot data) { future.complete(data); }
            public void onCancelled(DatabaseError error) { future.completeExceptionally(error.toException()); }
        });
        return future.get(15,TimeUnit.SECONDS);
    }
    // Transação em posts (nó existente): mantém exclusão e interações atômicas.
    // Interações antigas são incorporadas uma vez; rollout exige interromper escritores da versão anterior.
    private void mutar(String id, Consumer<MutableData> operacao) throws Exception {
        var root=database.getReference("posts");
        var legados=new HashMap<String,Object>();
        if(id!=null) for(var ramo:List.of("likes","comments","reposts"))
            legados.put(ramo,ler(database.getReference(ramo).child(chave(id))).getValue());
        var carregado=new CompletableFuture<DataSnapshot>();
        ValueEventListener listener=new ValueEventListener() {
            public void onDataChange(DataSnapshot data) { carregado.complete(data); }
            public void onCancelled(DatabaseError error) { carregado.completeExceptionally(error.toException()); }
        };
        root.addValueEventListener(listener);
        try { carregado.get(15,TimeUnit.SECONDS); }
        catch(Exception e) { root.removeEventListener(listener); throw e; }
        var future=new CompletableFuture<Void>();
        var falha=new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        root.runTransaction(new Transaction.Handler() {
            public Transaction.Result doTransaction(MutableData data) {
                try {
                    falha.set(null);
                    if(id!=null) {
                        var post=data.child(id);
                        if(post.getValue(Post.class)!=null && !Boolean.TRUE.equals(post.child("interacoesMigradas").getValue(Boolean.class))) {
                            legados.forEach((ramo,valor)->post.child(ramo).setValue(valor));
                            post.child("interacoesMigradas").setValue(true);
                        }
                    }
                    operacao.accept(data); return Transaction.success(data);
                }
                catch (RuntimeException e) { falha.set(e); return Transaction.abort(); }
            }
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot data) {
                if (error != null) future.completeExceptionally(error.toException());
                else if (falha.get()!=null) future.completeExceptionally(falha.get());
                else if (!committed) future.completeExceptionally(new IllegalStateException("Gravação não confirmada."));
                else future.complete(null);
            }
        },false);
        try { future.get(15,TimeUnit.SECONDS); }
        catch (ExecutionException e) { if (e.getCause() instanceof ResponseStatusException status) throw status; throw e; }
        finally { root.removeEventListener(listener); }
    }
    private Post exigir(MutableData root,String id) {
        var post=root.child(chave(id)).getValue(Post.class);
        if (post==null || Boolean.TRUE.equals(post.getExcluido())) throw erro(HttpStatus.NOT_FOUND,"Post não encontrado.");
        return post;
    }
    private void dono(Post post,Integer perfil) {
        if (!Objects.equals(post.getAutorId(),perfil)) throw erro(HttpStatus.FORBIDDEN,"Somente a autora pode alterar esta publicação.");
    }
    public Post recuperar(String id) throws Exception {
        var post=ler(database.getReference("posts").child(chave(id))).getValue(Post.class);
        if (post==null || Boolean.TRUE.equals(post.getExcluido())) throw erro(HttpStatus.NOT_FOUND,"Post não encontrado.");
        return post;
    }
    public List<Post> listarTodos() throws Exception {
        var data=ler(database.getReference("posts"));
        var posts=new ArrayList<Post>();
        for (var child:data.getChildren()) { var post=child.getValue(Post.class); if(post!=null && !Boolean.TRUE.equals(post.getExcluido())) posts.add(post); }
        var originais=new HashMap<String,Post>();
        posts.forEach(post -> { if(post.getRepostOriginalId()==null) originais.put(post.getId(),post); });
        posts.forEach(post -> {
            if(post.getRepostOriginalId()!=null) {
                post.setRepostOriginal(originais.get(post.getRepostOriginalId()));
                post.setOriginalIndisponivel(post.getRepostOriginal()==null);
            }
        });
        posts.sort(Comparator.comparing(Post::getDataCriacao,Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return posts;
    }
    public List<Post> listarPorAutor(Integer id) throws Exception { return listarTodos().stream().filter(p->id.equals(p.getAutorId())).toList(); }
    public List<Post> republicados(Integer perfil) throws Exception {
        var resultado=new ArrayList<Post>();
        for(var post:listarTodos()) if(post.getRepostOriginalId()==null && interacoes(post.getId(),perfil).repostadoUsuario()) resultado.add(post);
        return resultado;
    }
    private String texto(String texto,int limite) {
        if(texto==null || texto.isBlank() || texto.length()>limite) throw erro(HttpStatus.BAD_REQUEST,"Conteúdo vazio ou acima do limite.");
        return texto.strip();
    }
    private String requisicao(String valor) {
        if(valor==null) return UUID.randomUUID().toString();
        if(!valor.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw erro(HttpStatus.BAD_REQUEST,"Identificador de envio inválido.");
        return valor;
    }
    @org.springframework.transaction.annotation.Transactional
    public void criar(Post entrada) throws Exception {
        var post=new Post();
        post.setId(entrada.getAutorId()+"-"+requisicao(entrada.getRequisicaoId()));
        post.setAutorId(entrada.getAutorId()); post.setAutorFoto(entrada.getAutorFoto());
        post.setConteudo(texto(entrada.getConteudo(),5000)); post.setImagemUrl(entrada.getImagemUrl()); post.setImagemHash(entrada.getImagemHash());
        post.setDataCriacao(System.currentTimeMillis());
        mutar(null, root -> {
            var alvo=root.child(post.getId()); var anterior=alvo.getValue(Post.class);
            if(anterior!=null) {
                if(Boolean.TRUE.equals(anterior.getExcluido())) throw erro(HttpStatus.CONFLICT,"Publicação excluída.");
                if(anterior.getRepostOriginalId()!=null || !Objects.equals(anterior.getConteudo(),post.getConteudo()) || !Objects.equals(anterior.getImagemHash(),post.getImagemHash())) throw erro(HttpStatus.CONFLICT,"Envio já gravado com outros dados.");
            } else if(Boolean.TRUE.equals(alvo.child("excluido").getValue(Boolean.class))) throw erro(HttpStatus.CONFLICT,"Esta publicação foi excluída.");
            else alvo.setValue(post);
        });
        entrada.setId(post.getId());
    }
    public void editar(String id,Integer perfil,String conteudo,Long versao) throws Exception {
        var texto=texto(conteudo,5000);
        mutar(id, root -> {
            var post=exigir(root,id); dono(post,perfil);
            if(Objects.equals(post.getConteudo(),texto)) return;
            long atual=post.getVersao()==null ? 0L : post.getVersao();
            if(versao==null || versao!=atual) throw erro(HttpStatus.CONFLICT,"Publicação alterada. Atualize antes de editar.");
            root.child(id).child("conteudo").setValue(texto); root.child(id).child("versao").setValue(atual+1);
        });
    }
    public void excluir(String id,Integer perfil) throws Exception {
        mutar(id, root -> {
            var post=root.child(chave(id)).getValue(Post.class);
            if(post==null) throw erro(HttpStatus.NOT_FOUND,"Post não encontrado.");
            dono(post,perfil);
            root.child(id).setValue(Map.of("id",id,"autorId",perfil,"excluido",true,"interacoesMigradas",true));
        });
    }
    public InteracoesDTO interacoes(String id,Integer perfil) throws Exception {
        chave(id);
        var data=ler(database.getReference("posts").child(id));
        var post=data.getValue(Post.class);
        if(post==null || Boolean.TRUE.equals(post.getExcluido())) throw erro(HttpStatus.NOT_FOUND,"Post não encontrado.");
        boolean migrado=Boolean.TRUE.equals(data.child("interacoesMigradas").getValue(Boolean.class));
        var likes=migrado ? data.child("likes") : ler(database.getReference("likes").child(id));
        var comments=migrado ? data.child("comments") : ler(database.getReference("comments").child(id));
        var reposts=migrado ? data.child("reposts") : ler(database.getReference("reposts").child(id));
        return new InteracoesDTO(likes.getChildrenCount(),comments.getChildrenCount(),perfil!=null && Boolean.TRUE.equals(likes.child(perfil.toString()).getValue(Boolean.class)),
            reposts.getChildrenCount()+listarTodos().stream().filter(p->id.equals(p.getRepostOriginalId())).count(),perfil!=null && reposts.hasChild(perfil.toString()));
    }
    public void curtir(String id,Integer perfil) throws Exception { mutar(id, root -> { exigir(root,id); root.child(id).child("likes").child(perfil.toString()).setValue(true); }); }
    public void descurtir(String id,Integer perfil) throws Exception { mutar(id, root -> { exigir(root,id); root.child(id).child("likes").child(perfil.toString()).setValue(null); }); }
    public void comentar(String id,Comentario entrada) throws Exception {
        entrada.setTexto(texto(entrada.getTexto(),500)); entrada.setId(entrada.getAutorId()+"-"+requisicao(entrada.getRequisicaoId()));
        entrada.setDataCriacao(System.currentTimeMillis()); entrada.setRequisicaoId(null);
        mutar(id, root -> {
            exigir(root,id); var alvo=root.child(id).child("comments").child(entrada.getId()); var anterior=alvo.getValue(Comentario.class);
            if(anterior!=null) { if(!Objects.equals(anterior.getTexto(),entrada.getTexto())) throw erro(HttpStatus.CONFLICT,"Comentário já enviado com outro texto."); }
            else alvo.setValue(entrada);
        });
    }
    public List<Comentario> listarComentarios(String id) throws Exception {
        recuperar(id); var post=ler(database.getReference("posts").child(chave(id)));
        var data=Boolean.TRUE.equals(post.child("interacoesMigradas").getValue(Boolean.class)) ? post.child("comments") : ler(database.getReference("comments").child(id));
        var comentarios=new ArrayList<Comentario>();
        for(var child:data.getChildren()) { var c=child.getValue(Comentario.class); if(c!=null) comentarios.add(c); }
        comentarios.sort(Comparator.comparing(Comentario::getDataCriacao,Comparator.nullsLast(Comparator.naturalOrder())));
        return comentarios;
    }
    public void repostar(String id,Integer perfil) throws Exception {
        long agora=System.currentTimeMillis();
        mutar(id, root -> { var original=exigir(root,id); if(original.getRepostOriginalId()!=null) throw erro(HttpStatus.BAD_REQUEST,"Use a publicação original.");
            if(!root.child(id).child("reposts").hasChild(perfil.toString())) {
                root.child(id).child("reposts").child(perfil.toString()).setValue(new Repost(perfil,agora));

            }
        });
    }
    public void removerRepost(String id,Integer perfil) throws Exception {
        mutar(id, root -> { exigir(root,id); root.child(id).child("reposts").child(perfil.toString()).setValue(null); });
    }
    public void comentarRepost(String id,Post entrada) throws Exception {
        var novo=new Post(); novo.setId(entrada.getAutorId()+"-"+requisicao(entrada.getRequisicaoId()));
        novo.setAutorId(entrada.getAutorId()); novo.setAutorFoto(entrada.getAutorFoto()); novo.setRepostOwnerId(entrada.getAutorId());
        novo.setConteudo(texto(entrada.getConteudo(),500)); novo.setRepostOriginalId(chave(id)); novo.setRepostComComentario(true); novo.setDataCriacao(System.currentTimeMillis());
        mutar(id, root -> {
            var original=exigir(root,id); if(original.getRepostOriginalId()!=null) throw erro(HttpStatus.BAD_REQUEST,"Use a publicação original.");
            var alvo=root.child(novo.getId()); var anterior=alvo.getValue(Post.class);
            if(anterior!=null) { if(Boolean.TRUE.equals(anterior.getExcluido())) throw erro(HttpStatus.CONFLICT,"Republicação excluída."); if(!Objects.equals(anterior.getConteudo(),novo.getConteudo()) || !Objects.equals(anterior.getRepostOriginalId(),id)) throw erro(HttpStatus.CONFLICT,"Envio já gravado com outros dados."); }
            else if(Boolean.TRUE.equals(alvo.child("excluido").getValue(Boolean.class))) throw erro(HttpStatus.CONFLICT,"Republicação excluída.");
            else alvo.setValue(novo);
        });
        entrada.setId(novo.getId());
    }
}
