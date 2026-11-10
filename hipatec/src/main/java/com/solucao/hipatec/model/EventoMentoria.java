package com.solucao.hipatec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evento_mentoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventoMentoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentoria_id", nullable = false)
    private Integer mentoriaId;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descricao;

    @Column(nullable = false, length = 30)
    private String tipo = "MENTORIA";

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column
    private LocalDateTime fim;

    @Column(length = 255)
    private String localizacao;

    @Column(length = 1000)
    private String link;
}
