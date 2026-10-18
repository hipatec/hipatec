package com.solucao.hipatec.service;

import com.solucao.hipatec.model.Perfil;
import com.solucao.hipatec.repository.EstudanteRepository;
import com.solucao.hipatec.repository.MentoraRepository;
import com.solucao.hipatec.repository.PerfilRepository;
import com.solucao.hipatec.dto.PerfilDTO;
import com.solucao.hipatec.model.Estudante;
import com.solucao.hipatec.model.Mentora;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
// import jakarta.persistence.StoredProcedureQuery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class PerfilService {

    private final PerfilRepository repository;

    public PerfilService(PerfilRepository repository) {
        this.repository = repository;
    }

    public List<Perfil> listar() {
        return repository.findAll();
    }

    public Perfil salvar(Perfil perfil) {
        return repository.save(perfil);
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private PerfilRepository perfilRepository;

    @Autowired
    private EstudanteRepository estudanteRepository;

    @Autowired
    private MentoraRepository mentoraRepository;

    public PerfilDTO buscarPerfil(String role, Integer id) {

        Perfil perfil;
        PerfilDTO dto = new PerfilDTO();

        if ("estudantes".equals(role)) {

            perfil = perfilRepository.buscarPorEstudante(id);

            Estudante estudante = estudanteRepository.findById(id).orElseThrow();

            dto.setNome(estudante.getNome());
            dto.setEmail(estudante.getEmail());

        } else {

            perfil = perfilRepository.buscarPorMentora(id);

            Mentora mentora = mentoraRepository.findById(id).orElseThrow();

            dto.setNome(mentora.getNome());
            dto.setEmail(mentora.getEmail());
        }

        dto.setId(perfil.getId());
        dto.setRole(role);
        dto.setUsuario(perfil.getUsuario());
        dto.setBiografia(perfil.getBiografia());
        dto.setNum_seguidores(perfil.getNum_seguidores());
        dto.setNum_seguindo(perfil.getNum_seguindo());
        dto.setNum_publicacoes(perfil.getNum_publicacoes());
        dto.setPfp(perfil.getPfp());
        dto.setBackground(perfil.getBackground());

        return dto;
    }

    public PerfilDTO atualizarPerfil(
            String role,
            Integer id,
            String nome,
            String usuario,
            String biografia,
            MultipartFile pfp,
            MultipartFile background
        ) {

        Perfil perfil;

        if ("estudantes".equals(role)) {

            perfil = perfilRepository.buscarPorEstudante(id);

            Estudante estudante = estudanteRepository.findById(id)
                    .orElseThrow();

            estudante.setNome(nome);

            estudanteRepository.save(estudante);

        } else {

            perfil = perfilRepository.buscarPorMentora(id);

            Mentora mentora = mentoraRepository.findById(id)
                    .orElseThrow();

            mentora.setNome(nome);

            mentoraRepository.save(mentora);
        }

        perfil.setUsuario(usuario);
        perfil.setBiografia(biografia);

        if (pfp != null && !pfp.isEmpty()) {

            String urlImagem = cloudinaryService.uploadFile(pfp);

            perfil.setPfp(urlImagem);
        }

        if (background != null && !background.isEmpty()) {

            String urlImagem = cloudinaryService.uploadFile(background);

            perfil.setBackground(urlImagem);
        }

        perfilRepository.save(perfil);

        return buscarPerfil(role, id);
    }

}
