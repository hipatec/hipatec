package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Evento;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    Optional<Evento> findByProprietarioPerfilIdAndRequisicaoId(Integer dono, String requisicao);
    @Query("select e from Evento e where (e.proprietarioPerfilId = :dono or e.visibilidade = 'PUBLICO') and e.data between :de and :ate order by e.data, e.horario, e.id")
    List<Evento> listar(@Param("dono") Integer dono, @Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
