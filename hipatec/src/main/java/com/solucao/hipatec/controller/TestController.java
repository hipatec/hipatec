package com.solucao.hipatec.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/teste")
    public String home() {
        return "API funcionando!";
    }
}