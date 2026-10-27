package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.enums.TipoMidia;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.modulos.perfil.actions.usecase.TrocarFotoPerfilUseCase;
import com.limio.api.modulos.perfil.eventos.FotoPerfilTrocadaEvent;

@ExtendWith(MockitoExtension.class)
class TrocarFotoPerfilUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final LinkTemporario LINK = new LinkTemporario("https://bucket/assinado",
            Instant.parse("2026-10-19T18:00:00Z"));

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private TrocarFotoPerfilUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new TrocarFotoPerfilUseCase(midiaService, armazenamentoService, eventos, DataSize.ofMegabytes(5));
    }

    @Test
    void dadoPrimeiraFoto_quandoEnvia_entaoGuardaNoStorageERegistraAMidia() {
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.empty());
        when(armazenamentoService.gerarLink(anyString())).thenReturn(LINK);

        LinkTemporario link = useCase.executar(USUARIO_ID, ImagensDeTeste.jpegComExif(40, 20, 1));

        assertThat(link).isEqualTo(LINK);
        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(armazenamentoService).enviar(chave.capture(), conteudo.capture(), eq("image/jpeg"));
        assertThat(chave.getValue()).startsWith("fotos-perfil/" + USUARIO_ID + "/").endsWith(".jpg");
        assertThat(ImagensDeTeste.contem(conteudo.getValue(), ImagensDeTeste.SEGREDO_EXIF)).isFalse();

        ArgumentCaptor<Midia> midia = ArgumentCaptor.forClass(Midia.class);
        verify(midiaService).salvar(midia.capture());
        assertThat(midia.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(midia.getValue().getTipo()).isEqualTo(TipoMidia.FOTO_PERFIL);
        assertThat(midia.getValue().getUrlS3()).isEqualTo(chave.getValue());
        assertThat(midia.getValue().getMime()).isEqualTo("image/jpeg");
        assertThat(midia.getValue().getLargura()).isEqualTo(40);
        assertThat(midia.getValue().getAltura()).isEqualTo(20);
        assertThat(midia.getValue().getBytes()).isEqualTo(conteudo.getValue().length);
        assertThat(midia.getValue().isExifRemovido()).isTrue();
        assertThat(midia.getValue().getExpurgoEm()).isNull();

        verify(eventos).publishEvent(new FotoPerfilTrocadaEvent(chave.getValue(), null));
        verify(armazenamentoService).gerarLink(chave.getValue());
    }

    @Test
    void dadoFotoAnterior_quandoTroca_entaoExpurgaAAnteriorAntesDeGravarANova() {
        Midia anterior = Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/antiga.jpg", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.of(anterior));
        when(armazenamentoService.gerarLink(anyString())).thenReturn(LINK);

        useCase.executar(USUARIO_ID, ImagensDeTeste.png(30, 30));

        assertThat(anterior.getExpurgoEm()).isNotNull();
        InOrder ordem = inOrder(midiaService, armazenamentoService, eventos);
        ordem.verify(midiaService).travarFotoPerfil(USUARIO_ID);
        ordem.verify(midiaService).buscarFotoPerfilAtiva(USUARIO_ID);
        ordem.verify(armazenamentoService).enviar(anyString(), any(), eq("image/png"));
        ordem.verify(eventos).publishEvent(any(FotoPerfilTrocadaEvent.class));
        // Índice "uma foto ativa por usuário": a anterior tem de ir pro banco antes do insert da nova.
        ordem.verify(midiaService).salvarAgora(anterior);
        ordem.verify(midiaService).salvar(any(Midia.class));

        ArgumentCaptor<FotoPerfilTrocadaEvent> evento = ArgumentCaptor.forClass(FotoPerfilTrocadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().chaveAnterior()).isEqualTo("fotos-perfil/antiga.jpg");
        assertThat(evento.getValue().chaveNova()).endsWith(".png");
    }

    @Test
    void dadoArquivoVazio_quandoEnvia_entaoRecusaSemTocarEmNada() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, new byte[0]))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessage("Selecione uma foto para enviar.");

        verifyNoInteractions(midiaService, armazenamentoService, eventos);
    }

    @Test
    void dadoArquivoAcimaDoLimite_quandoEnvia_entaoRecusaComFotoInvalida() {
        byte[] grande = new byte[(int) DataSize.ofMegabytes(5).toBytes() + 1];

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, grande))
                .isInstanceOfSatisfying(FotoInvalidaException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo(CodigoErro.FOTO_INVALIDA);
                    assertThat(e.getMessage()).isEqualTo("A foto deve ter no máximo 5 MB.");
                });

        verifyNoInteractions(midiaService, armazenamentoService, eventos);
    }

    @Test
    void dadoGif_quandoEnvia_entaoRecusaComFotoInvalida() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, ImagensDeTeste.gif(20, 20)))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessage(CodigoErro.FOTO_INVALIDA.mensagemPadrao());

        verifyNoInteractions(midiaService, armazenamentoService, eventos);
    }

    @Test
    void dadoStorageFora_quandoEnvia_entaoNaoRegistraMidiaNemPublicaEvento() {
        Midia anterior = Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/antiga.jpg", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.of(anterior));
        doThrow(new ArmazenamentoIndisponivelException()).when(armazenamentoService)
                .enviar(anyString(), any(), anyString());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, ImagensDeTeste.jpeg(20, 20)))
                .isInstanceOf(ArmazenamentoIndisponivelException.class);

        assertThat(anterior.getExpurgoEm()).isNull();
        verify(midiaService, never()).salvar(any());
        verify(midiaService, never()).salvarAgora(any());
        verifyNoInteractions(eventos);
    }
}
