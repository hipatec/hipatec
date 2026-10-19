package com.solucao.hipatec.service;

import com.solucao.hipatec.model.Mentora;
import com.solucao.hipatec.model.Perfil;
import com.solucao.hipatec.repository.MentoraRepository;
import com.solucao.hipatec.repository.PerfilRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MentoraService {

    private final MentoraRepository repository;

    private final BCryptPasswordEncoder passwordEncoder;

    public MentoraService(MentoraRepository repository,
            BCryptPasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Mentora> listar() {
        return repository.findAll();
    }

    
    public Mentora buscarPorId(Integer id) {
        return repository.findById(id).orElse(null);
    }

     @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Integer login(String email, String senha) {
        if (email == null || senha == null || senha.isBlank() || senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        var contas = repository.findByEmailIgnoreCase(email.trim());
        if (contas.size() != 1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        if (contas.stream().anyMatch(conta -> conta.getSenha() != null && conta.getSenha().startsWith("{bcrypt}"))) {
            if (contas.size() != 1 || senha.getBytes(StandardCharsets.UTF_8).length > 72
                    || !passwordEncoder.matches(senha, contas.get(0).getSenha().substring(8))) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
            }
            return contas.get(0).getId();
        }
        // Contas ainda não recuperadas mantêm o procedimento legado, cujo SQL não está versionado.


        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("login_mentora");

        query.registerStoredProcedureParameter(
                "email",
                String.class,
                jakarta.persistence.ParameterMode.IN
        );

        query.registerStoredProcedureParameter(
                "senha",
                String.class,
                jakarta.persistence.ParameterMode.IN
        );

        query.setParameter("email", email);
        query.setParameter("senha", senha);

        Object resultado = query.getSingleResult();

        if (!(resultado instanceof Number) || ((Number) resultado).intValue() != contas.get(0).getId())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        var conta = contas.get(0);
        conta.setSenha("{bcrypt}" + passwordEncoder.encode(senha));
        repository.save(conta);
        return conta.getId();
    }

    @Autowired
    private MentoraRepository MentoraRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Transactional
    public Mentora salvar(Mentora mentora) {
        if (mentora.getId() != null || mentora.getNome() == null || mentora.getNome().isBlank()
                || mentora.getUsuario() == null || mentora.getUsuario().isBlank()
                || mentora.getEmail() == null || !mentora.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || mentora.getEmail().length() > 254 || mentora.getNome().length() > 100 || mentora.getUsuario().length() > 100
                || mentora.getDataNascimento() == null || !mentora.getDataNascimento().isBefore(java.time.LocalDate.now())
                || mentora.getSenha() == null || mentora.getSenha().isBlank() || mentora.getSenha().length() < 8
                || mentora.getSenha().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados de cadastro inválidos.");
        mentora.setEmail(mentora.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        if (!repository.findByEmailIgnoreCase(mentora.getEmail()).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro não disponível para estes dados.");
        mentora.setSenha("{bcrypt}" + passwordEncoder.encode(mentora.getSenha()));
        mentora.setAprovada(false);


        System.out.println("Entrou no save");

        Mentora mentoraSalvo = MentoraRepository.save(mentora);

        System.out.println("Mentora salvo: " + mentoraSalvo.getId());

        Perfil perfil = new Perfil();

        perfil.setUsuario(mentora.getUsuario());

        System.out.println("Usuario: " + mentora.getUsuario());

        perfil.setBiografia("");
        perfil.setNum_seguidores(0);
        perfil.setNum_seguindo(0);

        perfil.setId_mentora(mentoraSalvo.getId());

        perfilRepository.save(perfil);

        System.out.println("Perfil salvo");

        return mentoraSalvo;
    }
}