
package com.solucao.hipatec.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "onboarding_respostas",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_onboarding_estudante",
            columnNames = "estudante_id"
        )
    }
)
public class OnBoarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estudante_id", nullable = false)
    private Long estudanteId;

    @Column(name = "nivel_experiencia", nullable = false)
    private String nivelExperiencia;

    @Column(name = "areas_interesse", nullable = false,
            columnDefinition = "nvarchar(max)")
    private String areasInteresse;

    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String objetivos;

    @Column(name = "principal_dificuldade", nullable = false)
    private String principalDificuldade;

    @Column(name = "formato_aprendizado", nullable = false)
    private String formatoAprendizado;

    @Column(name = "data_conclusao", nullable = false)
    private LocalDateTime dataConclusao;

    public Long getId() {
        return id;
    }

    public Long getEstudanteId() {
        return estudanteId;
    }

    public void setEstudanteId(Long estudanteId) {
        this.estudanteId = estudanteId;
    }

    public String getNivelExperiencia() {
        return nivelExperiencia;
    }

    public void setNivelExperiencia(String nivelExperiencia) {
        this.nivelExperiencia = nivelExperiencia;
    }

    public String getAreasInteresse() {
        return areasInteresse;
    }

    public void setAreasInteresse(String areasInteresse) {
        this.areasInteresse = areasInteresse;
    }

    public String getObjetivos() {
        return objetivos;
    }

    public void setObjetivos(String objetivos) {
        this.objetivos = objetivos;
    }

    public String getPrincipalDificuldade() {
        return principalDificuldade;
    }

    public void setPrincipalDificuldade(String principalDificuldade) {
        this.principalDificuldade = principalDificuldade;
    }

    public String getFormatoAprendizado() {
        return formatoAprendizado;
    }

    public void setFormatoAprendizado(String formatoAprendizado) {
        this.formatoAprendizado = formatoAprendizado;
    }

    public LocalDateTime getDataConclusao() {
        return dataConclusao;
    }

    public void setDataConclusao(LocalDateTime dataConclusao) {
        this.dataConclusao = dataConclusao;
    }
}
