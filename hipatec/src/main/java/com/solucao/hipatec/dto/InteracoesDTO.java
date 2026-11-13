package com.solucao.hipatec.dto;
public record InteracoesDTO(long likesCount, long commentsCount, boolean curtidoUsuario, long repostsCount, boolean repostadoUsuario) {
    public InteracoesDTO(long likesCount, long commentsCount, boolean curtidoUsuario) { this(likesCount,commentsCount,curtidoUsuario,0,false); }
}
