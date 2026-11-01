package com.sanosysalvos.ms_alertas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sanosysalvos.ms_alertas.dto.AlertaResponseDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.exception.ServicioNoDisponibleException;
import com.sanosysalvos.ms_alertas.model.EstadoAlerta;
import com.sanosysalvos.ms_alertas.service.AlertaService;
import com.sanosysalvos.ms_alertas.service.ContactoService;

/*
 * Seguridad y validacion de entradas de los endpoints REST, con el contexto completo
 * (SecurityConfig real) y los servicios mockeados. Cubre lo que exige CLAUDE.md: prueba
 * positiva, rechazo sin autenticacion y entradas invalidas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertaControllerSeguridadTest {

    private static final String BODY_ALERTA = """
            {"mascotaId":"m1","latitudCentro":-33.45,"longitudCentro":-70.66,"radioKm":1.5}""";

    private static final String BODY_CONTACTO = """
            {"mascotaId":"m1","nombreContacto":"Ana","emailContacto":"ana@example.com",
             "mensaje":"Creo haber visto a tu mascota cerca de la plaza."}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertaService alertaService;

    @MockitoBean
    private ContactoService contactoService;

    private RequestPostProcessor usuarioComun() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_openid"));
    }

    private RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    // ---- lectura publica ----

    @Test
    void zona_esPublicaYDevuelveLasAlertas() throws Exception {
        when(alertaService.buscarPorZona(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(
                AlertaResponseDto.builder().id(1L).mascotaId("m1").latitudCentro(-33.45)
                        .longitudCentro(-70.66).radioKm(1.5).estado(EstadoAlerta.ACTIVA).build()));

        mockMvc.perform(get("/alertas/zona").param("lat", "-33.45").param("lng", "-70.66").param("radioKm", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mascotaId").value("m1"));
    }

    @Test
    void zona_conRadioMayorAlMaximoDevuelve400() throws Exception {
        mockMvc.perform(get("/alertas/zona").param("lat", "-33.45").param("lng", "-70.66").param("radioKm", "5000"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(alertaService);
    }

    @Test
    void zona_conCoordenadasFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(get("/alertas/zona").param("lat", "95").param("lng", "-70.66").param("radioKm", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/alertas/zona").param("lat", "-33.45").param("lng", "181").param("radioKm", "2"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(alertaService);
    }

    @Test
    void zona_conParametroFaltanteONoNumericoDevuelve400NoUn500() throws Exception {
        mockMvc.perform(get("/alertas/zona").param("lng", "-70.66").param("radioKm", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/alertas/zona").param("lat", "abc").param("lng", "-70.66").param("radioKm", "2"))
                .andExpect(status().isBadRequest());
    }

    // ---- escritura: solo administradores ----

    @Test
    void crear_sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/alertas").contentType(MediaType.APPLICATION_JSON).content(BODY_ALERTA))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(alertaService);
    }

    @Test
    void crear_conTokenDeUsuarioComunDevuelve403() throws Exception {
        mockMvc.perform(post("/alertas").with(usuarioComun()).contentType(MediaType.APPLICATION_JSON).content(BODY_ALERTA))
                .andExpect(status().isForbidden());
        verifyNoInteractions(alertaService);
    }

    @Test
    void crear_comoAdministradorDevuelve201() throws Exception {
        when(alertaService.crearAlerta(any())).thenReturn(
                AlertaResponseDto.builder().id(7L).mascotaId("m1").estado(EstadoAlerta.ACTIVA).build());

        mockMvc.perform(post("/alertas").with(administrador()).contentType(MediaType.APPLICATION_JSON).content(BODY_ALERTA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void crear_comoAdministradorConJsonMalformadoDevuelve400NoUn500() throws Exception {
        mockMvc.perform(post("/alertas").with(administrador()).contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_comoAdministradorConDatosInvalidosDevuelve400() throws Exception {
        String invalido = """
                {"mascotaId":"","latitudCentro":999,"longitudCentro":-70.66,"radioKm":0}""";

        mockMvc.perform(post("/alertas").with(administrador()).contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(alertaService);
    }

    @Test
    void actualizarYEliminar_conUsuarioComunDevuelve403() throws Exception {
        mockMvc.perform(patch("/alertas/1").with(usuarioComun()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"INACTIVA\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/alertas/1").with(usuarioComun())).andExpect(status().isForbidden());
        verifyNoInteractions(alertaService);
    }

    @Test
    void eliminar_comoAdministradorDevuelve204() throws Exception {
        mockMvc.perform(delete("/alertas/1").with(administrador())).andExpect(status().isNoContent());
        verify(alertaService).eliminarAlerta(1L);
    }

    @Test
    void eliminar_alertaInexistenteDevuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("Alerta no encontrada: 9")).when(alertaService).eliminarAlerta(9L);

        mockMvc.perform(delete("/alertas/9").with(administrador())).andExpect(status().isNotFound());
    }

    @Test
    void metodoNoSoportadoDevuelve405NoUn500() throws Exception {
        mockMvc.perform(put("/alertas").with(administrador()).contentType(MediaType.APPLICATION_JSON).content(BODY_ALERTA))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---- contacto (publico, sin cuenta) ----

    @Test
    void contactar_esPublicoYDevuelve2xx() throws Exception {
        mockMvc.perform(post("/contactos").contentType(MediaType.APPLICATION_JSON).content(BODY_CONTACTO))
                .andExpect(status().is2xxSuccessful());
        verify(contactoService).contactar(any());
    }

    @Test
    void contactar_conMascotaInexistenteDevuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("No se encontró un contacto")).when(contactoService).contactar(any());

        mockMvc.perform(post("/contactos").contentType(MediaType.APPLICATION_JSON).content(BODY_CONTACTO))
                .andExpect(status().isNotFound());
    }

    @Test
    void contactar_conMsMascotasCaidoDevuelve503() throws Exception {
        doThrow(new ServicioNoDisponibleException("ms-mascotas no responde", new RuntimeException()))
                .when(contactoService).contactar(any());

        mockMvc.perform(post("/contactos").contentType(MediaType.APPLICATION_JSON).content(BODY_CONTACTO))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void contactar_conEmailInvalidoDevuelve400() throws Exception {
        String invalido = BODY_CONTACTO.replace("ana@example.com", "no-es-un-correo");

        mockMvc.perform(post("/contactos").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(contactoService);
    }
}
