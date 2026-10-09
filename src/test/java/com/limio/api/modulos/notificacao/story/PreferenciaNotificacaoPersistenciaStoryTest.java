package com.limio.api.modulos.notificacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.notificacao.actions.usecase.AtualizarPreferenciaNotificacaoUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.ConsultarPreferenciaNotificacaoUseCase;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoRepository;

/**
 * BDD da ETI-34 contra banco real (Spring context + Testcontainers Postgres),
 * a partir do usecase: unicidade por usuário e padrões sem criar registro.
 * Os cenários HTTP (200/400/401) ficam em {@link PreferenciaNotificacaoStoryTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PreferenciaNotificacaoPersistenciaStoryTest {

    @Autowired
    private ConsultarPreferenciaNotificacaoUseCase consultarUseCase;

    @Autowired
    private AtualizarPreferenciaNotificacaoUseCase atualizarUseCase;

    @Autowired
    private PreferenciaNotificacaoRepository repository;

    @BeforeEach
    void limparBase() {
        repository.deleteAll();
    }

    private static PreferenciaNotificacao preferencia(boolean push, boolean email, boolean sms, boolean whatsapp) {
        return PreferenciaNotificacao.builder().push(push).email(email).sms(sms).whatsapp(whatsapp).build();
    }

    @Test
    void dadoUsuarioSemPreferencia_quandoConsulta_entaoRecebePadroesSemCriarRegistro() {
        PreferenciaNotificacao resultado = consultarUseCase.executar(UUID.randomUUID());

        assertThat(resultado.getPush()).isTrue();
        assertThat(resultado.getEmail()).isFalse();
        assertThat(resultado.getSms()).isFalse();
        assertThat(resultado.getWhatsapp()).isFalse();
        assertThat(repository.count()).isZero();
    }

    @Test
    void dadoUsuario_quandoAtualizaDuasVezes_entaoMantemUmUnicoRegistroComOUltimoEstado() {
        UUID usuarioId = UUID.randomUUID();

        atualizarUseCase.executar(usuarioId, preferencia(true, false, false, true));
        atualizarUseCase.executar(usuarioId, preferencia(false, true, true, false));

        assertThat(repository.count()).isEqualTo(1);
        PreferenciaNotificacao consultada = consultarUseCase.executar(usuarioId);
        assertThat(consultada.getPush()).isFalse();
        assertThat(consultada.getEmail()).isTrue();
        assertThat(consultada.getSms()).isTrue();
        assertThat(consultada.getWhatsapp()).isFalse();
    }

    @Test
    void dadoDoisUsuarios_quandoUmAtualiza_entaoOOutroContinuaComOsPadroes() {
        UUID usuarioA = UUID.randomUUID();
        UUID usuarioB = UUID.randomUUID();

        atualizarUseCase.executar(usuarioA, preferencia(false, true, true, true));

        PreferenciaNotificacao doB = consultarUseCase.executar(usuarioB);
        assertThat(doB.getPush()).isTrue();
        assertThat(doB.getEmail()).isFalse();
        assertThat(doB.getId()).isNull();
    }

    @Test
    void dadoRegistroExistente_quandoTentaGravarOutroDoMesmoUsuario_entaoBancoRecusa() {
        UUID usuarioId = UUID.randomUUID();
        PreferenciaNotificacao primeira = preferencia(true, false, false, false);
        primeira.setUsuarioId(usuarioId);
        repository.saveAndFlush(primeira);

        PreferenciaNotificacao duplicada = preferencia(false, false, false, false);
        duplicada.setUsuarioId(usuarioId);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
