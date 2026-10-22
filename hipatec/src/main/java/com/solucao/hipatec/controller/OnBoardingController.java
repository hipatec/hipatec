
package com.solucao.hipatec.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solucao.hipatec.dto.OnBoardingRequest;
import com.solucao.hipatec.model.OnBoarding;
import com.solucao.hipatec.repository.OnBoardingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:8100")
@RestController
@RequestMapping("/api/onboarding")
public class OnBoardingController {

    private final OnBoardingRepository repository;
    private final ObjectMapper objectMapper;

    public OnBoardingController(
            OnBoardingRepository repository,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/respostas")
    @PreAuthorize("hasAuthority('ROLE_ESTUDANTE')")
    public ResponseEntity<?> salvarRespostas(
            @RequestBody OnBoardingRequest request,
            Principal principal) {

        Long estudanteId = obterIdEstudante(principal);

        validarRespostas(request);

        try {
            OnBoarding onboarding = repository
                    .findByEstudanteId(estudanteId)
                    .orElseGet(OnBoarding::new);

            onboarding.setEstudanteId(estudanteId);

            onboarding.setNivelExperiencia(
                    request.nivelExperiencia());

            onboarding.setAreasInteresse(
                    objectMapper.writeValueAsString(
                            request.areasInteresse()));

            onboarding.setObjetivos(
                    objectMapper.writeValueAsString(
                            request.objetivos()));

            onboarding.setPrincipalDificuldade(
                    request.principalDificuldade());

            onboarding.setFormatoAprendizado(
                    request.formatoAprendizado());

            onboarding.setDataConclusao(LocalDateTime.now());

            repository.save(onboarding);

            return ResponseEntity.ok(Map.of(
                    "message", "Respostas salvas com sucesso.",
                    "concluido", true
            ));

        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erro ao processar as respostas.");
        }
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ROLE_ESTUDANTE')")
    public ResponseEntity<?> consultarStatus(Principal principal) {

        Long estudanteId = obterIdEstudante(principal);

        boolean concluido =
                repository.existsByEstudanteId(estudanteId);

        return ResponseEntity.ok(Map.of(
                "concluido", concluido
        ));
    }

    @GetMapping("/respostas")
    @PreAuthorize("hasAuthority('ROLE_ESTUDANTE')")
    public ResponseEntity<?> consultarRespostas(Principal principal) {

        Long estudanteId = obterIdEstudante(principal);

        return repository.findByEstudanteId(estudanteId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "A estudante ainda não respondeu ao onboarding."
                        )));
    }

    private void validarRespostas(OnBoardingRequest request) {

        if (request == null
                || request.nivelExperiencia() == null
                || request.nivelExperiencia().isBlank()
                || request.areasInteresse() == null
                || request.areasInteresse().isEmpty()
                || request.objetivos() == null
                || request.objetivos().isEmpty()
                || request.principalDificuldade() == null
                || request.principalDificuldade().isBlank()
                || request.formatoAprendizado() == null
                || request.formatoAprendizado().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Preencha todos os campos do onboarding."
            );
        }
    }

    private Long obterIdEstudante(Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "É necessário fazer login."
            );
        }

        try {
            return Long.parseLong(principal.getName());

        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "A identificação da estudante precisa ser adaptada à autenticação do projeto."
            );
        }
    }
}
