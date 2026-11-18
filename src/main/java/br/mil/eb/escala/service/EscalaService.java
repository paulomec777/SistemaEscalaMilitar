package br.mil.eb.escala.service;

import br.mil.eb.escala.dto.DiaEscala;
import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.exception.Textos;
import br.mil.eb.escala.model.EscalaFixada;
import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Missao;
import br.mil.eb.escala.model.PapelEscala;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.TipoTroca;
import br.mil.eb.escala.model.Troca;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.EscalaFixadaRepository;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.TrocaRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final Logger log = LoggerFactory.getLogger(EscalaService.class);

    @Autowired private MilitarRepository militarRepository;
    @Autowired private FeriadoRepository feriadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MissaoService missaoService;
    @Autowired private TrocaRepository trocaRepository;
    @Autowired private EscalaFixadaRepository escalaFixadaRepository;

    public List<Militar> getMilitaresOrdenadosParaEscala() {
        return getMilitaresOrdenadosParaEscala(LocalDate.now());
    }

    // Fila do dia: aptos, fora de quem o ADM afastou por missão e de quem saiu de serviço na véspera
    private List<Militar> getMilitaresOrdenadosParaEscala(LocalDate dia) {
        LocalDate vespera = dia.minusDays(1);
        List<Militar> todos = militarRepository.findAll();
        // Missão só tira da permanência quando o ADM marcou "afastar da permanência"
        Set<Long> afastadosPorMissao = missaoService.idsAfastadosPorMissao(dia);
        
        return todos.stream()
                .filter(m -> m.estaAptoParaServico(dia))
                .filter(m -> !afastadosPorMissao.contains(m.getId()))
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(vespera)) 
                .sorted(Comparator.comparing(Militar::getFolga).reversed()
                        .thenComparing(Militar::getDataUltimoServico, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // Prioriza quem foi escalado manualmente para hoje (troca), depois o fixado no mapa, depois a fila
    public Militar getProximoPermanencia() {
        return getTitularDoDia(LocalDate.now());
    }

    private Militar getTitularDoDia(LocalDate dia) {
        List<Militar> todos = militarRepository.findAll();
        
        Militar trocadoParaODia = todos.stream()
                .filter(m -> m.getDataUltimoServico() != null && m.getDataUltimoServico().isEqual(dia))
                .findFirst()
                .orElse(null);
            
        if (trocadoParaODia != null) return trocadoParaODia;

        // Militar fixado pelo ADM no mapa para o dia (se continuar disponível)
        Militar fixado = getFixadoValido(dia, PapelEscala.TITULAR);
        if (fixado != null) return fixado;

        List<Militar> ordenados = getMilitaresOrdenadosParaEscala(dia); 
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    public Militar getProximoSubstituto() {
        Militar titular = getProximoPermanencia();
        // Quem saiu do serviço de hoje numa troca (doente, dispensado...) não vira reserva
        Set<Long> sairamHoje = getTrocasDoServicoDeHoje().stream()
                .map(t -> t.getSai().getId())
                .collect(Collectors.toSet());

        Militar fixado = getFixadoValido(LocalDate.now(), PapelEscala.RESERVA);
        if (fixado != null && (titular == null || !fixado.getId().equals(titular.getId()))
                && !sairamHoje.contains(fixado.getId())) return fixado;

        return getMilitaresOrdenadosParaEscala().stream()
                .filter(m -> titular == null || !m.getId().equals(titular.getId()))
                .filter(m -> !sairamHoje.contains(m.getId()))
                .findFirst()
                .orElse(null);
    }

    // Militar fixado pelo ADM nessa data/posição, se ainda estiver disponível nela
    private Militar getFixadoValido(LocalDate data, PapelEscala papel) {
        return escalaFixadaRepository.findByDataAndPapel(data, papel)
                .map(EscalaFixada::getMilitar)
                .filter(m -> m.estaAptoParaServico(data))
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

    /**
     * Registra quem tirou o serviço do dia que terminou, se ninguém foi registrado
     * (troca ou data salva pelo ADM). Roda no começo da virada da meia-noite, antes das folgas,
     * ainda com o estado daquele dia. Sem isso, se o ADM esquecesse de salvar a data,
     * o titular não zerava a folga e caía de novo no topo da escala.
     */
    private void registrarServicoDe(LocalDate dia, List<Militar> todos) {
        if (!isDiaUtil(dia)) return; // fim de semana e feriado não têm permanência
        boolean jaRegistrado = todos.stream().anyMatch(m -> dia.equals(m.getDataUltimoServico()));
        if (jaRegistrado) return;

        Militar titular = getTitularDoDia(dia);
        if (titular != null) {
            titular.setDataUltimoServico(dia);
            log.info("Serviço de {} registrado automaticamente para {} {}", dia,
                    titular.getGraduacao(), titular.getNomeGuerra());
        } else {
            log.warn("Nenhum militar disponível para a permanência de {}", dia);
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

        // Quem tirou o serviço de ontem fica registrado antes de calcular as folgas
        registrarServicoDe(ontem, todos);

        for (Militar m : todos) {
            // Reativar afastamentos vencidos: só no dia SEGUINTE ao fim
            // (antes reativava no próprio último dia do afastamento)
            if (m.getDataFimAfastamento() != null && m.getDataFimAfastamento().isBefore(hoje)) {
                    m.setAtivoNaEscala(true);
                    m.setMotivoAfastamento(null);
                    m.setDataInicioAfastamento(null);
                    m.setDataFimAfastamento(null);
            }

            // Afastamento lançado com antecedência começa hoje: sai da escala
            if (m.isAtivoNaEscala() && m.getDataInicioAfastamento() != null
                    && !m.getDataInicioAfastamento().isAfter(hoje)) {
                    m.setAtivoNaEscala(false);
            }

            // Serviço externo encerrado (incluindo as 48h): limpa para o status voltar a APTO
            if (m.getDataFimServicoExterno() != null && m.getDataFimServicoExterno().plusDays(1).isBefore(hoje)) {
                    m.setDataInicioServicoExterno(null);
                    m.setDataFimServicoExterno(null);
            }

            if (ehDiaDeServico && m.isPagouServico()) {
                // Pagou o serviço numa troca: conta como se tivesse tirado ontem
                m.setFolga(0);
                m.setDataUltimoServico(ontem);
                m.setPagouServico(false);
            } else if (ehDiaDeServico) {
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
                m.setPagouServico(false);
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
        validarNomeEGraduacao(militar);
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
        validarNomeEGraduacao(dados);
        Militar original = findMilitarById(dados.getId());
        if (original != null) {
            original.setGraduacao(dados.getGraduacao().trim());
            original.setNomeGuerra(dados.getNomeGuerra().trim());
            militarRepository.save(original);
        }
    }
    
    public void deletarMilitar(Long id) {
        if (militarRepository.existsById(id)) {
            missaoService.removerMilitarDasMissoes(id);
            trocaRepository.deleteAll(trocaRepository.findBySaiIdOrEntraId(id, id));
            escalaFixadaRepository.deleteAll(escalaFixadaRepository.findByMilitarId(id));
            militarRepository.deleteById(id);
        }
    }

    public void afastarMilitar(Long id, String motivo, LocalDate inicio, LocalDate fim) {
        Militar militar = findMilitarById(id);
        if (militar == null) throw new RegraNegocioException("Selecione o militar.");
        motivo = Textos.obrigatorio(motivo, "o motivo do afastamento", 200);
        if (inicio == null || fim == null) throw new RegraNegocioException("Informe as datas de início e fim.");
        if (fim.isBefore(inicio)) throw new RegraNegocioException("A data de fim não pode ser antes da data de início.");

        // Afastamento que já começou tira da escala agora; o futuro só no dia do início
        // (quem cuida disso é o motor da meia-noite e o estaAptoParaServico pelas datas)
        militar.setAtivoNaEscala(inicio.isAfter(LocalDate.now()));
        militar.setMotivoAfastamento(motivo.trim());
        militar.setDataInicioAfastamento(inicio);
        militar.setDataFimAfastamento(fim);
        militarRepository.save(militar);
    }

    public List<Militar> getMilitaresInativos() {
        // Inclui afastamentos agendados para o futuro, para o ADM poder cancelar
        return militarRepository.findAll().stream()
                .filter(m -> !m.isAtivoNaEscala() || m.getDataFimAfastamento() != null)
                .toList();
    }
    
    public void reativarMilitar(Long id) {
        Militar militar = findMilitarById(id);
        if (militar != null) {
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
        if (m == null) throw new RegraNegocioException("Selecione o militar.");
        if (inicio == null || fim == null) throw new RegraNegocioException("Informe as datas de início e retorno.");
        if (fim.isBefore(inicio)) throw new RegraNegocioException("A data de retorno não pode ser antes da data de início.");
        m.setDataInicioServicoExterno(inicio);
        m.setDataFimServicoExterno(fim);
        militarRepository.save(m);
    }

    public void cancelarServicoExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setDataInicioServicoExterno(null);
            m.setDataFimServicoExterno(null); 
            militarRepository.save(m); 
        }
    }
    
    /** Trocas valendo hoje (a mais recente primeiro). */
    public List<Troca> getTrocasDeHoje() {
        return trocaRepository.findByDataAndDesfeitaFalseOrderByCriadoEmDesc(LocalDate.now());
    }

    public List<Troca> getHistoricoDeTrocas() {
        return trocaRepository.findTop20ByOrderByCriadoEmDesc();
    }

    /**
     * Quem a escala tinha colocado hoje antes de qualquer troca.
     * É sempre ele quem "sai" numa troca nova (a troca anterior do dia é desfeita antes).
     */
    public Militar getTitularOriginalDeHoje() {
        List<Troca> trocas = getTrocasDoServicoDeHoje();
        if (!trocas.isEmpty()) return trocas.get(trocas.size() - 1).getSai(); // a primeira do dia
        return getProximoPermanencia();
    }

    // Trocas de hoje que mudaram quem tira o serviço (a mais recente primeiro)
    private List<Troca> getTrocasDoServicoDeHoje() {
        return getTrocasDeHoje().stream().filter(Troca::isServicoDeHoje).toList();
    }

    /**
     * Motivo pelo qual NÃO dá para trocar hoje, ou null se pode.
     * (A tela usa isso para avisar o ADM antes de ele preencher o formulário.)
     */
    public String motivoParaNaoTrocarHoje() {
        LocalDate hoje = LocalDate.now();
        if (!isDiaUtil(hoje)) return "Hoje não tem permanência (fim de semana ou feriado).";
        Militar titular = getTitularOriginalDeHoje();
        if (titular == null) return "Não há titular escalado hoje.";
        return null;
    }

    /**
     * Troca feita pelo ADM. Regras de cada tipo em {@link TipoTroca}.
     *
     * Se quem SAI é o titular de hoje, quem entra assume o serviço de hoje. O ADM pode trocar
     * quantas vezes precisar: a troca anterior do serviço é desfeita primeiro (todos voltam como
     * estavam) e a nova parte do titular original.
     *
     * Se quem sai NÃO está de serviço hoje, a troca mexe só na fila (folgas), conforme o tipo:
     *   Missão: quem entra zera a folga na virada (conta como serviço); quem sai mantém.
     *   Pago: quem sai zera a folga na virada; quem entra mantém.
     *   Permuta: os dois trocam de lugar na fila na hora.
     */
    @Transactional
    public Troca processarTroca(Long idSai, Long idEntra, TipoTroca tipo, String autor) {
        String impedimento = motivoParaNaoTrocarHoje();
        if (impedimento != null) throw new RegraNegocioException(impedimento);
        if (tipo == null) throw new RegraNegocioException("Escolha o tipo da troca.");
        if (idSai == null) throw new RegraNegocioException("Escolha quem sai.");
        if (idEntra == null) throw new RegraNegocioException("Escolha quem entra.");
        if (idSai.equals(idEntra)) throw new RegraNegocioException("Quem sai e quem entra precisam ser militares diferentes.");

        LocalDate hoje = LocalDate.now();
        Militar titularOriginal = getTitularOriginalDeHoje();
        boolean servicoDeHoje = idSai.equals(titularOriginal.getId());

        if (servicoDeHoje && getTrocasDoServicoDeHoje().isEmpty() && hoje.equals(titularOriginal.getDataUltimoServico())) {
            // A data de hoje foi salva na mão no painel: não dá para saber como ele estava antes
            throw new RegraNegocioException("O serviço de hoje de " + titularOriginal.getGraduacao() + " " + titularOriginal.getNomeGuerra()
                    + " foi lançado pela data no painel. Corrija a data dele no painel antes de trocá-lo.");
        }

        if (servicoDeHoje) {
            // Desfaz a(s) troca(s) anterior(es) do serviço de hoje: quem tinha entrado volta como estava
            for (Troca anterior : getTrocasDoServicoDeHoje()) {
                restaurar(anterior);
            }
        }

        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);
        if (sai == null || entra == null) throw new RegraNegocioException("Militar não encontrado.");

        if (servicoDeHoje) {
            if (!entra.estaAptoParaServico(hoje) || missaoService.idsAfastadosPorMissao(hoje).contains(entra.getId())) {
                throw new RegraNegocioException(entra.getNomeGuerra() + " não está disponível para o serviço hoje.");
            }
            if (hoje.minusDays(1).equals(entra.getDataUltimoServico())) {
                throw new RegraNegocioException(entra.getNomeGuerra() + " saiu de serviço ontem e não pode entrar hoje.");
            }
        } else {
            Militar noServico = getProximoPermanencia();
            if (noServico != null && (noServico.getId().equals(entra.getId()) || noServico.getId().equals(sai.getId()))) {
                throw new RegraNegocioException("Para mexer em quem está de serviço hoje, escolha o titular em \"Sai do serviço\".");
            }
        }

        // Guarda como os dois estavam, para poder desfazer depois
        Troca troca = new Troca();
        troca.setData(hoje);
        troca.setTipo(tipo);
        troca.setServicoDeHoje(servicoDeHoje);
        troca.setSai(sai);
        troca.setEntra(entra);
        troca.setSaiFolgaAntes(sai.getFolga());
        troca.setSaiDataUltimoServicoAntes(sai.getDataUltimoServico());
        troca.setSaiPagouServicoAntes(sai.isPagouServico());
        troca.setEntraFolgaAntes(entra.getFolga());
        troca.setEntraDataUltimoServicoAntes(entra.getDataUltimoServico());
        troca.setEntraServicoPagoAntes(entra.isServicoPagoTemporario());
        troca.setEntraPagouServicoAntes(entra.isPagouServico());
        troca.setAutor(autor);
        troca.setCriadoEm(LocalDateTime.now());

        switch (tipo) {
            case MISSAO:
                if (servicoDeHoje) {
                    // Quem entra tira o serviço hoje (zera à meia-noite). Quem sai não muda nada.
                    entra.setDataUltimoServico(hoje);
                } else {
                    // Só na fila: quem entra cumpre pelo outro e zera na virada
                    entra.setPagouServico(true);
                }
                break;

            case PAGO:
                if (servicoDeHoje) {
                    // Quem entra tira hoje, mas o escudo impede a folga dele de zerar à meia-noite
                    entra.setDataUltimoServico(hoje);
                    entra.setServicoPagoTemporario(true);
                }
                // Quem sai (paga) zera à meia-noite como se tivesse tirado
                sai.setPagouServico(true);
                break;

            case PERMUTA:
                // Trocam de lugar na fila: quem sai herda a folga e a data de quem entrou...
                sai.setFolga(troca.getEntraFolgaAntes());
                sai.setDataUltimoServico(troca.getEntraDataUltimoServicoAntes());
                // ...e quem entra assume a posição de quem saiu
                entra.setFolga(troca.getSaiFolgaAntes());
                entra.setDataUltimoServico(servicoDeHoje ? hoje : troca.getSaiDataUltimoServicoAntes());
                break;
        }

        militarRepository.save(sai);
        militarRepository.save(entra);
        trocaRepository.save(troca);
        log.info("Troca {} em {} ({}): sai {} {}, entra {} {} (por {})", tipo, hoje,
                servicoDeHoje ? "serviço de hoje" : "só na fila",
                sai.getGraduacao(), sai.getNomeGuerra(), entra.getGraduacao(), entra.getNomeGuerra(), autor);
        return troca;
    }

    /** Desfaz uma troca de hoje: os dois militares voltam exatamente como estavam. */
    @Transactional
    public void desfazerTroca(Long idTroca) {
        Troca troca = trocaRepository.findById(idTroca)
                .orElseThrow(() -> new RegraNegocioException("Troca não encontrada."));
        if (!troca.isPodeDesfazer()) {
            throw new RegraNegocioException("Só dá para desfazer trocas de hoje que ainda estão valendo.");
        }
        // Se houver troca mais recente hoje, desfaz ela antes (ordem inversa)
        for (Troca t : getTrocasDeHoje()) {
            restaurar(t);
            if (t.getId().equals(troca.getId())) break;
        }
    }

    private void restaurar(Troca troca) {
        Militar sai = findMilitarById(troca.getSai().getId());
        Militar entra = findMilitarById(troca.getEntra().getId());
        if (sai != null) {
            sai.setFolga(troca.getSaiFolgaAntes());
            sai.setDataUltimoServico(troca.getSaiDataUltimoServicoAntes());
            sai.setPagouServico(troca.isSaiPagouServicoAntes());
            militarRepository.save(sai);
        }
        if (entra != null) {
            entra.setFolga(troca.getEntraFolgaAntes());
            entra.setDataUltimoServico(troca.getEntraDataUltimoServicoAntes());
            entra.setServicoPagoTemporario(troca.isEntraServicoPagoAntes());
            entra.setPagouServico(troca.isEntraPagouServicoAntes());
            militarRepository.save(entra);
        }
        troca.setDesfeita(true);
        trocaRepository.save(troca);
    }
    
    public List<Feriado> getTodosFeriados() { return feriadoRepository.findAll(); }

    public List<Usuario> getTodosUsuarios() { return usuarioRepository.findAll(); }

    @Transactional
    public void salvarNovoUsuario(Usuario usuario) {
        usuario.setLogin(Textos.obrigatorio(usuario.getLogin(), "o login", 50));
        usuario.setNomeCompleto(Textos.obrigatorio(usuario.getNomeCompleto(), "o nome completo", 120));
        usuario.setEmail(Textos.opcional(usuario.getEmail(), "o e-mail", 120)); // vazio vira null (campo único no banco)
        if (usuario.getPerfil() == null) throw new RegraNegocioException("Escolha o perfil de acesso.");
        validarSenha(usuario.getSenha());
        if (usuarioRepository.findByLogin(usuario.getLogin()).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com o login \"" + usuario.getLogin() + "\".");
        }
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void deletarUsuario(Long id, String loginDeQuemExclui) {
        Usuario usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) return;
        if ("root".equals(usuario.getLogin())) throw new RegraNegocioException("A conta principal (root) não pode ser excluída.");
        if (usuario.getLogin().equals(loginDeQuemExclui)) throw new RegraNegocioException("Você não pode excluir a sua própria conta.");
        long totalAdm = usuarioRepository.findAll().stream().filter(u -> u.getPerfil() == Perfil.ADM).count();
        if (usuario.getPerfil() == Perfil.ADM && totalAdm <= 1) throw new RegraNegocioException("O sistema precisa ter pelo menos um administrador.");
        usuarioRepository.deleteById(id);
    }

    /** O próprio usuário troca a senha, confirmando a atual. */
    @Transactional
    public void trocarSenha(String login, String senhaAtual, String novaSenha, String confirmacao) {
        Usuario usuario = usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado."));
        if (senhaAtual == null || !passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
            throw new RegraNegocioException("A senha atual não confere.");
        }
        validarSenha(novaSenha);
        if (!novaSenha.equals(confirmacao)) throw new RegraNegocioException("A confirmação não é igual à nova senha.");
        if (passwordEncoder.matches(novaSenha, usuario.getSenha())) {
            throw new RegraNegocioException("A nova senha precisa ser diferente da atual.");
        }
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        log.info("Usuário {} trocou a própria senha", login);
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < 8) throw new RegraNegocioException("A senha precisa ter pelo menos 8 caracteres.");
        if (senha.length() > 72) throw new RegraNegocioException("A senha aceita no máximo 72 caracteres.");
    }

    private void validarNomeEGraduacao(Militar militar) {
        militar.setGraduacao(Textos.obrigatorio(militar.getGraduacao(), "a graduação", 30));
        militar.setNomeGuerra(Textos.obrigatorio(militar.getNomeGuerra(), "o nome de guerra", 60));
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

        // Posições fixadas pelo ADM: "data|PAPEL" -> militar
        Map<String, Militar> fixados = new HashMap<>();
        for (EscalaFixada f : escalaFixadaRepository.findByDataBetween(inicioBusca, domingo)) {
            fixados.put(f.getData() + "|" + f.getPapel(), f.getMilitar());
        }

        Map<LocalDate, Militar[]> escalaProjetada = simularEscala(todos, hoje, domingo, missoesPorDia, fixados);

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

            DiaEscala diaEscala = new DiaEscala(dia, expediente, feriados.get(dia), titular, reserva,
                    missoesPorDia.getOrDefault(dia, List.of()), afastados, externos);
            Militar titularFixado = fixados.get(dia + "|" + PapelEscala.TITULAR);
            Militar reservaFixado = fixados.get(dia + "|" + PapelEscala.RESERVA);
            diaEscala.setTitularFixado(titular != null && titularFixado != null && titularFixado.getId().equals(titular.getId()));
            diaEscala.setReservaFixado(reserva != null && reservaFixado != null && reservaFixado.getId().equals(reserva.getId()));
            semana.add(diaEscala);
        }
        return semana;
    }

    /** Simula de hoje até {@code ate}. Retorna, por dia útil, [titular, reserva]. */
    private Map<LocalDate, Militar[]> simularEscala(List<Militar> todos, LocalDate hoje, LocalDate ate,
                                                    Map<LocalDate, List<Missao>> missoesPorDia,
                                                    Map<String, Militar> fixados) {
        Map<LocalDate, Militar[]> resultado = new HashMap<>();
        if (ate.isBefore(hoje)) return resultado;

        // Estado simulado (não mexe no banco)
        Map<Long, Integer> folga = new HashMap<>();
        Map<Long, LocalDate> ultimoServico = new HashMap<>();
        Map<Long, Boolean> servicoPago = new HashMap<>();
        Map<Long, Boolean> pagouServico = new HashMap<>();
        for (Militar m : todos) {
            folga.put(m.getId(), m.getFolga());
            ultimoServico.put(m.getId(), m.getDataUltimoServico());
            servicoPago.put(m.getId(), m.isServicoPagoTemporario());
            pagouServico.put(m.getId(), m.isPagouServico());
        }

        for (LocalDate d = hoje; !d.isAfter(ate); d = d.plusDays(1)) {
            final LocalDate dia = d;
            final LocalDate ontem = d.minusDays(1);

            if (dia.isAfter(hoje)) {
                // Mesma regra do avancarDiaDaEscala()
                boolean ontemFoiUtil = isDiaUtil(ontem);
                for (Militar m : todos) {
                    Long id = m.getId();
                    if (ontemFoiUtil && pagouServico.get(id)) {
                        folga.put(id, 0);
                        ultimoServico.put(id, ontem);
                        pagouServico.put(id, false);
                    } else if (ontemFoiUtil && ontem.equals(ultimoServico.get(id))) {
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
                // O que o ADM fixou no mapa vale primeiro (se o militar estiver disponível no dia)
                Militar titularFixado = fixados.get(dia + "|" + PapelEscala.TITULAR);
                if (titularFixado != null && estaAptoNaData(titularFixado, dia)) {
                    titular = titularFixado;
                } else {
                    titular = candidatos.isEmpty() ? null : candidatos.get(0);
                }
                final Militar titularDoDia = titular;
                Militar reservaFixado = fixados.get(dia + "|" + PapelEscala.RESERVA);
                if (reservaFixado != null && estaAptoNaData(reservaFixado, dia)
                        && (titularDoDia == null || !reservaFixado.getId().equals(titularDoDia.getId()))) {
                    reserva = reservaFixado;
                } else {
                    reserva = candidatos.stream()
                            .filter(m -> titularDoDia == null || !m.getId().equals(titularDoDia.getId()))
                            .findFirst().orElse(null);
                }
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

    /**
     * Grava as mudanças que o ADM fez arrastando militares no mapa.
     * Cada ajuste vem como "aaaa-mm-dd|TITULAR|idMilitar" (ou sem id para voltar ao automático).
     * Tudo ou nada: se um ajuste for inválido, nenhum é gravado.
     */
    @Transactional
    public int salvarAjustesDoMapa(List<String> ajustes, String autor) {
        if (ajustes == null || ajustes.isEmpty()) return 0;
        LocalDate hoje = LocalDate.now();
        java.time.format.DateTimeFormatter br = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        Map<LocalDate, Map<PapelEscala, Long>> resultadoPorDia = new HashMap<>();

        for (String ajuste : ajustes) {
            String[] partes = ajuste.split("\\|", -1);
            if (partes.length != 3) throw new RegraNegocioException("Ajuste inválido enviado pelo mapa.");
            LocalDate data;
            PapelEscala papel;
            try {
                data = LocalDate.parse(partes[0]);
                papel = PapelEscala.valueOf(partes[1]);
            } catch (Exception e) {
                throw new RegraNegocioException("Ajuste inválido enviado pelo mapa.");
            }
            if (!data.isAfter(hoje)) {
                throw new RegraNegocioException("O mapa só altera de amanhã em diante. Para hoje, use a tela de Trocas.");
            }
            if (!isDiaUtil(data)) {
                throw new RegraNegocioException("Não há permanência em " + data.format(br) + " (fim de semana ou feriado).");
            }

            EscalaFixada existente = escalaFixadaRepository.findByDataAndPapel(data, papel).orElse(null);
            if (partes[2].isBlank()) {
                // Voltar ao automático
                if (existente != null) escalaFixadaRepository.delete(existente);
                resultadoPorDia.computeIfAbsent(data, d -> new HashMap<>()).put(papel, null);
                continue;
            }

            Militar militar = findMilitarById(Long.valueOf(partes[2]));
            if (militar == null) throw new RegraNegocioException("Militar não encontrado.");
            if (!estaAptoNaData(militar, data)) {
                throw new RegraNegocioException(militar.getGraduacao() + " " + militar.getNomeGuerra()
                        + " não está disponível em " + data.format(br) + " (afastado ou em serviço externo).");
            }

            EscalaFixada fixada = existente != null ? existente : new EscalaFixada();
            fixada.setData(data);
            fixada.setPapel(papel);
            fixada.setMilitar(militar);
            fixada.setAutor(autor);
            fixada.setCriadoEm(LocalDateTime.now());
            escalaFixadaRepository.save(fixada);
            resultadoPorDia.computeIfAbsent(data, d -> new HashMap<>()).put(papel, militar.getId());
        }

        // O mesmo militar não pode ser titular e reserva no mesmo dia
        for (Map.Entry<LocalDate, Map<PapelEscala, Long>> dia : resultadoPorDia.entrySet()) {
            Long titular = idFixadoFinal(dia.getKey(), PapelEscala.TITULAR, dia.getValue());
            Long reserva = idFixadoFinal(dia.getKey(), PapelEscala.RESERVA, dia.getValue());
            if (titular != null && titular.equals(reserva)) {
                throw new RegraNegocioException("Em " + dia.getKey().format(br)
                        + " o mesmo militar ficou como titular e reserva. Ajuste e salve de novo.");
            }
        }

        log.info("{} ajuste(s) da escala salvos pelo mapa (por {})", ajustes.size(), autor);
        return ajustes.size();
    }

    // Militar que vai ficar fixado depois de salvar (o que veio agora ou o que já estava no banco)
    private Long idFixadoFinal(LocalDate data, PapelEscala papel, Map<PapelEscala, Long> alteradosAgora) {
        if (alteradosAgora.containsKey(papel)) return alteradosAgora.get(papel);
        return escalaFixadaRepository.findByDataAndPapel(data, papel).map(f -> f.getMilitar().getId()).orElse(null);
    }
}
