package com.sanosysalvos.ms_alertas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactoRequestDto {

    @NotBlank(message = "mascotaId es requerido")
    private String mascotaId;

    @NotBlank(message = "nombreContacto es requerido")
    @Size(max = 100)
    private String nombreContacto;

    @NotBlank(message = "emailContacto es requerido")
    @Email(message = "emailContacto debe ser un correo válido")
    @Size(max = 150)
    private String emailContacto;

    @Size(max = 30)
    private String telefonoContacto;

    @NotBlank(message = "mensaje es requerido")
    @Size(max = 1000, message = "mensaje no puede superar los 1000 caracteres")
    private String mensaje;
}
