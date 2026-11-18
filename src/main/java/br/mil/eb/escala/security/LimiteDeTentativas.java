package br.mil.eb.escala.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueia o login por 15 minutos depois de 5 senhas erradas seguidas para o mesmo usuário.
 * Impede que alguém fique "chutando" senhas. Acertar a senha zera a contagem.
 * Fica na memória do servidor (reiniciar o sistema libera todos).
 */
@Component
public class LimiteDeTentativas {

    public static final int MAXIMO_DE_ERROS = 5;
    public static final Duration TEMPO_DE_BLOQUEIO = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(LimiteDeTentativas.class);

    private record Situacao(int erros, Instant bloqueadoAte) {}

    private final Map<String, Situacao> porLogin = new ConcurrentHashMap<>();

    public boolean estaBloqueado(String login) {
        Situacao s = porLogin.get(chave(login));
        return s != null && s.bloqueadoAte() != null && Instant.now().isBefore(s.bloqueadoAte());
    }

    public void registrarErro(String login) {
        porLogin.compute(chave(login), (k, s) -> {
            int erros = (s == null || (s.bloqueadoAte() != null && Instant.now().isAfter(s.bloqueadoAte()))) ? 1 : s.erros() + 1;
            if (erros >= MAXIMO_DE_ERROS) {
                log.warn("Login '{}' bloqueado por {} minutos após {} senhas erradas", k, TEMPO_DE_BLOQUEIO.toMinutes(), erros);
                return new Situacao(erros, Instant.now().plus(TEMPO_DE_BLOQUEIO));
            }
            return new Situacao(erros, null);
        });
    }

    public void registrarAcerto(String login) {
        porLogin.remove(chave(login));
    }

    private String chave(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
