package com.limio.api.modulos.anuncio;

import java.util.List;

import com.limio.api.comum.portas.ResumoPerfilEmpregador;

/**
 * Resumo do Empregador pro painel "Início" (TICKET-0052): saudação ({@code perfil}) e os anúncios
 * abertos dele, da data pretendida mais próxima pra mais distante. Os contadores derivam da lista.
 */
public record PainelEmpregador(ResumoPerfilEmpregador perfil, List<AnuncioNoPainel> anuncios) {

    public int anunciosAbertos() {
        return anuncios.size();
    }

    /** Candidaturas {@code ENVIADA} somadas de todos os anúncios abertos — ainda sem resposta do Empregador. */
    public int novosCandidatos() {
        return anuncios.stream().mapToInt(AnuncioNoPainel::candidatos).sum();
    }
}
