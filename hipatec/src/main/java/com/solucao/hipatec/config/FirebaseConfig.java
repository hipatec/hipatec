package com.solucao.hipatec.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }

            String credentialsJson =
                    System.getenv("FIREBASE_CREDENTIALS_JSON");

            String databaseUrl =
                    System.getenv("FIREBASE_DATABASE_URL");

            if (credentialsJson == null || credentialsJson.isBlank()) {
                throw new IllegalStateException(
                        "FIREBASE_CREDENTIALS_JSON não configurada."
                );
            }

            if (databaseUrl == null || databaseUrl.isBlank()) {
                throw new IllegalStateException(
                        "FIREBASE_DATABASE_URL não configurada."
                );
            }

            ByteArrayInputStream credentialsStream =
                    new ByteArrayInputStream(
                            credentialsJson.getBytes(StandardCharsets.UTF_8)
                    );

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(
                            GoogleCredentials.fromStream(credentialsStream)
                    )
                    .setDatabaseUrl(databaseUrl)
                    .build();

            FirebaseApp.initializeApp(options);

            System.out.println("Firebase inicializado com sucesso!");

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao inicializar o Firebase.", e
            );
        }
    }
}
