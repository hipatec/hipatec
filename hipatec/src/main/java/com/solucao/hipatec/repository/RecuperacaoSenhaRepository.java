package com.solucao.hipatec.repository;

import java.util.Optional;
import com.solucao.hipatec.model.RecuperacaoSenha;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, String> {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecuperacaoSenha> findById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);
}
