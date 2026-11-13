package com.solucao.hipatec.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
@Table(name="denuncia",uniqueConstraints=@UniqueConstraint(columnNames={"perfilId","postId","comentarioId"}))
public class Denuncia {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Integer perfilId;
    private String postId;
    private String comentarioId;
    private String motivo;
    @Column(length=5000) private String evidencia;
    private Integer autorPerfilId;
    private String status = "PENDENTE";
    private java.time.Instant criadoEm = java.time.Instant.now();
}
