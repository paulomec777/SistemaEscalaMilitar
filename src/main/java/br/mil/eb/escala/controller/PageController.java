package br.mil.eb.escala.controller;

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
    
    // CORREÇÃO MESTRE: Agora o método responde TANTO para a raiz "/" quanto para "/dashboard"
    @GetMapping({"/", "/dashboard"})
    public String getDashboardPage(Model model) {
        // Busca a lista completa para a tabela
        var militares = escalaService.getTodosMilitaresParaDashboard();
        
        // Busca quem tira o serviço hoje e quem é o reserva
        var proximo = escalaService.getProximoPermanencia();
        var substituto = escalaService.getProximoSubstituto();

        model.addAttribute("listaMilitares", militares);
        model.addAttribute("servicoHoje", proximo);       
        model.addAttribute("substitutoAmanha", substituto); 
        
        // Garante segurança caso a lista venha vazia
        Long proximoId = (proximo != null) ? proximo.getId() : null;
        model.addAttribute("proximoMilitarId", proximoId);

        return "dashboard"; // Renderiza a tela direto sem dar loops de redirecionamento
    }
}
