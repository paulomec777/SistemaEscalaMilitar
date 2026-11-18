package br.mil.eb.escala.service;

import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.model.Aviso;
import br.mil.eb.escala.model.PrioridadeAviso;
import br.mil.eb.escala.repository.AvisoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AvisoService {

    private final AvisoRepository avisoRepository;

    public AvisoService(AvisoRepository avisoRepository) {
        this.avisoRepository = avisoRepository;
    }

    /** Avisos válidos hoje, os mais urgentes primeiro. */
    public List<Aviso> avisosVigentes() {
        LocalDate hoje = LocalDate.now();
        return avisosDoPeriodo(hoje, hoje);
    }

    /** Avisos que aparecem em algum dia do período, os mais urgentes primeiro. */
    public List<Aviso> avisosDoPeriodo(LocalDate inicio, LocalDate fim) {
        return avisoRepository
                .findByDataFimGreaterThanEqualAndDataInicioLessThanEqualOrderByDataInicioAsc(inicio, fim)
                .stream()
                .sorted(Comparator.comparing(Aviso::getPrioridade, Comparator.reverseOrder())
                        .thenComparing(Aviso::getDataInicio))
                .toList();
    }

    public List<Aviso> todos() {
        return avisoRepository.findAllByOrderByDataInicioDesc();
    }

    @Transactional
    public Aviso publicar(String titulo, String mensagem, PrioridadeAviso prioridade,
                          LocalDate inicio, LocalDate fim, String autor) {
        if (titulo == null || titulo.isBlank()) {
            throw new RegraNegocioException("Informe o título do aviso.");
        }
        if (inicio == null) inicio = LocalDate.now();
        if (fim == null) fim = inicio;
        if (fim.isBefore(inicio)) {
            throw new RegraNegocioException("A data final do aviso não pode ser antes da data inicial.");
        }

        Aviso aviso = new Aviso();
        aviso.setTitulo(titulo.trim());
        aviso.setMensagem(mensagem == null ? null : mensagem.trim());
        aviso.setPrioridade(prioridade == null ? PrioridadeAviso.NORMAL : prioridade);
        aviso.setDataInicio(inicio);
        aviso.setDataFim(fim);
        aviso.setAutor(autor);
        aviso.setCriadoEm(LocalDateTime.now());
        return avisoRepository.save(aviso);
    }

    @Transactional
    public void excluir(Long id) {
        avisoRepository.deleteById(id);
    }
}
