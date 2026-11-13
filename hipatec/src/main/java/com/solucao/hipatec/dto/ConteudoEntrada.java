package com.solucao.hipatec.dto;

import jakarta.validation.constraints.*;

public record ConteudoEntrada(
    @NotBlank @Size(max=160) String titulo,
    @Size(max=4000) String descricao,
    @Size(max=160) String instituicao,
    @Size(max=160) String area,
    @NotNull @Pattern(regexp="Online|Presencial|Híbrido") String formato,
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requisicaoId
) {
    public String descricaoNormalizada() { return descricao == null ? "" : descricao.strip(); }
}
