package br.mil.eb.escala.controller;

import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.service.EscalaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class PageController {

    @Autowired
    private EscalaService escalaService;
    
    @GetMapping("/login")
    public String getLoginPage() {
        return "login";
    }
    
    @GetMapping("/dashboard")
    public String getDashboardPage(Model model) {
        List<Militar> militares = escalaService.getTodosMilitaresParaDashboard();
        Militar proximo = escalaService.getProximoDaEscala();
        Militar substituto = escalaService.getProximoSubstituto();

        model.addAttribute("listaMilitares", militares);
        model.addAttribute("servicoHoje", proximo);
        model.addAttribute("substitutoAmanha", substituto);
        
        if (proximo != null) {
            model.addAttribute("proximoMilitarId", proximo.getId()); 
        } else {
            model.addAttribute("proximoMilitarId", null);
        }

        return "dashboard";
    }
    
    @GetMapping("/")
    public String getRootPage() {
        return "redirect:/dashboard";
    }
}