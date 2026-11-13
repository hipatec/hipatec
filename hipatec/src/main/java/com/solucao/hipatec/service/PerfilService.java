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

    @Autowired
    private com.solucao.hipatec.repository.SeguimentoRepository seguimentos;

    @Autowired
    private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder;

    @Autowired private org.springframework.beans.factory.ObjectProvider<PostService> posts;
    private int publicacoes(Integer perfil) {
        var provider=posts.getIfAvailable();
        if(provider==null) return 0;
        try { return provider.listarPorAutor(perfil).size(); }
        catch(Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível carregar publicações.",e); }
    }
    public List<Perfil> listar() {
        var perfis = repository.findAll();
        perfis.forEach(p -> {
            p.setNum_seguidores(seguimentos.countBySeguidoPerfilId(p.getId()));
            p.setNum_seguindo(seguimentos.countBySeguidorPerfilId(p.getId()));
        });
        return perfis;
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

    @Autowired
    private com.solucao.hipatec.config.Acesso acesso;

    public PerfilDTO buscarPerfil(String role, Integer id) {

        if (!"estudantes".equals(role) && !"mentoras".equals(role))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        Perfil perfil;
        PerfilDTO dto = acesso.proprio(role, id)
                ? new com.solucao.hipatec.dto.PerfilConfiguracoesDTO() : new PerfilDTO();

        if ("estudantes".equals(role)) {

            perfil = perfilRepository.buscarPorEstudante(id);

            Estudante estudante = estudanteRepository.findById(id).orElseThrow();

            dto.setNome(estudante.getNome());
            if (dto instanceof com.solucao.hipatec.dto.PerfilConfiguracoesDTO configuracoes)
                configuracoes.setEmail(estudante.getEmail());

        } else {

            perfil = perfilRepository.buscarPorMentora(id);

            Mentora mentora = mentoraRepository.findById(id).orElseThrow();

            dto.setNome(mentora.getNome());
            if (dto instanceof com.solucao.hipatec.dto.PerfilConfiguracoesDTO configuracoes)
                configuracoes.setEmail(mentora.getEmail());
        }

        dto.setId(perfil.getId());
        dto.setRole(role);
        dto.setUsuario(perfil.getUsuario());
        dto.setBiografia(perfil.getBiografia());
        dto.setNum_seguidores(seguimentos.countBySeguidoPerfilId(perfil.getId()));
        dto.setNum_seguindo(seguimentos.countBySeguidorPerfilId(perfil.getId()));
        dto.setNum_publicacoes(publicacoes(perfil.getId()));
        dto.setPfp(perfil.getPfp());
        dto.setBackground(perfil.getBackground());

        return dto;
    }

    @jakarta.transaction.Transactional
    public PerfilDTO atualizarPerfil(
            String role,
            Integer id,
            String nome,
            String usuario,
            String biografia,
            String email,
            String novaSenha,
            MultipartFile pfp,
            MultipartFile background
        ) {

        if (!"estudantes".equals(role) && !"mentoras".equals(role))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        if (email != null && (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "E-mail inválido.");
        boolean trocarSenha = novaSenha != null && !novaSenha.isEmpty();
        if (trocarSenha && (novaSenha.isBlank() || novaSenha.length() < 8 || novaSenha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.");
        if (email != null) {
            // Uma linha comum serializa ajustes de e-mail entre roles e instâncias.
            // O lock dura até o commit; não depende de sincronização apenas nesta JVM.
            entityManager.createQuery("select p from Perfil p order by p.id", Perfil.class)
                    .setMaxResults(1).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
            boolean estudanteDuplicado = estudanteRepository.findByEmailIgnoreCase(email).stream()
                    .anyMatch(c -> !"estudantes".equals(role) || !c.getId().equals(id));
            boolean mentoraDuplicada = mentoraRepository.findByEmailIgnoreCase(email).stream()
                    .anyMatch(c -> !"mentoras".equals(role) || !c.getId().equals(id));
            if (estudanteDuplicado || mentoraDuplicada) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "E-mail indisponível.");
        }
        Perfil perfil;

        if ("estudantes".equals(role)) {

            perfil = perfilRepository.buscarPorEstudante(id);

            Estudante estudante = estudanteRepository.findById(id)
                    .orElseThrow();

            estudante.setNome(nome);
            if (email != null) estudante.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
            if (trocarSenha) estudante.setSenha("{bcrypt}" + encoder.encode(novaSenha));

            estudanteRepository.save(estudante);

        } else {

            perfil = perfilRepository.buscarPorMentora(id);

            Mentora mentora = mentoraRepository.findById(id)
                    .orElseThrow();

            mentora.setNome(nome);
            if (email != null) mentora.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
            if (trocarSenha) mentora.setSenha("{bcrypt}" + encoder.encode(novaSenha));

            mentoraRepository.save(mentora);
        }

        if (nome == null || nome.isBlank() || nome.length() > 100 || usuario == null || usuario.isBlank()
                || usuario.length() > 100 || biografia == null || biografia.length() > 255)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Dados de perfil inválidos.");
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
