package com.solucao.hipatec;

import com.solucao.hipatec.config.FirebaseConfig;
import com.solucao.hipatec.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = HipatecApplication.class, properties = {
    "spring.config.location=optional:classpath:/application-context-test.properties",
    "spring.datasource.url=jdbc:h2:mem:contexto;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "cloudinary.cloud_name=teste", "cloudinary.api_key=teste", "cloudinary.api_secret=teste",
    "spring.mail.host=localhost", "hipatec.mail.from=teste@example.test",
    "hipatec.recuperacao-senha.url=http://localhost:8100/redefinir-senha"
})
class HipatecApplicationTests {
    @MockitoBean FirebaseConfig firebase;
    @MockitoBean PostService posts;

    @Test void contextLoads() {}
}
