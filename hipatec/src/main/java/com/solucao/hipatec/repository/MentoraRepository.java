package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Mentora;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MentoraRepository extends JpaRepository<Mentora, Integer> {
    List<Mentora> findByEmailIgnoreCase(String email);

}