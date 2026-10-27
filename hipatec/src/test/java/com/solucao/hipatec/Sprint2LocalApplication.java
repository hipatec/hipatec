package com.solucao.hipatec;

import com.solucao.hipatec.model.*;
import com.solucao.hipatec.repository.MentoraRepository;
import com.solucao.hipatec.service.*;
import org.springframework.boot.SpringApplication;
import java.time.LocalDate;

/** Banco H2 descartável: sessão, cadastro e perfil reais; serviços externos simulados. */
public class Sprint2LocalApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(SessaoTests.Config.class);
        var context = app.run(
                "--spring.config.location=optional:classpath:/application-sprint2-local.properties",
                "--spring.datasource.url=jdbc:h2:mem:sprint2;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--server.servlet.session.cookie.http-only=true",
                "--server.servlet.session.cookie.same-site=lax",
                "--server.servlet.session.cookie.secure=false",
                "--server.servlet.session.timeout=30m");
        for (String nome : new String[]{"aluna", "outra"}) {
            var conta = new Estudante();
            conta.setNome(nome); conta.setUsuario(nome); conta.setEmail(nome + "@example.test");
            conta.setDataNascimento(LocalDate.of(2000, 1, 1)); conta.setSenha("TesteLocal123!");
            context.getBean(EstudanteService.class).salvar(conta);
        }
        for (String nome : new String[]{"mentora", "pendente"}) {
            var conta = new Mentora();
            conta.setNome(nome); conta.setUsuario(nome); conta.setEmail(nome + "@example.test");
            conta.setDataNascimento(LocalDate.of(2000, 1, 1)); conta.setSenha("TesteLocal123!");
            conta = context.getBean(MentoraService.class).salvar(conta);
            if (nome.equals("mentora")) {
                conta.setAprovada(true); context.getBean(MentoraRepository.class).save(conta);
            }
        }
        org.mockito.Mockito.when(context.getBean(CloudinaryService.class).uploadFile(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Upload externo não está disponível neste ambiente de teste."));
    }
}
