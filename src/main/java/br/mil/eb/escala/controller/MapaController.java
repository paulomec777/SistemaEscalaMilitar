package br.mil.eb.escala.controller;

import br.mil.eb.escala.dto.DiaEscala;
import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.service.AvisoService;
import br.mil.eb.escala.service.EscalaService;
import br.mil.eb.escala.service.MissaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Mapa semanal de permanência, missões e avisos (público, como o painel). */
@Controller
public class MapaController {

    private final EscalaService escalaService;
    private final AvisoService avisoService;
    private final MissaoService missaoService;

    public MapaController(EscalaService escalaService, AvisoService avisoService, MissaoService missaoService) {
        this.escalaService = escalaService;
        this.avisoService = avisoService;
        this.missaoService = missaoService;
    }

    /** @param semana 0 = esta semana, 1 = próxima, -1 = anterior */
    @GetMapping("/mapa")
    public String mapa(@RequestParam(value = "semana", defaultValue = "0") int semana, Model model) {
        semana = Math.max(-8, Math.min(8, semana)); // limita a navegação a ~2 meses

        LocalDate segunda = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(semana);
        LocalDate domingo = segunda.plusDays(6);

        List<DiaEscala> dias = escalaService.projetarSemana(segunda);

        model.addAttribute("dias", dias);
        model.addAttribute("semana", semana);
        model.addAttribute("segunda", segunda);
        model.addAttribute("domingo", domingo);
        model.addAttribute("avisos", avisoService.avisosDoPeriodo(segunda, domingo));
        model.addAttribute("ultimaHora", missaoService.missoesDeUltimaHora());
        model.addAttribute("totalMissoes", dias.stream().mapToInt(d -> d.getMissoes().size()).sum());
        // Efetivo para o ADM arrastar no modo edição (a tela só mostra para quem é ADM)
        model.addAttribute("efetivo", escalaService.getTodosMilitaresParaDashboard());
        return "mapa";
    }

    /** Grava o que o ADM arrastou no mapa (só ADM: a rota está debaixo de /admin). */
    @PostMapping("/admin/mapa/salvar")
    public String salvarAjustes(@RequestParam(value = "ajustes", required = false) List<String> ajustes,
                                @RequestParam(value = "semana", defaultValue = "0") int semana,
                                Principal principal,
                                RedirectAttributes attributes) {
        try {
            int total = escalaService.salvarAjustesDoMapa(ajustes, principal.getName());
            attributes.addFlashAttribute("mensagem", total == 0
                    ? "Nenhuma alteração para salvar."
                    : total + " alteração(ões) salva(s). A previsão dos outros dias foi recalculada.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage() + " Nada foi salvo.");
        }
        return "redirect:/mapa?semana=" + semana;
    }
}
