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

    public Integer login(String email, String senha) {
        var contas = repository.findByEmailIgnoreCase(email.trim());
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

        return ((Number) resultado).intValue();
    }

    @Autowired
    private EstudanteRepository estudanteRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Transactional
    public Estudante salvar(Estudante estudante) {

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