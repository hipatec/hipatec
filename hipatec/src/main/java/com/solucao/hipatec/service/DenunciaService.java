package com.solucao.hipatec.service;
import com.solucao.hipatec.config.Acesso;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.DenunciaRepository;
import jakarta.persistence.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class DenunciaService {
    private final Acesso acesso; private final PostService posts; private final DenunciaRepository repository;
    @PersistenceContext private EntityManager em;
    public DenunciaService(Acesso acesso, PostService posts, DenunciaRepository repository) { this.acesso=acesso; this.posts=posts; this.repository=repository; }
    @Transactional
    public Long registrar(String postId,String comentarioId,String motivo) throws Exception {
        if(!java.util.Set.of("assedio","discurso-odioso","spam","conteudo-inadequado").contains(java.util.Objects.toString(motivo,"")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escolha um motivo válido.");
        String comentario=comentarioId==null ? "" : comentarioId;
        var perfil=acesso.perfil().getId(); em.find(Perfil.class,perfil,LockModeType.PESSIMISTIC_WRITE);
        var anterior=repository.findByPerfilIdAndPostIdAndComentarioId(perfil,postId,comentario);
        if(anterior.isPresent()) {
            if(!Objects.equals(anterior.get().getMotivo(),motivo))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Denúncia já registrada com outro motivo.");
            return anterior.get().getId();
        }
        var publicacao=posts.recuperar(postId);
        var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("versao",1);
        var textos=new ArrayList<Map<String,Object>>();
        var publicacaoSnapshot=snapshot(publicacao); textos.add(publicacaoSnapshot);
        snapshot.put("publicacao",publicacaoSnapshot);
        Integer autor=publicacao.getAutorId();
        if(!comentario.isEmpty()) {
            var alvo=posts.listarComentarios(postId).stream().filter(c->comentario.equals(c.getId())).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            var detalhe=new LinkedHashMap<String,Object>();
            detalhe.put("id",alvo.getId()); detalhe.put("autorId",alvo.getAutorId());
            detalhe.put("texto",alvo.getTexto()); detalhe.put("dataCriacao",alvo.getDataCriacao());
            snapshot.put("comentario",detalhe); autor=alvo.getAutorId();
        }
        if(publicacao.getRepostOriginalId()!=null) {
            try {
                // Nunca confia no objeto repostOriginal recebido/armazenado junto à republicação.
                var original=snapshot(posts.recuperar(publicacao.getRepostOriginalId()));
                snapshot.put("original",original); textos.add(original);
            } catch(ResponseStatusException e) {
                if(e.getStatusCode().value()!=404) throw e;
                snapshot.put("original",Map.of("id",publicacao.getRepostOriginalId(),"indisponivel",true));
            }
        }
        String evidencia=serializar(snapshot,textos);
        var denuncia=new Denuncia(); denuncia.setPerfilId(perfil); denuncia.setPostId(postId); denuncia.setComentarioId(comentario); denuncia.setMotivo(motivo); denuncia.setEvidencia(evidencia); denuncia.setAutorPerfilId(autor);
        return repository.saveAndFlush(denuncia).getId();
    }

    // Lista explícita: sem perfil completo, foto, credenciais, interações ou payload arbitrário.
    private Map<String,Object> snapshot(Post post) {
        var resultado=new LinkedHashMap<String,Object>();
        resultado.put("id",post.getId()); resultado.put("autorId",post.getAutorId());
        resultado.put("conteudo",post.getConteudo()); resultado.put("dataCriacao",post.getDataCriacao());
        resultado.put("imagemUrl",post.getImagemUrl()); resultado.put("imagemHash",post.getImagemHash());
        resultado.put("repostOriginalId",post.getRepostOriginalId());
        return resultado;
    }
    private String serializar(Map<String,Object> snapshot,List<Map<String,Object>> textos) throws Exception {
        var mapper=new ObjectMapper();
        String json=mapper.writeValueAsString(snapshot);
        // VARCHAR(5000) existente: mídia integral; texto longo vira trecho explícito + hash do texto completo.
        while(json.length()>5000) {
            var maior=textos.stream().filter(t->t.get("conteudo") instanceof String)
                .max(Comparator.comparingInt(t->((String)t.get("conteudo")).length())).orElseThrow();
            String texto=(String)maior.get("conteudo");
            if(texto.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Evidência excede o limite de armazenamento.");
            if(!maior.containsKey("conteudoHash")) {
                maior.put("conteudoHash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))));
                maior.put("conteudoTruncado",true);
            }
            int fim=Math.max(0,texto.length()-Math.max(1,(json.length()-5000)/2));
            if(fim>0 && Character.isHighSurrogate(texto.charAt(fim-1))) fim--;
            maior.put("conteudo",texto.substring(0,fim));
            json=mapper.writeValueAsString(snapshot);
        }
        return json;
    }
}
