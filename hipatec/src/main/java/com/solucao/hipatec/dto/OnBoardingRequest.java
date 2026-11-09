
package com.solucao.hipatec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OnBoardingRequest(
    String nivelExperiencia,
    List<String> areasInteresse,
    List<String> objetivos,
    String principalDificuldade,
    String formatoAprendizado
) {}
