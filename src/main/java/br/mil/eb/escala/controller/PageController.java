package br.mil.eb.escala.controller;

import br.mil.eb.escala.service.AvisoService;
import br.mil.eb.escala.service.EscalaService;
import br.mil.eb.escala.service.MissaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final EscalaService escalaService;
    private final AvisoService avisoService;
    private final MissaoService missaoService;

    public PageController(EscalaService escalaService, AvisoService avisoService, MissaoService missaoService) {
        this.escalaService = escalaService;
        this.avisoService = avisoService;
        this.missaoService = missaoService;
    }
    
    @GetMapping("/login")
    public String getLoginPage() {
        return "login";
    }
    
    //Agora o método responde TANTO para a raiz "/" quanto para "/dashboard"
    @GetMapping({"/", "/dashboard"})
    public String getDashboardPage(Model model) {
        //Busca a lista completa para a tabela
        var militares = escalaService.getTodosMilitaresParaDashboard();
        
        //Busca quem tira o serviço hoje e quem é o reserva
        var proximo = escalaService.getProximoPermanencia();
        var substituto = escalaService.getProximoSubstituto();

        model.addAttribute("listaMilitares", militares);
        model.addAttribute("servicoHoje", proximo);       
        model.addAttribute("substitutoAmanha", substituto); 
        model.addAttribute("avisos", avisoService.avisosVigentes());
        model.addAttribute("ultimaHora", missaoService.missoesDeUltimaHora());
        
        //Garante segurança caso a lista venha vazia
        Long proximoId = (proximo != null) ? proximo.getId() : null;
        model.addAttribute("proximoMilitarId", proximoId);
        //Renderiza a tela direto sem dar loops de redirecionamento
        return "dashboard"; 
    }
}
