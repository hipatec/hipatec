package com.solucao.hipatec.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name="curso", uniqueConstraints=@UniqueConstraint(columnNames={"idMentora", "requisicaoId"}))
public class Curso {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    private Integer idMentora;
    private String titulo;
    @Column(length=4000) private String descricao;
    private String instituicao;
    private String formato;
    private String requisicaoId;
}
