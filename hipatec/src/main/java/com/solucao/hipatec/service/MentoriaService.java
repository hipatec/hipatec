package com.solucao.hipatec.service;

import com.solucao.hipatec.config.Acesso;
import com.solucao.hipatec.dto.ConteudoEntrada;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.MentoriaRepository;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MentoriaService {
    private final MentoriaRepository repository;
    private final Acesso acesso;
    @PersistenceContext private EntityManager em;
    public MentoriaService(MentoriaRepository repository, Acesso acesso) { this.repository=repository; this.acesso=acesso; }
    public List<Mentoria> listar() { return repository.findAll(); }
    @Transactional
    public Mentoria criar(ConteudoEntrada entrada) {
        var usuario=acesso.atual();
        if (!usuario.role().equals("mentoras")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        em.find(Perfil.class, acesso.perfil().getId(), LockModeType.PESSIMISTIC_WRITE);
        var anterior=repository.findByIdMentoraAndRequisicaoId(usuario.id(), entrada.requisicaoId());
        var titulo=entrada.titulo().strip();
        var descricao=entrada.descricaoNormalizada();
        var especifico=entrada.area() == null ? "" : entrada.area().strip();
        if (anterior.isPresent()) {
            var item=anterior.get();
            if (!Objects.equals(item.getTitulo(),titulo) || !Objects.equals(item.getDescricao(),descricao)
                || !Objects.equals(item.getFormato(),entrada.formato()) || !Objects.equals(item.getArea(),especifico))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Envio já gravado com outros dados. Consulte a lista.");
            return item;
        }
        var item=new Mentoria();
        item.setIdMentora(usuario.id()); item.setRequisicaoId(entrada.requisicaoId());
        item.setTitulo(titulo); item.setDescricao(descricao); item.setFormato(entrada.formato()); item.setArea(especifico);
        item.setProgresso(0);
        return repository.saveAndFlush(item);
    }
}
