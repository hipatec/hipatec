package com.solucao.hipatec.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mentoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mentoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String titulo;

    @jakarta.persistence.Column(length=4000)
    private String descricao;

    private Integer progresso;

    private String area;
    private String formato;
    private String requisicaoId;

    private Integer idMentora;



}