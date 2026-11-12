package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Seguimento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimentoRepository extends JpaRepository<Seguimento, Long> {
    List<Seguimento> findBySeguidorPerfilId(Integer perfilId);
    List<Seguimento> findBySeguidoPerfilId(Integer perfilId);
    boolean existsBySeguidorPerfilIdAndSeguidoPerfilId(Integer seguidorPerfilId, Integer seguidoPerfilId);
    void deleteBySeguidorPerfilIdAndSeguidoPerfilId(Integer seguidorPerfilId, Integer seguidoPerfilId);
}
