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
@Table(name = "vaga")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vaga {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String empresa;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Column(length = 40)
    private String modalidade;

    @Column(length = 255)
    private String local;

    @Column(length = 100)
    private String pais;

    @Column(length = 100)
    private String estado;

    @Column(length = 100)
    private String cidade;

    @Column(length = 100)
    private String area;

    @Column(length = 100)
    private String formacao;

    @Column(length = 50)
    private String senioridade;

    @Column(length = 255)
    private String idiomas;

    @Column(length = 50)
    private String contrato;

    @Column(name = "publicada_em", nullable = false)
    private LocalDateTime publicadaEm;

    @Column(length = 1000)
    private String url;

    @Column(nullable = false)
    private Boolean ativa = true;
}
