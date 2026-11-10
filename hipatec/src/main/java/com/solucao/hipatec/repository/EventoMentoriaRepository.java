package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.EventoMentoria;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoMentoriaRepository extends JpaRepository<EventoMentoria, Long> {
    List<EventoMentoria> findByInicioBetweenOrderByInicio(LocalDateTime inicio, LocalDateTime fim);
    List<EventoMentoria> findByMentoriaIdOrderByInicio(Integer mentoriaId);
}
