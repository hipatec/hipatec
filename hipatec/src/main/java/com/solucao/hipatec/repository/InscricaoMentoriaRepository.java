package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.InscricaoMentoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscricaoMentoriaRepository extends JpaRepository<InscricaoMentoria, Long> {
    List<InscricaoMentoria> findByEstudanteId(Integer estudanteId);
    List<InscricaoMentoria> findByMentoriaId(Integer mentoriaId);
}
