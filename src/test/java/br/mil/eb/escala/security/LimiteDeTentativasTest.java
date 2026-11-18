package br.mil.eb.escala.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LimiteDeTentativasTest {

    @Test
    void bloqueiaDepoisDeCincoErrosSeguidos() {
        LimiteDeTentativas limite = new LimiteDeTentativas();
        for (int i = 0; i < 4; i++) limite.registrarErro("sgt.silva");
        assertFalse(limite.estaBloqueado("sgt.silva"));
        limite.registrarErro("sgt.silva");
        assertTrue(limite.estaBloqueado("sgt.silva"));
        assertTrue(limite.estaBloqueado("SGT.SILVA ")); // maiúsculas e espaços não driblam o bloqueio
        assertFalse(limite.estaBloqueado("cb.souza"));  // outros usuários não são afetados
    }

    @Test
    void acertarASenhaZeraAContagem() {
        LimiteDeTentativas limite = new LimiteDeTentativas();
        for (int i = 0; i < 4; i++) limite.registrarErro("sgt.silva");
        limite.registrarAcerto("sgt.silva");
        limite.registrarErro("sgt.silva");
        assertFalse(limite.estaBloqueado("sgt.silva"));
    }
}
