package com.solucao.hipatec.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@com.google.firebase.database.IgnoreExtraProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    private String id;
    private Boolean excluido;
    private Long versao = 0L;
    private Integer autorId;
    private String conteudo;
    private String imagemUrl;
    private String imagemHash;
    private String autorFoto;
    private String requisicaoId;
    private String repostOriginalId;
    private Integer repostOwnerId;
    private Boolean repostComComentario;
    private Post repostOriginal;
    private Boolean originalIndisponivel;


    private Long dataCriacao;

    private Integer likesCount;
    private Integer commentsCount;
    private Integer repostsCount;

}