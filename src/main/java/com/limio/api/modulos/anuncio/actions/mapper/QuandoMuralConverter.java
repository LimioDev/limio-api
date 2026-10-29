package com.limio.api.modulos.anuncio.actions.mapper;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.enums.QuandoMural;

/**
 * {@code ?quando=} aceita {@code qualquer}, {@code esta-semana} e {@code fim-de-semana} (também com espaço
 * ou sublinhado). Valor desconhecido vira 400 {@code VALIDACAO_FALHOU}, como qualquer parâmetro inválido.
 */
@Component
public class QuandoMuralConverter implements Converter<String, QuandoMural> {

    @Override
    public QuandoMural convert(String valor) {
        return QuandoMural.valueOf(valor.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_"));
    }
}
