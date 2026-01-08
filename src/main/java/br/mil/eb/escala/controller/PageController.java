package br.mil.eb.escala.controller;

import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.service.EscalaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final EscalaService escalaService;

    // Injeção de dependência via construtor (Melhor prática do Spring)
    public PageController(EscalaService escalaService) {
        this.escalaService = escalaService;
    }
    
    @GetMapping("/login")
    public String getLoginPage() {
        return "login";
    }
    
    @GetMapping("/dashboard")
    public String getDashboardPage(Model model) {
        // 'var' detecta automaticamente o tipo (Java 10+)
        var militares = escalaService.getTodosMilitaresParaDashboard();
        var proximo = escalaService.getProximoDaEscala();
        var substituto = escalaService.getProximoSubstituto();

        model.addAttribute("listaMilitares", militares);
        model.addAttribute("servicoHoje", proximo);
        model.addAttribute("substitutoAmanha", substituto);
        
        // Lógica simplificada: Se proximo existir pega o ID, senão nulo.
        Long proximoId = (proximo != null) ? proximo.getId() : null;
        model.addAttribute("proximoMilitarId", proximoId);

        return "dashboard";
    }
    
    @GetMapping("/")
    public String getRootPage() {
        return "redirect:/dashboard";
    }
}