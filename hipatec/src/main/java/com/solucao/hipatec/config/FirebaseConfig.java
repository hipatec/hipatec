
package com.solucao.hipatec.config;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(name = "hipatec.firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {

    @Value("${FIREBASE_CREDENTIALS_JSON:}")
    private String credentialsJson;

    @Value("${FIREBASE_CREDENTIALS_PATH:}")
    private String credentialsPath;

    @Value("${FIREBASE_DATABASE_URL:}")
    private String databaseUrl;

    @PostConstruct
    public void initialize() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }

            if (databaseUrl == null || databaseUrl.isBlank()) {
                throw new IllegalStateException(
                        "FIREBASE_DATABASE_URL não configurada."
                );
            }

            GoogleCredentials credentials;

            if (credentialsJson != null && !credentialsJson.isBlank()) {

                // PRODUÇÃO: utiliza o conteúdo JSON configurado no Railway
                try (InputStream stream = new ByteArrayInputStream(
                        credentialsJson.getBytes(StandardCharsets.UTF_8))) {

                    credentials = GoogleCredentials.fromStream(stream);
                }

                System.out.println(
                        "Firebase: utilizando credenciais JSON."
                );

            } else if (credentialsPath != null && !credentialsPath.isBlank()) {

                // LOCAL: utiliza o caminho do arquivo JSON
                Path path = Path.of(credentialsPath);

                if (!path.isAbsolute()) {
                    path = Path.of(System.getProperty("user.dir"))
                            .resolve(path)
                            .normalize();
                }

                try (InputStream stream = new FileInputStream(path.toFile())) {
                    credentials = GoogleCredentials.fromStream(stream);
                }

                System.out.println(
                        "Firebase: utilizando arquivo local de credenciais."
                );

            } else {
                throw new IllegalStateException(
                        "Configure FIREBASE_CREDENTIALS_JSON ou FIREBASE_CREDENTIALS_PATH."
                );
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
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
