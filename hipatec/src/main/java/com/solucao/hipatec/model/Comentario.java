package com.solucao.hipatec.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comentario {

    private String id;

    private Integer autorId;

    private String autorNome;

    private String autorFoto;

    private String texto;
    private String requisicaoId;

    private Long dataCriacao;
}
