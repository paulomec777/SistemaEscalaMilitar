package br.mil.eb.escala.service;

import br.mil.eb.escala.dto.DiaEscala;
import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Missao;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EscalaService {

    @Autowired private MilitarRepository militarRepository;
    @Autowired private FeriadoRepository feriadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MissaoService missaoService;

    public List<Militar> getMilitaresOrdenadosParaEscala() {
        LocalDate hoje = LocalDate.now();
        LocalDate ontem = hoje.minusDays(1);
        List<Militar> todos = militarRepository.findAll();
        // Missão só tira da permanência quando o ADM marcou "afastar da permanência"
        Set<Long> afastadosPorMissao = missaoService.idsAfastadosPorMissao(hoje);
        
        return todos.stream()
                .filter(m -> m.estaAptoParaServico(hoje))
                .filter(m -> !afastadosPorMissao.contains(m.getId()))
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(ontem)) 
                .sorted(Comparator.comparing(Militar::getFolga).reversed()
                        .thenComparing(Militar::getDataUltimoServico, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // MÉTODO CORRIGIDO: Prioriza quem foi escalado manualmente para hoje
    public Militar getProximoPermanencia() {
        LocalDate hoje = LocalDate.now();
        List<Militar> todos = militarRepository.findAll();
        
        Militar trocadoParaHoje = todos.stream()
                .filter(m -> m.getDataUltimoServico() != null && m.getDataUltimoServico().isEqual(hoje))
                .findFirst()
                .orElse(null);
            
        if (trocadoParaHoje != null) return trocadoParaHoje;

        List<Militar> ordenados = getMilitaresOrdenadosParaEscala(); 
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    public Militar getProximoSubstituto() {
        Militar titular = getProximoPermanencia();
        return getMilitaresOrdenadosParaEscala().stream()
                .filter(m -> titular == null || !m.getId().equals(titular.getId()))
                .findFirst()
                .orElse(null);
    }

    public List<Militar> getTodosMilitaresParaDashboard() {
        return militarRepository.findAll().stream()
                .sorted(Comparator.comparing(Militar::isAtivoNaEscala).reversed()
                        .thenComparing(Militar::getFolga, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void atualizarFolgaManual(Long idMilitar, int novaFolga) {
        Militar militar = findMilitarById(idMilitar);
        if (militar != null) {
            militar.setFolga(novaFolga);
            militarRepository.save(militar);
        }
    }

    @Transactional
    public void atualizarDataUltimoServicoManual(Long idMilitar, LocalDate novaData) {
        Militar militar = findMilitarById(idMilitar);
        if (militar != null) {
            militar.setDataUltimoServico(novaData);
            
            LocalDate hoje = LocalDate.now();
            int folgaCalculada = 0;
            
            if (novaData.isBefore(hoje)) {
                folgaCalculada = (int) ChronoUnit.DAYS.between(novaData, hoje);
            }
            
            militar.setFolga(folgaCalculada);
            militarRepository.save(militar);
        }
    }

    // MOTOR DA MEIA-NOITE ATUALIZADO COM O ESCUDO DO SERVIÇO PAGO
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional 
    public void avancarDiaDaEscala() {
        LocalDate hoje = LocalDate.now(); // Amanhã (novo dia)
        LocalDate ontem = hoje.minusDays(1); // O dia que acabou de se encerrar
        boolean ehDiaDeServico = isDiaUtil(ontem); // Verifica se o dia que passou foi útil
        
        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            // Reativar afastamentos/doenças vencidas automaticamente
            if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
               (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                    m.setAtivoNaEscala(true);
                    m.setMotivoAfastamento(null);
                    m.setDataInicioAfastamento(null);
                    m.setDataFimAfastamento(null);
            }

            if (ehDiaDeServico) {
                // Se o militar trabalhou no dia que acabou de se encerrar (data do último serviço = ontem)
                if (m.getDataUltimoServico() != null && m.getDataUltimoServico().isEqual(ontem)) {
                    
                    // VERIFICA SE ELE TEM O ESCUDO DO SERVIÇO PAGO
                    if (m.isServicoPagoTemporario()) {
                        // Trabalhou pago: a folga NÃO zera. Ele ganha +1 dia normal e perde o escudo.
                        m.setFolga(m.getFolga() + 1);
                        m.setServicoPagoTemporario(false);
                    } else {
                        // Tirou serviço normal ou missão: zera a folga legitimamente.
                        m.setFolga(0); 
                    }
                    
                } else {
                    // Quem não trabalhou ganha +1 de folga
                    m.setFolga(m.getFolga() + 1); 
                }
            } else {
                // Finais de semana e feriados: ninguém tira serviço, todos ganham folga
                m.setFolga(m.getFolga() + 1);
            }
        }
        militarRepository.saveAll(todos); 
    }

    public List<Militar> getMilitaresAtivos() {
        return militarRepository.findAll().stream().filter(Militar::isAtivoNaEscala).collect(Collectors.toList());
    }
    
    public Militar findMilitarById(Long id) {
        return militarRepository.findById(id).orElse(null); 
    }

    public void salvarNovoMilitar(Militar militar) {
        militar.setAtivoNaEscala(true);
        if (militar.getDataUltimoServico() == null) militar.setDataUltimoServico(LocalDate.now());

        LocalDate hoje = LocalDate.now();
        LocalDate dataUltimoSv = militar.getDataUltimoServico();
        int folgaCalculada = 0;

        if (dataUltimoSv.isBefore(hoje)) {
            folgaCalculada = (int) ChronoUnit.DAYS.between(dataUltimoSv, hoje);
        }

        militar.setFolga(folgaCalculada); 
        militarRepository.save(militar);
    }
    
    public void editarMilitar(Militar dados) {
        Militar original = findMilitarById(dados.getId());
        if (original != null) {
            original.setGraduacao(dados.getGraduacao());
            original.setNomeGuerra(dados.getNomeGuerra());
            militarRepository.save(original);
        }
    }
    
    public void deletarMilitar(Long id) {
        if (militarRepository.existsById(id)) {
            missaoService.removerMilitarDasMissoes(id);
            militarRepository.deleteById(id);
        }
    }

    public void afastarMilitar(Long id, String motivo, LocalDate inicio, LocalDate fim) {
        Militar militar = findMilitarById(id);
        if (militar != null) {
            militar.setAtivoNaEscala(false); 
            militar.setMotivoAfastamento(motivo);
            militar.setDataInicioAfastamento(inicio);
            militar.setDataFimAfastamento(fim);
            militarRepository.save(militar);
        }
    }

    public List<Militar> getMilitaresInativos() {
        return militarRepository.findAll().stream().filter(m -> !m.isAtivoNaEscala()).toList();
    }
    
    public void reativarMilitar(Long id) {
        Militar militar = findMilitarById(id);
        if (militar != null && !militar.isAtivoNaEscala()) {
            militar.setAtivoNaEscala(true);
            militar.setMotivoAfastamento(null);
            militar.setDataInicioAfastamento(null);
            militar.setDataFimAfastamento(null);
            militarRepository.save(militar);
        }
    }
    
    private boolean isDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
        return !feriadoRepository.existsByData(data);
    }
    
    public void agendarServicoExterno(Long id, LocalDate inicio, LocalDate fim) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setDataInicioServicoExterno(inicio);
            m.setDataFimServicoExterno(fim); 
            militarRepository.save(m); 
        }
    }

    public void cancelarServicoExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setDataInicioServicoExterno(null);
            m.setDataFimServicoExterno(null); 
            militarRepository.save(m); 
        }
    }
    
    @Transactional
    public void processarTroca(Long idSai, Long idEntra, String tipoTroca, String funcaoAlvo) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);
        
        if (sai == null || entra == null) return;
        
        LocalDate hoje = LocalDate.now();
        if (tipoTroca == null) tipoTroca = "MISSAO";

        switch (tipoTroca) {
            case "PAGO":
                // Quem paga perde a folga, mas NÃO tem a data mudada para hoje (não assume o Dashboard)
                sai.setFolga(0);
                
                // Quem entra assume a data de hoje para aparecer no Dashboard...
                entra.setDataUltimoServico(hoje);
                
                // ... e ATIVA O ESCUDO para não zerar a folga à meia-noite!
                entra.setServicoPagoTemporario(true);
                break;
                
            case "MISSAO":
                // Força o substituto a ser exibido no Dashboard hoje (sem o escudo, pois a missão zera a folga dele à meia-noite)
                entra.setDataUltimoServico(hoje);
                break;
                
            case "PERMUTA":
                int folgaTemp = sai.getFolga();
                LocalDate dataTemp = sai.getDataUltimoServico();
                sai.setFolga(entra.getFolga());
                sai.setDataUltimoServico(entra.getDataUltimoServico());
                entra.setFolga(folgaTemp);
                entra.setDataUltimoServico(dataTemp);
                break;
        }
        
        militarRepository.save(sai);
        militarRepository.save(entra);
    }
    
    public List<Feriado> getTodosFeriados() { return feriadoRepository.findAll(); }

    public List<Usuario> getTodosUsuarios() { return usuarioRepository.findAll(); }

    @Transactional
    public void salvarNovoUsuario(Usuario usuario) {
        if (usuario.getSenha() != null && !usuario.getSenha().isEmpty()) {
            usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        }
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void deletarUsuario(Long id) {
        if (usuarioRepository.existsById(id)) usuarioRepository.deleteById(id);
    }

    // =====================================================================
    // MAPA SEMANAL: projeta a escala dia a dia, simulando a virada da meia-noite
    // =====================================================================

    /**
     * Monta os 7 dias da semana que começa em {@code segunda}.
     * Dias passados mostram quem tirou serviço (quando há registro).
     * Hoje usa a escala real (inclui trocas manuais).
     * Dias futuros são simulados com as mesmas regras do motor da meia-noite.
     */
    public List<DiaEscala> projetarSemana(LocalDate segunda) {
        LocalDate domingo = segunda.plusDays(6);
        LocalDate hoje = LocalDate.now();
        LocalDate inicioBusca = segunda.isBefore(hoje) ? segunda : hoje;

        List<Militar> todos = militarRepository.findAll();
        Map<LocalDate, List<Missao>> missoesPorDia = missaoService.missoesDoPeriodo(inicioBusca, domingo).stream()
                .collect(Collectors.groupingBy(Missao::getData));
        Map<LocalDate, String> feriados = feriadoRepository.findAll().stream()
                .filter(f -> !f.getData().isBefore(inicioBusca) && !f.getData().isAfter(domingo))
                .collect(Collectors.toMap(Feriado::getData, Feriado::getDescricao, (a, b) -> a));

        Map<LocalDate, Militar[]> escalaProjetada = simularEscala(todos, hoje, domingo, missoesPorDia);

        List<DiaEscala> semana = new ArrayList<>();
        for (LocalDate d = segunda; !d.isAfter(domingo); d = d.plusDays(1)) {
            final LocalDate dia = d;
            boolean expediente = isDiaUtil(dia);
            Militar titular = null;
            Militar reserva = null;

            if (dia.isBefore(hoje)) {
                titular = todos.stream()
                        .filter(m -> dia.equals(m.getDataUltimoServico()))
                        .findFirst().orElse(null);
            } else if (escalaProjetada.containsKey(dia)) {
                titular = escalaProjetada.get(dia)[0];
                reserva = escalaProjetada.get(dia)[1];
            }

            List<Militar> afastados = todos.stream().filter(m -> estaAfastadoEm(m, dia)).toList();
            List<Militar> externos = todos.stream().filter(m -> estaEmServicoExternoEm(m, dia)).toList();

            semana.add(new DiaEscala(dia, expediente, feriados.get(dia), titular, reserva,
                    missoesPorDia.getOrDefault(dia, List.of()), afastados, externos));
        }
        return semana;
    }

    /** Simula de hoje até {@code ate}. Retorna, por dia útil, [titular, reserva]. */
    private Map<LocalDate, Militar[]> simularEscala(List<Militar> todos, LocalDate hoje, LocalDate ate,
                                                    Map<LocalDate, List<Missao>> missoesPorDia) {
        Map<LocalDate, Militar[]> resultado = new HashMap<>();
        if (ate.isBefore(hoje)) return resultado;

        // Estado simulado (não mexe no banco)
        Map<Long, Integer> folga = new HashMap<>();
        Map<Long, LocalDate> ultimoServico = new HashMap<>();
        Map<Long, Boolean> servicoPago = new HashMap<>();
        for (Militar m : todos) {
            folga.put(m.getId(), m.getFolga());
            ultimoServico.put(m.getId(), m.getDataUltimoServico());
            servicoPago.put(m.getId(), m.isServicoPagoTemporario());
        }

        for (LocalDate d = hoje; !d.isAfter(ate); d = d.plusDays(1)) {
            final LocalDate dia = d;
            final LocalDate ontem = d.minusDays(1);

            if (dia.isAfter(hoje)) {
                // Mesma regra do avancarDiaDaEscala()
                boolean ontemFoiUtil = isDiaUtil(ontem);
                for (Militar m : todos) {
                    Long id = m.getId();
                    if (ontemFoiUtil && ontem.equals(ultimoServico.get(id))) {
                        if (servicoPago.get(id)) {
                            folga.put(id, folga.get(id) + 1);
                            servicoPago.put(id, false);
                        } else {
                            folga.put(id, 0);
                        }
                    } else {
                        folga.put(id, folga.get(id) + 1);
                    }
                }
            }

            if (!isDiaUtil(dia)) continue; // fim de semana e feriado: sem permanência

            Militar titular;
            Militar reserva;
            if (dia.isEqual(hoje)) {
                titular = getProximoPermanencia();
                reserva = getProximoSubstituto();
            } else {
                Set<Long> afastadosPorMissao = missoesPorDia.getOrDefault(dia, List.of()).stream()
                        .filter(Missao::isAfastaDaPermanencia)
                        .flatMap(mi -> mi.getMilitares().stream())
                        .map(Militar::getId)
                        .collect(Collectors.toSet());

                List<Militar> candidatos = todos.stream()
                        .filter(m -> estaAptoNaData(m, dia))
                        .filter(m -> !afastadosPorMissao.contains(m.getId()))
                        .filter(m -> !ontem.equals(ultimoServico.get(m.getId())))
                        .sorted(Comparator.comparing((Militar m) -> folga.get(m.getId()), Comparator.reverseOrder())
                                .thenComparing(m -> ultimoServico.get(m.getId()),
                                        Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList();
                titular = candidatos.isEmpty() ? null : candidatos.get(0);
                reserva = candidatos.size() < 2 ? null : candidatos.get(1);
            }

            if (titular != null) ultimoServico.put(titular.getId(), dia);
            resultado.put(dia, new Militar[]{titular, reserva});
        }
        return resultado;
    }

    /** Aptidão numa data futura, já considerando que afastamentos vencidos são reativados. */
    private boolean estaAptoNaData(Militar m, LocalDate data) {
        if (!m.isDisponivelManualmente()) return false;
        if (!m.isAtivoNaEscala()
                && (m.getDataFimAfastamento() == null || !data.isAfter(m.getDataFimAfastamento()))) {
            return false;
        }
        if (estaAfastadoEm(m, data)) return false;
        if (m.getDataInicioServicoExterno() != null && m.getDataFimServicoExterno() != null
                && !data.isBefore(m.getDataInicioServicoExterno())
                && !data.isAfter(m.getDataFimServicoExterno().plusDays(1))) { // regra das 48h
            return false;
        }
        return true;
    }

    private boolean estaAfastadoEm(Militar m, LocalDate data) {
        if (!m.isDisponivelManualmente()) return true;
        if (m.getDataInicioAfastamento() != null && m.getDataFimAfastamento() != null) {
            return !data.isBefore(m.getDataInicioAfastamento()) && !data.isAfter(m.getDataFimAfastamento());
        }
        return !m.isAtivoNaEscala();
    }

    private boolean estaEmServicoExternoEm(Militar m, LocalDate data) {
        return m.getDataInicioServicoExterno() != null && m.getDataFimServicoExterno() != null
                && !data.isBefore(m.getDataInicioServicoExterno())
                && !data.isAfter(m.getDataFimServicoExterno());
    }
}
