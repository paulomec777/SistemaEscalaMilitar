package br.mil.eb.escala.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MilitarTest {

    private final LocalDate hoje = LocalDate.now();

    private Militar militar() {
        Militar m = new Militar();
        m.setId(1L);
        m.setGraduacao("Cb");
        m.setNomeGuerra("Teste");
        return m;
    }

    @Test
    void militarSemImpedimentoEstaApto() {
        Militar m = militar();
        assertTrue(m.estaAptoParaServico(hoje));
        assertEquals("APTO", m.getStatus());
    }

    @Test
    void impedimentoManualDoAdmTiraDaEscala() {
        Militar m = militar();
        m.setDisponivelManualmente(false);
        assertFalse(m.estaAptoParaServico(hoje));
        assertEquals("AFASTADO", m.getStatus());
    }

    @Test
    void afastamentoBloqueiaSoDentroDoPeriodo() {
        Militar m = militar();
        m.setDataInicioAfastamento(hoje.plusDays(5));
        m.setDataFimAfastamento(hoje.plusDays(10));

        assertTrue(m.estaAptoParaServico(hoje));
        assertFalse(m.estaAptoParaServico(hoje.plusDays(5)));
        assertFalse(m.estaAptoParaServico(hoje.plusDays(10)));
        assertTrue(m.estaAptoParaServico(hoje.plusDays(11)));
    }

    @Test
    void feriasMarcadasParaODepoisNaoMostramAfastadoHoje() {
        Militar m = militar();
        m.setDataInicioAfastamento(hoje.plusDays(30));
        m.setDataFimAfastamento(hoje.plusDays(45));
        assertEquals("APTO", m.getStatus());
    }

    @Test
    void servicoExternoTemRegraDas48Horas() {
        Militar m = militar();
        m.setDataInicioServicoExterno(hoje);
        m.setDataFimServicoExterno(hoje.plusDays(1));

        assertFalse(m.estaAptoParaServico(hoje));             // durante
        assertFalse(m.estaAptoParaServico(hoje.plusDays(1))); // último dia
        assertFalse(m.estaAptoParaServico(hoje.plusDays(2))); // descanso (48h)
        assertTrue(m.estaAptoParaServico(hoje.plusDays(3)));  // volta à escala
        assertEquals("EXTERNO", m.getStatus());
    }

    @Test
    void quemJaVoltouDoExternoNaoFicaComStatusExterno() {
        Militar m = militar();
        m.setDataInicioServicoExterno(hoje.minusDays(10));
        m.setDataFimServicoExterno(hoje.minusDays(8));
        assertEquals("APTO", m.getStatus());
    }
}
