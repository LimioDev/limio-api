package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;
import com.limio.api.modulos.perfil.records.JanelaAtendimentoRequest;
import com.limio.api.modulos.perfil.records.PerfilAtuacaoRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PerfilAtuacaoRequestTest {

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void criarValidator() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        fabrica.close();
    }

    private static Set<String> camposInvalidos(PerfilAtuacaoRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static PerfilAtuacaoRequest comRaio(Integer raio) {
        return new PerfilAtuacaoRequest(List.of(UUID.randomUUID()), raio, "Campinas", Uf.SP, List.of());
    }

    private static PerfilAtuacaoRequest comJanelas(JanelaAtendimentoRequest... janelas) {
        return new PerfilAtuacaoRequest(List.of(), 10, "Campinas", Uf.SP, Arrays.asList(janelas));
    }

    private static JanelaAtendimentoRequest janela(LocalTime inicio, LocalTime fim) {
        return new JanelaAtendimentoRequest(DiaSemana.SEGUNDA, inicio, fim);
    }

    private static JanelaAtendimentoRequest janela(DiaSemana dia, int horaInicio, int horaFim) {
        return new JanelaAtendimentoRequest(dia, LocalTime.of(horaInicio, 0), LocalTime.of(horaFim, 0));
    }

    @Test
    void deveAceitarPerfilSemEspecialidadesESemJanelas() {
        assertThat(camposInvalidos(new PerfilAtuacaoRequest(List.of(), 10, "Campinas", Uf.SP, List.of()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 40 })
    void deveAceitarRaioNosLimites(int raio) {
        assertThat(camposInvalidos(comRaio(raio))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { -5, 0, 41 })
    void deveRecusarRaioForaDe1a40(int raio) {
        assertThat(camposInvalidos(comRaio(raio))).containsExactly("raioAtendimentoKm");
    }

    @Test
    void deveRecusarJanelaComInicioDepoisDoFim() {
        assertThat(camposInvalidos(comJanelas(janela(LocalTime.of(14, 0), LocalTime.of(9, 0)))))
                .containsExactly("janelas[0].intervaloValido");
    }

    @Test
    void deveRecusarJanelaComInicioIgualAoFim() {
        assertThat(camposInvalidos(comJanelas(janela(LocalTime.of(9, 0), LocalTime.of(9, 0)))))
                .containsExactly("janelas[0].intervaloValido");
    }

    @Test
    void deveRecusarJanelasSobrepostasNoMesmoDia() {
        assertThat(camposInvalidos(comJanelas(janela(DiaSemana.SEGUNDA, 8, 12), janela(DiaSemana.SEGUNDA, 9, 11))))
                .containsExactly("janelasSemSobreposicao");
    }

    @Test
    void deveRecusarJanelaRepetidaNoMesmoDia() {
        assertThat(camposInvalidos(comJanelas(janela(DiaSemana.TERCA, 8, 12), janela(DiaSemana.TERCA, 8, 12))))
                .containsExactly("janelasSemSobreposicao");
    }

    @Test
    void deveAceitarJanelasQueSoEncostamOuSaoDeDiasDiferentes() {
        assertThat(camposInvalidos(comJanelas(janela(DiaSemana.SEGUNDA, 13, 18), janela(DiaSemana.SEGUNDA, 8, 13),
                janela(DiaSemana.TERCA, 9, 11)))).isEmpty();
    }

    @Test
    void naoDeveAcusarSobreposicaoDeJanelaQueJaEInvalida() {
        assertThat(camposInvalidos(comJanelas(janela(DiaSemana.SEGUNDA, 8, 12), janela(DiaSemana.SEGUNDA, 11, 9))))
                .containsExactly("janelas[1].intervaloValido");
    }

    @Test
    void deveAcusarSoOCampoAusenteQuandoFaltaHorarioNaJanela() {
        assertThat(camposInvalidos(comJanelas(janela(null, LocalTime.of(9, 0)))))
                .containsExactly("janelas[0].horaInicio");
    }

    @Test
    void deveRecusarCamposObrigatoriosAusentes() {
        assertThat(camposInvalidos(new PerfilAtuacaoRequest(null, null, "  ", null, null)))
                .containsExactlyInAnyOrder("especialidades", "raioAtendimentoKm", "municipioBase", "ufBase", "janelas");
    }

    @Test
    void deveRecusarEspecialidadeNulaNaLista() {
        assertThat(camposInvalidos(new PerfilAtuacaoRequest(Arrays.asList((UUID) null), 10, "Campinas", Uf.SP,
                List.of()))).singleElement().asString().startsWith("especialidades[0]");
    }

    @Test
    void deveTirarEspacosDasPontasDoMunicipio() {
        assertThat(new PerfilAtuacaoRequest(List.of(), 10, "  Campinas ", Uf.SP, List.of()).municipioBase())
                .isEqualTo("Campinas");
    }
}
