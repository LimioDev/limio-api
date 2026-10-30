package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.perfil.actions.service.ConsultaFotoPerfilService;
import com.limio.api.modulos.perfil.actions.service.ConsultaPerfilEmpregadorService;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ConsultaPerfilEmpregadorServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    private ConsultaPerfilEmpregadorService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaPerfilEmpregadorService(perfilEmpregadorService,
                new ConsultaFotoPerfilService(midiaService, armazenamentoService));
    }

    private void comPerfil(String nomeExibicao) {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao(nomeExibicao).build();
        DadosUsuario dados = new DadosUsuario("Maria da Silva", "maria@example.com", null, null, null);
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenReturn(new MeuPerfilEmpregador(perfil, dados));
    }

    @Test
    void dadoPerfilComFoto_quandoBuscaResumo_entaoDevolveNomeDeExibicaoELink() {
        comPerfil("Mari");
        Midia foto = Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/atual.jpg", "image/jpeg", 10, 10, 100);
        Instant expiraEm = Instant.now();
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.of(foto));
        when(armazenamentoService.gerarLink("fotos-perfil/atual.jpg"))
                .thenReturn(new LinkTemporario("https://bucket/assinado", expiraEm));

        assertThat(service.buscarResumo(USUARIO_ID))
                .isEqualTo(new ResumoPerfilEmpregador("Mari", "https://bucket/assinado", expiraEm, null, 0));
    }

    @Test
    void dadoPerfilSemFoto_quandoBuscaResumo_entaoFotoNulaSemGerarLink() {
        comPerfil("Mari");
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.buscarResumo(USUARIO_ID)).isEqualTo(new ResumoPerfilEmpregador("Mari", null, null, null, 0));
        verifyNoInteractions(armazenamentoService);
    }

    @Test
    void dadoStorageIndisponivel_quandoBuscaResumo_entaoSegueSemFoto() {
        comPerfil("Mari");
        Midia foto = Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/atual.jpg", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.of(foto));
        when(armazenamentoService.gerarLink("fotos-perfil/atual.jpg"))
                .thenThrow(new ArmazenamentoIndisponivelException());

        assertThat(service.buscarResumo(USUARIO_ID)).isEqualTo(new ResumoPerfilEmpregador("Mari", null, null, null, 0));
    }

    @Test
    void dadoContaInexistente_quandoBuscaResumo_entaoPropagaPerfilNaoEncontrado() {
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> service.buscarResumo(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);
        verifyNoInteractions(midiaService, armazenamentoService);
    }

    @Test
    void dadoUsuarioSemPerfil_quandoBuscaResumoSemCriar_entaoNomeInicialSemCriarPerfil() {
        when(perfilEmpregadorService.nomeExibicaoSemCriar(USUARIO_ID)).thenReturn(Optional.of("Maria da Silva"));
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.buscarResumoSemCriar(USUARIO_ID))
                .contains(new ResumoPerfilEmpregador("Maria da Silva", null, null));
        verify(perfilEmpregadorService, never()).garantirPerfil(USUARIO_ID);
    }

    @Test
    void dadaContaEncerrada_quandoBuscaResumoSemCriar_entaoVazioSemConsultarFoto() {
        when(perfilEmpregadorService.nomeExibicaoSemCriar(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.buscarResumoSemCriar(USUARIO_ID)).isEmpty();
        verifyNoInteractions(midiaService, armazenamentoService);
    }

    @Test
    void dadoVariosEmpregadores_quandoBuscaResumosEmLote_entaoUmaConsultaDePerfilEUmaDeFotoComMediaENome() {
        UUID comFoto = UUID.randomUUID();
        UUID semFoto = UUID.randomUUID();
        UUID semPerfilSalvo = UUID.randomUUID();
        PerfilEmpregador perfilComFoto = PerfilEmpregador.builder().usuarioId(comFoto).nomeExibicao("Mari")
                .mediaAvaliacoes(new BigDecimal("4.5")).qtdAvaliacoes(8).build();
        PerfilEmpregador perfilSemFoto = PerfilEmpregador.builder().usuarioId(semFoto).nomeExibicao("João").build();
        PerfilEmpregador transitorio = PerfilEmpregador.builder().usuarioId(semPerfilSalvo).nomeExibicao("Ana").build();
        List<UUID> ids = List.of(comFoto, semFoto, semPerfilSalvo);
        when(perfilEmpregadorService.buscarPerfisOuIniciais(ids)).thenReturn(Map.of(
                comFoto, perfilComFoto, semFoto, perfilSemFoto, semPerfilSalvo, transitorio));
        Instant expiraEm = Instant.now();
        when(midiaService.buscarFotosPerfilAtivas(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Map.of(
                comFoto, Midia.fotoPerfil(comFoto, "fotos-perfil/mari.jpg", "image/jpeg", 10, 10, 100)));
        when(armazenamentoService.gerarLink("fotos-perfil/mari.jpg"))
                .thenReturn(new LinkTemporario("https://bucket/mari", expiraEm));

        Map<UUID, ResumoPerfilEmpregador> resumos = service.buscarResumosEmLote(ids);

        assertThat(resumos).containsOnlyKeys(comFoto, semFoto, semPerfilSalvo);
        assertThat(resumos.get(comFoto)).isEqualTo(
                new ResumoPerfilEmpregador("Mari", "https://bucket/mari", expiraEm, new BigDecimal("4.5"), 8));
        assertThat(resumos.get(semFoto)).isEqualTo(new ResumoPerfilEmpregador("João", null, null, null, 0));
        assertThat(resumos.get(semPerfilSalvo).nomeExibicao()).isEqualTo("Ana");
        verify(midiaService).buscarFotosPerfilAtivas(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void dadoStorageIndisponivel_quandoBuscaResumosEmLote_entaoSegueSemFoto() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Mari").build();
        when(perfilEmpregadorService.buscarPerfisOuIniciais(List.of(USUARIO_ID))).thenReturn(Map.of(USUARIO_ID, perfil));
        when(midiaService.buscarFotosPerfilAtivas(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Map.of(
                USUARIO_ID, Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/atual.jpg", "image/jpeg", 10, 10, 100)));
        when(armazenamentoService.gerarLink("fotos-perfil/atual.jpg")).thenThrow(new ArmazenamentoIndisponivelException());

        assertThat(service.buscarResumosEmLote(List.of(USUARIO_ID)))
                .containsEntry(USUARIO_ID, new ResumoPerfilEmpregador("Mari", null, null, null, 0));
    }
}
