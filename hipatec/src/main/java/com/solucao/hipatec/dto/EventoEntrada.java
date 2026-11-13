package com.solucao.hipatec.dto;

public record EventoEntrada(String titulo, String data, String horario, String local,
        String descricao, String visibilidade, String requisicaoId) {}
