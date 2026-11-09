
package com.solucao.hipatec;

import com.solucao.hipatec.config.RecuperacaoSenhaConfig;
import com.solucao.hipatec.config.SecurityConfig;
import com.solucao.hipatec.controller.EstudanteController;
import com.solucao.hipatec.controller.MentoraController;
import com.solucao.hipatec.controller.RecuperacaoSenhaController;
import com.solucao.hipatec.controller.SessaoController;
import com.solucao.hipatec.model.Estudante;
import com.solucao.hipatec.model.Mentora;
import com.solucao.hipatec.repository.EstudanteRepository;
import com.solucao.hipatec.repository.MentoraRepository;
import com.solucao.hipatec.service.EmailService;
import com.solucao.hipatec.service.EstudanteService;
import com.solucao.hipatec.service.MentoraService;
import com.solucao.hipatec.service.RecuperacaoSenhaService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@Import({
    SessaoController.class,
    SecurityConfig.class,
    RecuperacaoSenhaConfig.class,
    EmailService.class,
    RecuperacaoSenhaService.class,
    RecuperacaoSenhaController.class,
    EstudanteService.class,
    EstudanteController.class,
    MentoraService.class,
    MentoraController.class
})
public class RecuperacaoLocalApplication {

    public static void main(String[] args) {
        SpringApplication app =
                new SpringApplication(RecuperacaoLocalApplication.class);

        app.setWebApplicationType(WebApplicationType.SERVLET);

        app.run(
            "--spring.profiles.active=local",
            "--spring.config.additional-location=optional:classpath:/application-recuperacao-local.properties"
        );
    }

    @Bean
    ApplicationRunner contasLocais(
            EstudanteRepository estudantes,
            MentoraRepository mentoras,
            BCryptPasswordEncoder encoder,
            @Value("${hipatec.local.test-email:}") String emailTeste
    ) {
        return args -> {
            String senha = "{bcrypt}" + encoder.encode("TesteLocal123!");

            var estudante = new Estudante();
            estudante.setNome("Estudante de teste");
            estudante.setEmail("estudante@example.test");
            estudante.setSenha(senha);
            estudantes.save(estudante);

            var mentora = new Mentora();
            mentora.setNome("Mentora de teste");
            mentora.setEmail("mentora@example.test");
            mentora.setSenha(senha);
            mentora.setAprovada(true);
            mentoras.save(mentora);

            if (!emailTeste.isBlank()
                    && !emailTeste.equalsIgnoreCase("estudante@example.test")
                    && !emailTeste.equalsIgnoreCase("mentora@example.test")) {

                var contaReal = new Estudante();
                contaReal.setNome("Teste de entrega real");
                contaReal.setEmail(emailTeste.trim());
                contaReal.setSenha(senha);
                estudantes.save(contaReal);
            }
        };
    }
}
