package com.solucao.hipatec.repository;
import com.solucao.hipatec.model.Mentoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MentoriaRepository extends JpaRepository<Mentoria, Integer> {
    Optional<Mentoria> findByIdMentoraAndRequisicaoId(Integer idMentora, String requisicaoId);
}
