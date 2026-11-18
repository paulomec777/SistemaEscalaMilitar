package br.mil.eb.escala.controller;

import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.service.EscalaService;
import br.mil.eb.escala.service.MissaoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Lançamento de missões (inclusive as de última hora). */
@Controller
@RequestMapping("/admin/missoes")
public class MissaoController {

    private final MissaoService missaoService;
    private final EscalaService escalaService;

    public MissaoController(MissaoService missaoService, EscalaService escalaService) {
        this.missaoService = missaoService;
        this.escalaService = escalaService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("proximas", missaoService.proximas());
        model.addAttribute("recentes", missaoService.recentes());
        model.addAttribute("militares", escalaService.getTodosMilitaresParaDashboard());
        model.addAttribute("hoje", LocalDate.now());
        model.addAttribute("titularHoje", escalaService.getProximoPermanencia());
        return "admin-missoes";
    }

    @PostMapping("/salvar")
    public String salvar(@RequestParam(value = "titulo") String titulo,
                         @RequestParam(value = "descricao", required = false) String descricao,
                         @RequestParam(value = "local", required = false) String local,
                         @RequestParam(value = "data", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                         @RequestParam(value = "horario", required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horario,
                         @RequestParam(value = "ultimaHora", defaultValue = "false") boolean ultimaHora,
                         @RequestParam(value = "afastaDaPermanencia", defaultValue = "false") boolean afastaDaPermanencia,
                         @RequestParam(value = "militaresIds", required = false) List<Long> militaresIds,
                         Principal principal,
                         RedirectAttributes attributes) {
        try {
            missaoService.criar(titulo, descricao, local, data, horario, ultimaHora, afastaDaPermanencia, militaresIds,
                    principal != null ? principal.getName() : null);
            attributes.addFlashAttribute("mensagem", ultimaHora
                    ? "Missão de última hora lançada! Ela já aparece em alerta no painel e no mapa."
                    : "Missão lançada no mapa.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/missoes";
    }

    @PostMapping("/permanencia/{id}")
    public String alternarPermanencia(@PathVariable("id") Long id, RedirectAttributes attributes) {
        try {
            boolean afastou = missaoService.alternarAfastamentoDaPermanencia(id);
            attributes.addFlashAttribute("mensagem", afastou
                    ? "Os escalados dessa missão saíram da permanência no dia. A escala foi recalculada."
                    : "Os escalados dessa missão voltaram para a escala de permanência.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/missoes";
    }

    @PostMapping("/excluir/{id}")
    public String excluir(@PathVariable("id") Long id, RedirectAttributes attributes) {
        missaoService.excluir(id);
        attributes.addFlashAttribute("mensagem", "Missão cancelada.");
        return "redirect:/admin/missoes";
    }
}
