package com.solucao.hipatec.repository;

import com.solucao.hipatec.model.Estudante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
// import org.springframework.data.jpa.repository.query.Procedure;
// import org.springframework.data.repository.query.Param;

public interface EstudanteRepository extends JpaRepository<Estudante, Integer> {
    List<Estudante> findByEmailIgnoreCase(String email);
    // @Procedure(procedureName = "sp_login_estudante")
    // Integer sp_login_estudante(
    //         @Param("email") String email,
    //         @Param("senha") String senha
    //   );

    
}