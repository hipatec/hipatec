package com.solucao.hipatec.repository;
import com.solucao.hipatec.model.Denuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DenunciaRepository extends JpaRepository<Denuncia,Long> {
    Optional<Denuncia> findByPerfilIdAndPostIdAndComentarioId(Integer perfilId,String postId,String comentarioId);
}
