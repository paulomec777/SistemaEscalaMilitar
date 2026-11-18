package br.mil.eb.escala.controller;

import br.mil.eb.escala.dto.DiaEscala;
import br.mil.eb.escala.service.AvisoService;
import br.mil.eb.escala.service.EscalaService;
import br.mil.eb.escala.service.MissaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        return "mapa";
    }
}
