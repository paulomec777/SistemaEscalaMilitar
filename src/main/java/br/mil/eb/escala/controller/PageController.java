package br.mil.eb.escala.controller;

import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.service.EscalaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final EscalaService escalaService;

    public PageController(EscalaService escalaService) {
        this.escalaService = escalaService;
    }
    
    @GetMapping("/login")
    public String getLoginPage() {
        return "login";
    }
    
    @GetMapping("/dashboard")
    public String getDashboardPage(Model model) {
        // Busca a lista completa para a tabela
        var militares = escalaService.getTodosMilitaresParaDashboard();
        
        // Busca quem tira o serviço hoje e quem é o reserva
        var proximo = escalaService.getProximoPermanencia();
        var substituto = escalaService.getProximoSubstituto();

        model.addAttribute("listaMilitares", militares);
        model.addAttribute("servicoHoje", proximo);       
        model.addAttribute("substitutoAmanha", substituto); 
        
        // PROTEÇÃO: Garante que se não houver militar apto, o ID passado seja null de forma segura
        Long proximoId = (proximo != null) ? proximo.getId() : null;
        model.addAttribute("proximoMilitarId", proximoId);

        return "dashboard";
    }
    
    @GetMapping("/")
    public String getRootPage() {
        return "redirect:/dashboard";
    }
}
