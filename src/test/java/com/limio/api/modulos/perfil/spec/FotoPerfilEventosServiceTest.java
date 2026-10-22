package com.limio.api.modulos.perfil.spec;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.perfil.actions.service.ArmazenamentoService;
import com.limio.api.modulos.perfil.actions.service.FotoPerfilEventosService;
import com.limio.api.modulos.perfil.eventos.FotoPerfilTrocadaEvent;

@ExtendWith(MockitoExtension.class)
class FotoPerfilEventosServiceTest {

    @Mock
    private ArmazenamentoService armazenamentoService;

    @InjectMocks
    private FotoPerfilEventosService service;

    @Test
    void dadoTrocaConfirmada_entaoApagaSoAFotoAnterior() {
        service.aoConfirmarTroca(new FotoPerfilTrocadaEvent("nova.jpg", "antiga.jpg"));

        verify(armazenamentoService).remover("antiga.jpg");
    }

    @Test
    void dadoPrimeiraFotoConfirmada_entaoNaoApagaNada() {
        service.aoConfirmarTroca(new FotoPerfilTrocadaEvent("nova.jpg", null));

        verifyNoInteractions(armazenamentoService);
    }

    @Test
    void dadoTrocaDesfeita_entaoApagaOObjetoNovoQueFicouSemMidia() {
        service.aoDesfazerTroca(new FotoPerfilTrocadaEvent("nova.jpg", "antiga.jpg"));

        verify(armazenamentoService).remover("nova.jpg");
    }
}
