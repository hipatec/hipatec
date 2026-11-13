package com.solucao.hipatec.service;

import com.solucao.hipatec.config.Acesso;
import com.solucao.hipatec.dto.*;
import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.EventoRepository;
import jakarta.persistence.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventoService {
    private final EventoRepository repository;
    private final Acesso acesso;
    @PersistenceContext private EntityManager em;
    public EventoService(EventoRepository repository, Acesso acesso) {
        this.repository = repository; this.acesso = acesso;
    }
    private Integer dono() {
        if (!Set.of("estudantes", "mentoras").contains(acesso.atual().role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return acesso.perfil().getId();
    }
    public static class EntradaInvalida extends RuntimeException {
        public final String campo;
        public EntradaInvalida(String campo, String mensagem) { super(mensagem); this.campo = campo; }
    }
    private String texto(String valor, String campo, int limite, boolean obrigatorio) {
        String texto = valor == null ? "" : valor.strip();
        if ((obrigatorio && texto.isEmpty()) || texto.length() > limite)
            throw new EntradaInvalida(campo, "Informe " + campo + " com " + (obrigatorio ? "1" : "0") + " a " + limite + " caracteres.");
        return texto;
    }
    private LocalDate data(String valor, String campo) {
        try {
            if (valor == null || !valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            LocalDate data = LocalDate.parse(valor);
            if (data.getYear() < 2000 || data.getYear() > 2100) throw new IllegalArgumentException();
            return data;
        } catch (RuntimeException e) { throw new EntradaInvalida(campo, "Informe uma data válida entre 2000 e 2100."); }
    }
    @Transactional
    public EventoDTO criar(EventoEntrada entrada) {
        Integer dono = dono();
        var evento = new Evento();
        evento.setTitulo(texto(entrada.titulo(), "titulo", 160, true));
        evento.setLocal(texto(entrada.local(), "local", 1000, false));
        evento.setDescricao(texto(entrada.descricao(), "descricao", 4000, false));
        evento.setData(data(entrada.data(), "data"));
        if (entrada.horario() != null && !entrada.horario().isEmpty()) {
            try {
                if (!entrada.horario().matches("[0-9]{2}:[0-9]{2}")) throw new IllegalArgumentException();
                evento.setHorario(LocalTime.parse(entrada.horario()));
                if (ZoneId.of(evento.getFuso()).getRules().getValidOffsets(evento.getData().atTime(evento.getHorario())).size() != 1)
                    throw new IllegalArgumentException();
            } catch (RuntimeException e) { throw new EntradaInvalida("horario", "Informe um horário válido e não ambíguo em America/Sao_Paulo (HH:mm)."); }
        }
        if (!Set.of("PRIVADO", "PUBLICO").contains(Objects.toString(entrada.visibilidade(), "")))
            throw new EntradaInvalida("visibilidade", "Escolha Privado ou Público.");
        if (entrada.visibilidade().equals("PUBLICO") && !acesso.atual().role().equals("mentoras"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Estudantes só podem criar eventos privados.");
        evento.setVisibilidade(entrada.visibilidade());
        try {
            if (entrada.requisicaoId() == null || !UUID.fromString(entrada.requisicaoId()).toString().equals(entrada.requisicaoId())) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new EntradaInvalida("requisicaoId", "Identificador de envio inválido."); }
        evento.setRequisicaoId(entrada.requisicaoId());
        evento.setProprietarioPerfilId(dono);
        // Serializa retries do mesmo perfil entre processos; a UNIQUE também protege a tabela.
        em.find(Perfil.class, dono, LockModeType.PESSIMISTIC_WRITE);
        var anterior = repository.findByProprietarioPerfilIdAndRequisicaoId(dono, entrada.requisicaoId());
        if (anterior.isPresent()) {
            var existente = anterior.get();
            boolean mesmoConteudo = existente.getTitulo().equals(evento.getTitulo())
                && existente.getData().equals(evento.getData())
                && Objects.equals(existente.getHorario(), evento.getHorario())
                && existente.getLocal().equals(evento.getLocal())
                && existente.getDescricao().equals(evento.getDescricao())
                && existente.getVisibilidade().equals(evento.getVisibilidade());
            if (!mesmoConteudo)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este envio já foi gravado com outros dados. Consulte a agenda antes de criar outro evento.");
            return EventoDTO.de(existente);
        }
        return EventoDTO.de(repository.saveAndFlush(evento));
    }
    @Transactional(readOnly = true)
    public List<EventoDTO> listar(String de, String ate) {
        Integer dono = dono();
        var inicio = data(de, "de"); var fim = data(ate, "ate");
        if (fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) > 366)
            throw new EntradaInvalida("ate", "Consulte um intervalo de até 366 dias, com fim igual ou posterior ao início.");
        return repository.listar(dono, inicio, fim).stream().map(EventoDTO::de).toList();
    }
    @Transactional(readOnly = true)
    public EventoDTO recuperar(Long id) {
        Integer dono = dono();
        var evento = repository.findById(id).filter(e -> e.getProprietarioPerfilId().equals(dono) || e.getVisibilidade().equals("PUBLICO"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return EventoDTO.de(evento);
    }
}
