package br.mil.eb.escala.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Último recurso: se algo escapar das validações (texto grande demais, valor duplicado no banco),
 * o usuário volta para a tela em que estava com uma mensagem, em vez de ver um erro 500.
 */
@ControllerAdvice
public class TratamentoDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratamentoDeErros.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String dadoInvalido(DataIntegrityViolationException e, HttpServletRequest request, RedirectAttributes attributes) {
        log.warn("Dado recusado pelo banco em {}: {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        attributes.addFlashAttribute("erro", "Não foi possível salvar: algum campo está grande demais ou já existe no sistema.");
        return "redirect:" + voltar(request);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public String regra(RegraNegocioException e, HttpServletRequest request, RedirectAttributes attributes) {
        attributes.addFlashAttribute("erro", e.getMessage());
        return "redirect:" + voltar(request);
    }

    // Volta para a página de onde veio (só o caminho, nunca outro site)
    private String voltar(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String caminho = uri.getRawPath();
                if (caminho != null && caminho.startsWith("/") && !caminho.startsWith("//")) {
                    return caminho + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                }
            } catch (IllegalArgumentException ignorado) {
                // Referer estranho: cai no painel
            }
        }
        return "/dashboard";
    }
}
