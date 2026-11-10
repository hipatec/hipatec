package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Vaga;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VagaRepository extends JpaRepository<Vaga, Long> {
    List<Vaga> findByAtivaTrueOrderByPublicadaEmDesc();
}
