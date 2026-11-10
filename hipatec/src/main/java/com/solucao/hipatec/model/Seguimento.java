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
@Table(name = "seguimento", uniqueConstraints = @UniqueConstraint(
        name = "uk_seguimento_origem_destino",
        columnNames = {"seguidor_perfil_id", "seguido_perfil_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Seguimento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seguidor_perfil_id", nullable = false)
    private Integer seguidorPerfilId;

    @Column(name = "seguido_perfil_id", nullable = false)
    private Integer seguidoPerfilId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
