package com.solucao.hipatec.dto;

import lombok.Data;

@Data
public class ComentarioDTO {

    private Integer usuarioId;

    private String autorNome;

    private String autorFoto;

    private String texto;
    private String requisicaoId;

    private String postId;
    
}
