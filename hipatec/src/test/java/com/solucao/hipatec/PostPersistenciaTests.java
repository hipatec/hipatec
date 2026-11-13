package com.solucao.hipatec;

import com.google.firebase.database.*;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.service.PostService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.concurrent.*;

/** Contratos do callback SDK simulado; a integração real é RtdbSdkTests + social-http.py. */
class PostPersistenciaTests {
    static class Fixture implements AutoCloseable {
        final FirebaseDatabase database=mock(FirebaseDatabase.class);
        final DatabaseReference ref=mock(DatabaseReference.class);
        final DataSnapshot snapshot=mock(DataSnapshot.class);
        final MutableData data=mock(MutableData.class);
        final org.mockito.MockedStatic<FirebaseDatabase> firebase=mockStatic(FirebaseDatabase.class);
        final PostService service;
        Fixture() {
            firebase.when(FirebaseDatabase::getInstance).thenReturn(database);
            when(database.getReference()).thenReturn(ref); when(database.getReference(anyString())).thenReturn(ref);
            when(ref.child(anyString())).thenReturn(ref); when(snapshot.child(anyString())).thenReturn(snapshot);
            when(data.child(anyString())).thenReturn(data); when(data.getChildren()).thenReturn(java.util.List.of());
            var post=new Post(); post.setId("post"); post.setAutorId(42); post.setConteudo("Texto");
            when(data.getValue(Post.class)).thenReturn(post); when(data.getValue(Boolean.class)).thenReturn(true);
            when(snapshot.getValue(Post.class)).thenReturn(post);
            doAnswer(i->{ i.getArgument(0,ValueEventListener.class).onDataChange(snapshot); return null; }).when(ref).addListenerForSingleValueEvent(any());
            doAnswer(i->{ i.getArgument(0,ValueEventListener.class).onDataChange(snapshot); return null; }).when(ref).addValueEventListener(any());
            service=new PostService();
        }
        void completar(DatabaseError erro) {
            doAnswer(i->{ var handler=i.getArgument(0,Transaction.Handler.class); var result=handler.doTransaction(data);
                handler.onComplete(erro,result.isSuccess(),snapshot); return null; }).when(ref).runTransaction(any(),eq(false));
        }
        public void close(){firebase.close();}
    }
    static Comentario comentario() { var c=new Comentario();c.setAutorId(42);c.setTexto("Texto");return c; }
    @Test void falhaDeGravacaoNaoPodeVirarSucesso() throws Exception {
        try(var f=new Fixture()) {
            f.completar(DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED));
            assertThrows(ExecutionException.class,()->f.service.curtir("post",42));
            assertThrows(ExecutionException.class,()->f.service.descurtir("post",42));
            assertThrows(ExecutionException.class,()->f.service.comentar("post",comentario()));
        }
    }
    @Test void contaNosPersistidosEUsaChaveDoPerfilParaEstadoPessoal() throws Exception {
        try(var f=new Fixture()) {
            when(f.snapshot.hasChild("post")).thenReturn(true);when(f.snapshot.getChildrenCount()).thenReturn(3L);
            when(f.snapshot.getValue(Boolean.class)).thenReturn(true);
            var estado=f.service.interacoes("post",42);
            assertEquals(3,estado.likesCount());assertEquals(3,estado.commentsCount());assertTrue(estado.curtidoUsuario());
            assertFalse(f.service.interacoes("post",null).curtidoUsuario());verify(f.snapshot,never()).child("1");
        }
    }
    @Test void postInexistenteNaoGravaInteracoes() throws Exception {
        try(var f=new Fixture()) {
            when(f.data.getValue(Post.class)).thenReturn(null);f.completar(null);
            for(org.junit.jupiter.api.function.Executable acao:java.util.List.<org.junit.jupiter.api.function.Executable>of(
                ()->f.service.curtir("ausente",42),()->f.service.comentar("ausente",comentario()),()->f.service.repostar("ausente",42))) {
                var erro=assertThrows(org.springframework.web.server.ResponseStatusException.class,acao);assertEquals(404,erro.getStatusCode().value());
            }
            verify(f.data,never()).setValue(any());
        }
    }
    @Test void repostAtomicoAguardaPersistenciaDosDoisCaminhosEFalhaSemSucessoParcial() throws Exception {
        try(var f=new Fixture()) {
            f.completar(null);f.service.repostar("post",42);
            verify(f.ref,times(1)).runTransaction(any(),eq(false));
            verify(f.data,atLeastOnce()).child("reposts");
            verify(f.data).setValue(isA(Repost.class));
            f.completar(DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED));
            assertThrows(ExecutionException.class,()->f.service.repostar("post",42));
            verify(f.ref,never()).setValueAsync(any());verify(f.ref,never()).updateChildrenAsync(anyMap());
        }
    }
    @Test void likeEComentarioAguardamFutureDoProvider() throws Exception {
        try(var f=new Fixture()) {
            var executor=Executors.newSingleThreadExecutor();
            try {
                for(boolean like:new boolean[]{true,false}) {
                    var chamado=new CompletableFuture<Transaction.Handler>();
                    doAnswer(i->{var handler=i.getArgument(0,Transaction.Handler.class);handler.doTransaction(f.data);chamado.complete(handler);return null;}).when(f.ref).runTransaction(any(),eq(false));
                    var result=executor.submit(()->{if(like)f.service.curtir("post",42);else f.service.comentar("post",comentario());return null;});
                    var handler=chamado.get(5,TimeUnit.SECONDS);assertFalse(result.isDone());
                    handler.onComplete(null,true,f.snapshot);result.get(5,TimeUnit.SECONDS);
                }
            } finally {executor.shutdownNow();}
        }
    }
}
