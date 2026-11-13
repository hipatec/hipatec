package com.solucao.hipatec.service;

import com.solucao.hipatec.config.Acesso;
import com.solucao.hipatec.dto.ConteudoEntrada;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.CursoRepository;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CursoService {
    private final CursoRepository repository;
    private final Acesso acesso;
    @PersistenceContext private EntityManager em;
    public CursoService(CursoRepository repository, Acesso acesso) { this.repository=repository; this.acesso=acesso; }
    public List<Curso> listar() { return repository.findAll(); }
    @Transactional
    public Curso criar(ConteudoEntrada entrada) {
        var usuario=acesso.atual();
        if (!usuario.role().equals("mentoras")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        em.find(Perfil.class, acesso.perfil().getId(), LockModeType.PESSIMISTIC_WRITE);
        var anterior=repository.findByIdMentoraAndRequisicaoId(usuario.id(), entrada.requisicaoId());
        var titulo=entrada.titulo().strip();
        var descricao=entrada.descricaoNormalizada();
        var especifico=entrada.instituicao() == null ? "" : entrada.instituicao().strip();
        if (anterior.isPresent()) {
            var item=anterior.get();
            if (!Objects.equals(item.getTitulo(),titulo) || !Objects.equals(item.getDescricao(),descricao)
                || !Objects.equals(item.getFormato(),entrada.formato()) || !Objects.equals(item.getInstituicao(),especifico))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Envio já gravado com outros dados. Consulte a lista.");
            return item;
        }
        var item=new Curso();
        item.setIdMentora(usuario.id()); item.setRequisicaoId(entrada.requisicaoId());
        item.setTitulo(titulo); item.setDescricao(descricao); item.setFormato(entrada.formato()); item.setInstituicao(especifico);

        return repository.saveAndFlush(item);
    }
}
