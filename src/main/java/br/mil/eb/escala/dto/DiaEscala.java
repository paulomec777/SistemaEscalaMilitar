package br.mil.eb.escala.dto;

import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Missao;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Um dia do mapa semanal: quem está de permanência, missões, afastados e externos. */
@Getter
public class DiaEscala {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final LocalDate data;
    private final boolean expediente;
    private final String feriado;
    private final Militar titular;
    private final Militar reserva;
    private final List<Missao> missoes;
    private final List<Militar> afastados;
    private final List<Militar> externos;

    public DiaEscala(LocalDate data, boolean expediente, String feriado, Militar titular, Militar reserva,
                     List<Missao> missoes, List<Militar> afastados, List<Militar> externos) {
        this.data = data;
        this.expediente = expediente;
        this.feriado = feriado;
        this.titular = titular;
        this.reserva = reserva;
        this.missoes = missoes;
        this.afastados = afastados;
        this.externos = externos;
    }

    public boolean isHoje() {
        return data.isEqual(LocalDate.now());
    }

    public boolean isPassado() {
        return data.isBefore(LocalDate.now());
    }

    /** O titular do dia também está escalado numa missão (mas continua na permanência)? */
    public boolean isTitularEmMissao() {
        return estaEmMissao(titular);
    }

    public boolean isReservaEmMissao() {
        return estaEmMissao(reserva);
    }

    private boolean estaEmMissao(Militar militar) {
        return militar != null && missoes.stream()
                .anyMatch(mi -> mi.getMilitares().stream().anyMatch(m -> m.getId().equals(militar.getId())));
    }

    public boolean isTemUltimaHora() {
        return missoes.stream().anyMatch(Missao::isUltimaHora);
    }

    /** "Segunda", "Terça"... */
    public String getNomeDia() {
        String nome = data.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR).replace("-feira", "");
        return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
    }
}
