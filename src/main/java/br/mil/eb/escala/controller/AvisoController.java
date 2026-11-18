package br.mil.eb.escala.controller;

import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.model.PrioridadeAviso;
import br.mil.eb.escala.service.AvisoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;

/** Quadro de avisos da semana, mantido pelo ADM. */
@Controller
@RequestMapping("/admin/avisos")
public class AvisoController {

    private final AvisoService avisoService;

    public AvisoController(AvisoService avisoService) {
        this.avisoService = avisoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("avisos", avisoService.todos());
        model.addAttribute("prioridades", PrioridadeAviso.values());
        model.addAttribute("hoje", LocalDate.now());
        model.addAttribute("fimDaSemana", LocalDate.now().plusDays(6));
        return "admin-avisos";
    }

    @PostMapping("/salvar")
    public String salvar(@RequestParam(value = "titulo") String titulo,
                         @RequestParam(value = "mensagem", required = false) String mensagem,
                         @RequestParam(value = "prioridade", defaultValue = "NORMAL") PrioridadeAviso prioridade,
                         @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
                         @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
                         Principal principal,
                         RedirectAttributes attributes) {
        try {
            avisoService.publicar(titulo, mensagem, prioridade, dataInicio, dataFim,
                    principal != null ? principal.getName() : null);
            attributes.addFlashAttribute("mensagem", "Aviso publicado!");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/avisos";
    }

    @PostMapping("/excluir/{id}")
    public String excluir(@PathVariable("id") Long id, RedirectAttributes attributes) {
        avisoService.excluir(id);
        attributes.addFlashAttribute("mensagem", "Aviso removido.");
        return "redirect:/admin/avisos";
    }
}
