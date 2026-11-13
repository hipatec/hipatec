package com.solucao.hipatec.repository;
import com.solucao.hipatec.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CursoRepository extends JpaRepository<Curso, Integer> {
    Optional<Curso> findByIdMentoraAndRequisicaoId(Integer idMentora, String requisicaoId);
}
