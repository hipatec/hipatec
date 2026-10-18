package com.solucao.hipatec.config;

import java.util.concurrent.Executor;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@EnableAsync
public class RecuperacaoSenhaConfig implements AsyncConfigurer {
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean("recuperacaoSenhaExecutor")
    public Executor recuperacaoSenhaExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("recuperacao-senha-");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Não registrar argumentos, token ou exceções SMTP, que podem conter dados pessoais.
        return (error, method, args) -> LoggerFactory.getLogger(RecuperacaoSenhaConfig.class)
                .error("Falha ao processar recuperação de senha. Verifique banco e provedor de e-mail.");
    }
}
