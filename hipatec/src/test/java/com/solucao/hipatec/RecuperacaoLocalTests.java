package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.solucao.hipatec.model.Estudante;
import com.solucao.hipatec.repository.EstudanteRepository;
import com.solucao.hipatec.repository.MentoraRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RecuperacaoLocalTests {
    @Test
    void preservaContasFicticiasAoConfigurarDestinatarioReal() throws Exception {
        for (String email : new String[]{"", "destinatario@example.test", "estudante@example.test"}) {
            var estudantes = mock(EstudanteRepository.class);
            var mentoras = mock(MentoraRepository.class);
            var encoder = mock(BCryptPasswordEncoder.class);
            when(encoder.encode(anyString())).thenReturn("hash-de-teste");
            new RecuperacaoLocalApplication().contasLocais(estudantes, mentoras, encoder, email).run(null);
            var contas = ArgumentCaptor.forClass(Estudante.class);
            verify(estudantes, times(email.equals("destinatario@example.test") ? 2 : 1)).save(contas.capture());
            assertEquals("estudante@example.test", contas.getAllValues().get(0).getEmail());
            if (email.equals("destinatario@example.test")) assertEquals(email, contas.getValue().getEmail());
            verify(mentoras).save(argThat(conta -> conta.getEmail().equals("mentora@example.test")));
        }
    }
}
