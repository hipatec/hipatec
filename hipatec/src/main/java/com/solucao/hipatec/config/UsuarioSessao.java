package com.solucao.hipatec.config;

import java.io.Serializable;

// O hash permanece somente na sessão do servidor, para revogação após troca de senha.
public record UsuarioSessao(Integer id, String role, String senhaHash) implements Serializable {}
