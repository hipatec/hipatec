package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Perfil;

// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
// import org.springframework.data.jpa.repository.query.Procedure;
// import org.springframework.data.repository.query.Param;
// import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerfilRepository extends JpaRepository<Perfil, Integer> {
    @Query("SELECT p FROM Perfil p WHERE p.id_estudante = :id")
    Perfil buscarPorEstudante(Integer id);

    @Query("SELECT p FROM Perfil p WHERE p.id_mentora = :id")
    Perfil buscarPorMentora(Integer id);

    @Query("SELECT p FROM Perfil p WHERE lower(p.usuario) = lower(:usuario) AND p.id_estudante IS NOT NULL")
    Perfil buscarEstudantePorUsuario(@Param("usuario") String usuario);

    @Query("SELECT p FROM Perfil p WHERE lower(p.usuario) = lower(:usuario) AND p.id_mentora IS NOT NULL")
    Perfil buscarMentoraPorUsuario(@Param("usuario") String usuario);

    @Modifying
    @Query("""
            UPDATE Perfil p
            SET p.num_publicacoes =
                p.num_publicacoes + 1
            WHERE p.id = :id
            """)
    void incrementarPublicacoes(
            @Param("id") Integer id);
}