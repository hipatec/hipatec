
package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.OnBoarding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnBoardingRepository
        extends JpaRepository<OnBoarding, Long> {

    Optional<OnBoarding> findByEstudanteId(Long estudanteId);

    boolean existsByEstudanteId(Long estudanteId);
}
