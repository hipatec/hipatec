package com.solucao.hipatec.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RecuperacaoSenha {
    // Uma solicitação por conta; um novo link substitui o anterior.
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 10)
    private String perfil;

    @Column(nullable = false)
    private Integer contaId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant criadoEm;

    @Column(nullable = false)
    private Instant expiraEm;
    
    @Version
    private Long versao;
}
