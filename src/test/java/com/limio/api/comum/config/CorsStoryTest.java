package com.limio.api.comum.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;

/**
 * CORS do app web: o navegador manda um preflight (OPTIONS) sem token antes
 * do POST. Antes do CorsConfig ele voltava 401 sem headers CORS e o navegador
 * barrava a chamada.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsStoryTest {

    private static final String ORIGEM_EXPO_WEB = "http://localhost:8080";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightDeOrigemPermitidaRespondeSemExigirToken() throws Exception {
        mockMvc.perform(options("/auth/cadastro")
                .header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_EXPO_WEB));
    }

    @Test
    void preflightDeRotaAutenticadaTambemPassa() throws Exception {
        mockMvc.perform(options("/conta/preferencias-notificacao")
                .header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_EXPO_WEB));
    }

    @Test
    void respostaDeErroTambemLevaHeaderCors() throws Exception {
        // sem o header, o navegador esconde o ErroResponse do app
        mockMvc.perform(post("/auth/login")
                .header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_EXPO_WEB));
    }

    @Test
    void origemNaoPermitidaEhRecusada() throws Exception {
        mockMvc.perform(options("/auth/cadastro")
                .header(HttpHeaders.ORIGIN, "https://site-qualquer.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
