package com.solucao.hipatec.dto;

import com.solucao.hipatec.model.Evento;
public record EventoDTO(Long id, String titulo, String data, String horario, String local,
        String descricao, String visibilidade, String fuso) {
    public static EventoDTO de(Evento e) {
        return new EventoDTO(e.getId(), e.getTitulo(), e.getData().toString(),
            e.getHorario() == null ? null : e.getHorario().toString(), e.getLocal(),
            e.getDescricao(), e.getVisibilidade(), e.getFuso());
    }
}
