package com.sanosysalvos.ms_alertas.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanosysalvos.ms_alertas.dto.ContactoRequestDto;
import com.sanosysalvos.ms_alertas.service.ContactoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/contactos")
@RequiredArgsConstructor
public class ContactoController {

    private final ContactoService contactoService;

    @PostMapping
    public ResponseEntity<Void> contactar(@Valid @RequestBody ContactoRequestDto dto) {
        contactoService.contactar(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
