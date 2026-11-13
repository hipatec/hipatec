package com.solucao.hipatec.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "evento", uniqueConstraints = @UniqueConstraint(columnNames = {"proprietario_perfil_id", "requisicao_id"}))
@Getter @Setter
public class Evento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "proprietario_perfil_id", nullable = false)
    private Integer proprietarioPerfilId;
    @Column(name = "requisicao_id", nullable = false, length = 36)
    private String requisicaoId;
    @Column(nullable = false, length = 160)
    private String titulo;
    @Column(nullable = false)
    private LocalDate data;
    private LocalTime horario;
    @Column(nullable = false, length = 1000)
    private String local;
    @Column(nullable = false, length = 4000)
    private String descricao;
    @Column(nullable = false, length = 10)
    private String visibilidade;
    // Data e hora civis no fuso explícito; não dependem do timezone da conexão JDBC.
    @Column(nullable = false, length = 40)
    private String fuso = "America/Sao_Paulo";
}
