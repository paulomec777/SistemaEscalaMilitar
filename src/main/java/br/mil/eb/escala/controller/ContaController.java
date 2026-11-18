package br.mil.eb.escala.controller;

import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.service.EscalaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Tela "Minha senha": qualquer usuário logado troca a própria senha. */
@Controller
public class ContaController {

    private final EscalaService escalaService;

    public ContaController(EscalaService escalaService) {
        this.escalaService = escalaService;
    }

    @GetMapping("/conta/senha")
    public String telaSenha() {
        return "conta-senha";
    }

    @PostMapping("/conta/senha")
    public String trocarSenha(@RequestParam(value = "senhaAtual", required = false) String senhaAtual,
                              @RequestParam(value = "novaSenha", required = false) String novaSenha,
                              @RequestParam(value = "confirmacao", required = false) String confirmacao,
                              Principal principal,
                              RedirectAttributes attributes) {
        try {
            escalaService.trocarSenha(principal.getName(), senhaAtual, novaSenha, confirmacao);
            attributes.addFlashAttribute("mensagem", "Senha trocada. Use a nova senha no próximo acesso.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/conta/senha";
    }
}
