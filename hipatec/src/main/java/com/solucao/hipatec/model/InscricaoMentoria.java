package com.solucao.hipatec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inscricao_mentoria", uniqueConstraints = @UniqueConstraint(
        name = "uk_inscricao_mentoria_estudante_mentoria",
        columnNames = {"estudante_id", "mentoria_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InscricaoMentoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estudante_id", nullable = false)
    private Integer estudanteId;

    @Column(name = "mentoria_id", nullable = false)
    private Integer mentoriaId;

    @Column(nullable = false)
    private Integer progresso = 0;

    @Column(nullable = false, length = 30)
    private String status = "ATIVA";

    @Column(name = "data_inscricao", nullable = false)
    private LocalDateTime dataInscricao;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;
}
