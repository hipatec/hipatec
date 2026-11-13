package com.solucao.hipatec.dto;

import lombok.Getter;
import lombok.Setter;

/** Campos privados entregues somente ao dono da conta. */
@Getter
@Setter
public class PerfilConfiguracoesDTO extends PerfilDTO {
    private String email;
}
