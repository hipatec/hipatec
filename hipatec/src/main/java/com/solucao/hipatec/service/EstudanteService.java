package com.solucao.hipatec.service;

import com.solucao.hipatec.model.Estudante;
import com.solucao.hipatec.model.Perfil;
import com.solucao.hipatec.repository.EstudanteRepository;
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
public class EstudanteService {

    private final EstudanteRepository repository;

    private final BCryptPasswordEncoder passwordEncoder;

    public EstudanteService(EstudanteRepository repository,
            BCryptPasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Estudante> listar() {
        return repository.findAll();
    }

    public Estudante buscarPorId(Integer id) {
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


        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("login_estudante");

        query.registerStoredProcedureParameter(
                "email",
                String.class,
                jakarta.persistence.ParameterMode.IN);

        query.registerStoredProcedureParameter(
                "senha",
                String.class,
                jakarta.persistence.ParameterMode.IN);

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
    private EstudanteRepository estudanteRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Transactional
    public Estudante salvar(Estudante estudante) {
        if (estudante.getId() != null || estudante.getNome() == null || estudante.getNome().isBlank()
                || estudante.getUsuario() == null || estudante.getUsuario().isBlank()
                || estudante.getEmail() == null || !estudante.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || estudante.getEmail().length() > 254 || estudante.getNome().length() > 100 || estudante.getUsuario().length() > 100
                || estudante.getDataNascimento() == null || !estudante.getDataNascimento().isBefore(java.time.LocalDate.now())
                || estudante.getSenha() == null || estudante.getSenha().isBlank() || estudante.getSenha().length() < 8
                || estudante.getSenha().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados de cadastro inválidos.");
        estudante.setEmail(estudante.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        if (!repository.findByEmailIgnoreCase(estudante.getEmail()).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro não disponível para estes dados.");
        estudante.setSenha("{bcrypt}" + passwordEncoder.encode(estudante.getSenha()));


        System.out.println("Entrou no save");

        Estudante estudanteSalvo = estudanteRepository.save(estudante);

        System.out.println("Estudante salvo: " + estudanteSalvo.getId());

        Perfil perfil = new Perfil();

        perfil.setUsuario(estudante.getUsuario());

        System.out.println("Usuario: " + estudante.getUsuario());

        perfil.setBiografia("");
        perfil.setNum_seguidores(0);
        perfil.setNum_seguindo(0);

        perfil.setId_estudante(estudanteSalvo.getId());

        perfilRepository.save(perfil);

        System.out.println("Perfil salvo");

        return estudanteSalvo;
    }
}