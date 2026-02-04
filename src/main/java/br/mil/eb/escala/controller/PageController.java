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
        
        // CORREÇÃO AQUI: Mudamos o nome do método no Service para 'getProximoPermanencia'
        var proximo = escalaService.getProximoPermanencia();
        
        // O substituto (o segundo da fila)
        var substituto = escalaService.getProximoSubstituto();

        model.addAttribute("listaMilitares", militares);
        model.addAttribute("servicoHoje", proximo);       // Quem tira o serviço
        model.addAttribute("substitutoAmanha", substituto); // Quem é o reserva/próximo
        
        // Passa o ID do próximo para o botão de "Trocar" no front-end saber quem tirar
        Long proximoId = (proximo != null) ? proximo.getId() : null;
        model.addAttribute("proximoMilitarId", proximoId);

        return "dashboard";
    }
    
    @GetMapping("/")
    public String getRootPage() {
        return "redirect:/dashboard";
    }
}