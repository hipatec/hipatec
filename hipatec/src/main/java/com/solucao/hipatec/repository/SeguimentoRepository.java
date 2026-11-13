package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Seguimento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface SeguimentoRepository extends JpaRepository<Seguimento, Long> {
    int countBySeguidorPerfilId(Integer perfilId);
    int countBySeguidoPerfilId(Integer perfilId);
    List<Seguimento> findBySeguidorPerfilId(Integer perfilId);
    List<Seguimento> findBySeguidoPerfilId(Integer perfilId);
    boolean existsBySeguidorPerfilIdAndSeguidoPerfilId(Integer seguidorPerfilId, Integer seguidoPerfilId);
    // Delete derivado carrega e remove entidades: precisa de transação de escrita (o controller chama direto).
    @Transactional
    void deleteBySeguidorPerfilIdAndSeguidoPerfilId(Integer seguidorPerfilId, Integer seguidoPerfilId);
}
