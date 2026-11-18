package br.mil.eb.escala.service;

import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.exception.Textos;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Missao;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.MissaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MissaoService {

    private final MissaoRepository missaoRepository;
    private final MilitarRepository militarRepository;

    public MissaoService(MissaoRepository missaoRepository, MilitarRepository militarRepository) {
        this.missaoRepository = missaoRepository;
        this.militarRepository = militarRepository;
    }

    public List<Missao> missoesDoPeriodo(LocalDate inicio, LocalDate fim) {
        return missaoRepository.findByDataBetweenOrderByDataAscHorarioAsc(inicio, fim);
    }

    /** Missões urgentes de hoje em diante (alerta do painel). */
    public List<Missao> missoesDeUltimaHora() {
        return missaoRepository.findByUltimaHoraTrueAndDataGreaterThanEqualOrderByDataAscHorarioAsc(LocalDate.now());
    }

    public List<Missao> proximas() {
        return missaoRepository.findByDataGreaterThanEqualOrderByDataAscHorarioAsc(LocalDate.now());
    }

    public List<Missao> recentes() {
        return missaoRepository.findTop10ByDataLessThanOrderByDataDescHorarioDesc(LocalDate.now());
    }

    /**
     * IDs dos militares que o ADM afastou da permanência nessa data por causa de missão.
     * Missões sem essa marcação não tiram ninguém da escala.
     */
    public Set<Long> idsAfastadosPorMissao(LocalDate data) {
        Set<Long> ids = new HashSet<>();
        for (Missao missao : missaoRepository.findByDataOrderByHorarioAsc(data)) {
            if (missao.isAfastaDaPermanencia()) {
                missao.getMilitares().forEach(m -> ids.add(m.getId()));
            }
        }
        return ids;
    }

    @Transactional
    public Missao criar(String titulo, String descricao, String local, LocalDate data, LocalTime horario,
                        boolean ultimaHora, boolean afastaDaPermanencia, List<Long> militaresIds, String autor) {
        titulo = Textos.obrigatorio(titulo, "o nome da missão", 120);
        descricao = Textos.opcional(descricao, "as instruções", 1000);
        local = Textos.opcional(local, "o local", 120);
        if (data == null) {
            throw new RegraNegocioException("Informe a data da missão.");
        }
        if (data.isBefore(LocalDate.now())) {
            throw new RegraNegocioException("Não dá para lançar missão em data que já passou.");
        }
        if (militaresIds == null || militaresIds.isEmpty()) {
            throw new RegraNegocioException("Escale pelo menos um militar para a missão.");
        }

        Set<Militar> escalados = new LinkedHashSet<>(militarRepository.findAllById(militaresIds));
        if (escalados.isEmpty()) {
            throw new RegraNegocioException("Nenhum dos militares escolhidos foi encontrado.");
        }

        Missao missao = new Missao();
        missao.setTitulo(titulo);
        missao.setDescricao(descricao);
        missao.setLocal(local);
        missao.setData(data);
        missao.setHorario(horario);
        missao.setUltimaHora(ultimaHora);
        missao.setAfastaDaPermanencia(afastaDaPermanencia);
        missao.setMilitares(escalados);
        missao.setAutor(autor);
        missao.setCriadoEm(LocalDateTime.now());
        return missaoRepository.save(missao);
    }

    /** Liga/desliga o afastamento da permanência numa missão já lançada. Retorna o novo valor. */
    @Transactional
    public boolean alternarAfastamentoDaPermanencia(Long id) {
        Missao missao = missaoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Missão não encontrada."));
        missao.setAfastaDaPermanencia(!missao.isAfastaDaPermanencia());
        missaoRepository.save(missao);
        return missao.isAfastaDaPermanencia();
    }

    @Transactional
    public void excluir(Long id) {
        missaoRepository.deleteById(id);
    }

    /** Tira o militar de todas as missões (usado antes de excluí-lo do sistema). */
    @Transactional
    public void removerMilitarDasMissoes(Long militarId) {
        for (Missao missao : missaoRepository.findAll()) {
            if (missao.getMilitares().removeIf(m -> m.getId().equals(militarId))) {
                missaoRepository.save(missao);
            }
        }
    }
}
