package com.solucao.hipatec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class HipatecApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("C:/Users/NathaliaV/Documents/hipatec/hipatec")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            if (System.getenv(entry.getKey()) == null
                    && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        SpringApplication.run(HipatecApplication.class, args);
    }
}