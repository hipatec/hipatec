package com.solucao.hipatec.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PerfilDTO {

    private String role;

    // Perfil
    private Integer id;
    private String usuario;
    private String biografia;
    private Integer num_seguidores;
    private Integer num_seguindo;
    private String pfp;
    private String background;
    private Integer num_publicacoes;

    // Usuário
    private String nome;
}