package br.mil.eb.escala.service;

import br.mil.eb.escala.dto.DiaEscala;
import br.mil.eb.escala.exception.RegraNegocioException;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.TipoTroca;
import br.mil.eb.escala.model.Troca;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.TrocaRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regras da escala testadas sem banco: os repositórios são simulados (Mockito). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscalaServiceTest {

    @Mock private MilitarRepository militarRepository;
    @Mock private FeriadoRepository feriadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MissaoService missaoService;
    @Mock private TrocaRepository trocaRepository;

    @InjectMocks private EscalaService escalaService;

    private final LocalDate hoje = LocalDate.now();
    private final List<Militar> militares = new ArrayList<>();
    private final List<Troca> trocas = new ArrayList<>(); // "banco" das trocas

    @BeforeEach
    void preparar() {
        when(militarRepository.findAll()).thenReturn(militares);
        when(missaoService.idsAfastadosPorMissao(any())).thenReturn(Set.of());
        when(missaoService.missoesDoPeriodo(any(), any())).thenReturn(List.of());
        when(feriadoRepository.existsByData(any())).thenReturn(false);
        when(feriadoRepository.findAll()).thenReturn(List.of());

        // Repositório de trocas simulado em memória
        when(trocaRepository.save(any())).thenAnswer(inv -> {
            Troca t = inv.getArgument(0);
            if (t.getId() == null) t.setId((long) trocas.size() + 1);
            if (!trocas.contains(t)) trocas.add(t);
            return t;
        });
        when(trocaRepository.findByDataAndDesfeitaFalseOrderByCriadoEmDesc(any())).thenAnswer(inv -> {
            List<Troca> lista = new ArrayList<>(trocas.stream()
                    .filter(t -> !t.isDesfeita() && t.getData().equals(inv.getArgument(0))).toList());
            java.util.Collections.reverse(lista); // mais recente primeiro
            return lista;
        });
        when(trocaRepository.findById(any())).thenAnswer(inv ->
                trocas.stream().filter(t -> t.getId().equals(inv.getArgument(0))).findFirst());
    }

    private Militar militar(long id, String nome, int folga, LocalDate ultimoServico) {
        Militar m = new Militar();
        m.setId(id);
        m.setGraduacao("Cb");
        m.setNomeGuerra(nome);
        m.setFolga(folga);
        m.setDataUltimoServico(ultimoServico);
        militares.add(m);
        return m;
    }

    @Test
    void titularEhOMaisFolgadoEReservaOSegundo() {
        militar(1, "Alves", 5, hoje.minusDays(5));
        militar(2, "Barros", 9, hoje.minusDays(9));
        militar(3, "Cardoso", 7, hoje.minusDays(7));

        assertEquals("Barros", escalaService.getProximoPermanencia().getNomeGuerra());
        assertEquals("Cardoso", escalaService.getProximoSubstituto().getNomeGuerra());
    }

    @Test
    void quemTirouServicoOntemNaoEntraHoje() {
        militar(1, "Alves", 20, hoje.minusDays(1));
        militar(2, "Barros", 3, hoje.minusDays(3));

        assertEquals("Barros", escalaService.getProximoPermanencia().getNomeGuerra());
    }

    @Test
    void reservaNuncaEhIgualAoTitularDepoisDeTroca() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        Militar trocado = militar(2, "Barros", 8, hoje); // escalado manualmente para hoje
        militar(3, "Cardoso", 1, hoje.minusDays(1));

        assertEquals(trocado, escalaService.getProximoPermanencia());
        assertNotEquals(trocado, escalaService.getProximoSubstituto());
    }

    @Test
    void missaoMantemOMaisFolgadoNaEscalaPorPadrao() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        militar(2, "Barros", 5, hoje.minusDays(5));
        // Alves está numa missão hoje, mas o ADM NÃO marcou para afastar da permanência
        when(missaoService.idsAfastadosPorMissao(hoje)).thenReturn(Set.of());

        assertEquals("Alves", escalaService.getProximoPermanencia().getNomeGuerra());
    }

    @Test
    void admPodeAfastarDaPermanenciaPorMissao() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        militar(2, "Barros", 5, hoje.minusDays(5));
        when(missaoService.idsAfastadosPorMissao(hoje)).thenReturn(Set.of(1L));

        assertEquals("Barros", escalaService.getProximoPermanencia().getNomeGuerra());
    }

    @Test
    void tabelaDoPainelMostraAfastadosPorUltimo() {
        Militar afastado = militar(1, "Jardim", 15, hoje.minusDays(15));
        afastado.setAtivoNaEscala(false);
        militar(2, "Alves", 9, hoje.minusDays(9));
        militar(3, "Barros", 3, hoje.minusDays(3));

        List<Militar> tabela = escalaService.getTodosMilitaresParaDashboard();
        assertEquals("Alves", tabela.get(0).getNomeGuerra());
        assertEquals("Barros", tabela.get(1).getNomeGuerra());
        assertEquals("Jardim", tabela.get(2).getNomeGuerra());
    }

    @Test
    void afastamentoFuturoNaoTiraDaEscalaHoje() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        when(militarRepository.findById(1L)).thenReturn(Optional.of(militares.get(0)));

        escalaService.afastarMilitar(1L, "Férias", hoje.plusDays(10), hoje.plusDays(40));

        assertTrue(militares.get(0).isAtivoNaEscala());
        assertEquals("Alves", escalaService.getProximoPermanencia().getNomeGuerra());
    }

    @Test
    void afastamentoComDataFinalAntesDaInicialEhRecusado() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        when(militarRepository.findById(1L)).thenReturn(Optional.of(militares.get(0)));

        assertThrows(RegraNegocioException.class,
                () -> escalaService.afastarMilitar(1L, "Férias", hoje.plusDays(5), hoje));
    }

    @Test
    void meiaNoiteReativaSoNoDiaSeguinteAoFimDoAfastamento() {
        Militar acabouOntem = militar(1, "Alves", 9, hoje.minusDays(30));
        acabouOntem.setAtivoNaEscala(false);
        acabouOntem.setDataInicioAfastamento(hoje.minusDays(20));
        acabouOntem.setDataFimAfastamento(hoje.minusDays(1));

        Militar acabaHoje = militar(2, "Barros", 9, hoje.minusDays(30));
        acabaHoje.setAtivoNaEscala(false);
        acabaHoje.setDataInicioAfastamento(hoje.minusDays(20));
        acabaHoje.setDataFimAfastamento(hoje);

        escalaService.avancarDiaDaEscala();

        assertTrue(acabouOntem.isAtivoNaEscala());
        assertNull(acabouOntem.getDataFimAfastamento());
        assertFalse(acabaHoje.isAtivoNaEscala());
    }

    @Test
    void meiaNoiteLimpaServicoExternoEncerrado() {
        Militar m = militar(1, "Leite", 4, hoje.minusDays(10));
        m.setDataInicioServicoExterno(hoje.minusDays(6));
        m.setDataFimServicoExterno(hoje.minusDays(3));

        escalaService.avancarDiaDaEscala();

        assertNull(m.getDataInicioServicoExterno());
        assertEquals("APTO", m.getStatus());
    }

    @Test
    void fechamentoDoDiaRegistraOTitularQuandoNinguemFoiRegistrado() {
        DayOfWeek dia = hoje.getDayOfWeek();
        assumeTrue(dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY, "só faz sentido em dia útil");

        Militar alves = militar(1, "Alves", 9, hoje.minusDays(9));
        militar(2, "Barros", 5, hoje.minusDays(5));

        escalaService.registrarServicoDoDia();

        assertEquals(hoje, alves.getDataUltimoServico());
        verify(militarRepository).save(alves);
    }

    @Test
    void fechamentoDoDiaRespeitaTrocaJaRegistrada() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        militar(2, "Barros", 5, hoje); // ADM já colocou o Barros hoje

        escalaService.registrarServicoDoDia();

        verify(militarRepository, never()).save(any());
    }

    @Test
    void mapaTemSeteDiasESemPermanenciaNoFimDeSemana() {
        militar(1, "Alves", 9, hoje.minusDays(9));
        militar(2, "Barros", 5, hoje.minusDays(5));
        militar(3, "Cardoso", 2, hoje.minusDays(2));

        LocalDate segunda = hoje.with(DayOfWeek.MONDAY).plusWeeks(1);
        List<DiaEscala> semana = escalaService.projetarSemana(segunda);

        assertEquals(7, semana.size());
        assertNull(semana.get(5).getTitular()); // sábado
        assertNull(semana.get(6).getTitular()); // domingo
        for (int i = 0; i < 5; i++) {
            assertNotNull(semana.get(i).getTitular(), "dia útil sem titular: " + semana.get(i).getData());
            assertNotEquals(semana.get(i).getTitular(), semana.get(i).getReserva());
        }
        // ninguém tira dois dias úteis seguidos
        for (int i = 1; i < 5; i++) {
            assertNotEquals(semana.get(i - 1).getTitular(), semana.get(i).getTitular());
        }
    }

    @Test
    void naoExcluiOProprioUsuarioNemORoot() {
        Usuario root = usuario(1L, "root", Perfil.ADM);
        Usuario sgt = usuario(2L, "sgt.silva", Perfil.ADM);
        when(usuarioRepository.findAll()).thenReturn(List.of(root, sgt));

        assertThrows(RegraNegocioException.class, () -> escalaService.deletarUsuario(1L, "sgt.silva"));
        assertThrows(RegraNegocioException.class, () -> escalaService.deletarUsuario(2L, "sgt.silva"));
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    void senhaCurtaEhRecusada() {
        Usuario novo = new Usuario();
        novo.setLogin("cb.souza");
        novo.setNomeCompleto("Cb Souza");
        novo.setPerfil(Perfil.ADM);
        novo.setSenha("123");

        assertThrows(RegraNegocioException.class, () -> escalaService.salvarNovoUsuario(novo));
        verify(usuarioRepository, never()).save(any());
    }

    // ======================= TROCAS =======================

    private boolean hojeEhDiaUtil() {
        DayOfWeek d = hoje.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    private Militar comBusca(Militar m) {
        when(militarRepository.findById(m.getId())).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void trocaPorMissaoQuemEntraTiraEQuemSaiMantemAFolga() {
        assumeTrue(hojeEhDiaUtil());
        Militar alves = comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        Militar barros = comBusca(militar(2, "Barros", 5, hoje.minusDays(5)));

        escalaService.processarTroca(1L, 2L, TipoTroca.MISSAO, "adm");

        assertEquals(barros, escalaService.getProximoPermanencia());
        assertEquals(hoje, barros.getDataUltimoServico());
        assertFalse(barros.isServicoPagoTemporario());   // zera normalmente à meia-noite
        assertEquals(9, alves.getFolga());                // quem saiu mantém a folga
        assertFalse(alves.isPagouServico());
    }

    @Test
    void servicoPagoQuemEntraNaoZeraEQuemSaiZeraNaVirada() {
        assumeTrue(hojeEhDiaUtil());
        Militar alves = comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        Militar barros = comBusca(militar(2, "Barros", 5, hoje.minusDays(5)));

        escalaService.processarTroca(1L, 2L, TipoTroca.PAGO, "adm");

        assertEquals(barros, escalaService.getProximoPermanencia());
        assertTrue(barros.isServicoPagoTemporario());     // escudo: não zera à meia-noite
        assertTrue(alves.isPagouServico());               // zera à meia-noite
        assertEquals(9, alves.getFolga());                // ...mas não antes disso
    }

    @Test
    void permutaTrocaOsLugaresNaFila() {
        assumeTrue(hojeEhDiaUtil());
        Militar alves = comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        Militar barros = comBusca(militar(2, "Barros", 5, hoje.minusDays(5)));

        escalaService.processarTroca(1L, 2L, TipoTroca.PERMUTA, "adm");

        assertEquals(barros, escalaService.getProximoPermanencia());
        assertEquals(hoje, barros.getDataUltimoServico());
        assertEquals(9, barros.getFolga());                       // assumiu o lugar do Alves
        assertEquals(5, alves.getFolga());                        // Alves ficou no lugar do Barros
        assertEquals(hoje.minusDays(5), alves.getDataUltimoServico());
    }

    @Test
    void novaTrocaNoMesmoDiaDesfazAAnteriorEPartedoTitularOriginal() {
        assumeTrue(hojeEhDiaUtil());
        Militar alves = comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        Militar barros = comBusca(militar(2, "Barros", 5, hoje.minusDays(5)));
        Militar cardoso = comBusca(militar(3, "Cardoso", 3, hoje.minusDays(3)));

        escalaService.processarTroca(1L, 2L, TipoTroca.PERMUTA, "adm");
        // Barros teve um imprevisto: nova troca, agora o Cardoso entra (sai continua sendo o Alves)
        escalaService.processarTroca(1L, 3L, TipoTroca.PAGO, "adm");

        assertEquals(cardoso, escalaService.getProximoPermanencia());
        assertEquals(5, barros.getFolga());                       // Barros voltou exatamente como estava
        assertEquals(hoje.minusDays(5), barros.getDataUltimoServico());
        assertEquals(9, alves.getFolga());                        // a permuta foi desfeita
        assertTrue(alves.isPagouServico());                       // e vale o serviço pago
        assertEquals(1, escalaService.getTrocasDeHoje().size());
        assertEquals(alves, escalaService.getTitularOriginalDeHoje());
    }

    @Test
    void desfazerTrocaVoltaTodoMundoComoEstava() {
        assumeTrue(hojeEhDiaUtil());
        Militar alves = comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        Militar barros = comBusca(militar(2, "Barros", 5, hoje.minusDays(5)));

        Troca troca = escalaService.processarTroca(1L, 2L, TipoTroca.PERMUTA, "adm");
        escalaService.desfazerTroca(troca.getId());

        assertEquals(9, alves.getFolga());
        assertEquals(hoje.minusDays(9), alves.getDataUltimoServico());
        assertEquals(5, barros.getFolga());
        assertEquals(hoje.minusDays(5), barros.getDataUltimoServico());
        assertEquals(alves, escalaService.getProximoPermanencia());
        assertTrue(troca.isDesfeita());
    }

    @Test
    void quemSaiuDeServicoOntemNaoPodeEntrarNaTroca() {
        assumeTrue(hojeEhDiaUtil());
        comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        comBusca(militar(2, "Barros", 0, hoje.minusDays(1)));

        assertThrows(RegraNegocioException.class, () -> escalaService.processarTroca(1L, 2L, TipoTroca.MISSAO, "adm"));
    }

    @Test
    void trocaRecusadaSeOTitularMudouComATelaAberta() {
        assumeTrue(hojeEhDiaUtil());
        comBusca(militar(1, "Alves", 9, hoje.minusDays(9)));
        comBusca(militar(2, "Barros", 5, hoje.minusDays(5)));
        comBusca(militar(3, "Cardoso", 3, hoje.minusDays(3)));

        // a tela dizia que o titular era o Barros, mas é o Alves
        assertThrows(RegraNegocioException.class, () -> escalaService.processarTroca(2L, 3L, TipoTroca.MISSAO, "adm"));
    }

    @Test
    void naViradaQuemPagouOServicoZeraComoSeTivesseTirado() {
        DayOfWeek ontem = hoje.minusDays(1).getDayOfWeek();
        assumeTrue(ontem != DayOfWeek.SATURDAY && ontem != DayOfWeek.SUNDAY, "ontem precisa ser dia útil");

        Militar alves = militar(1, "Alves", 9, hoje.minusDays(10));
        alves.setPagouServico(true);
        Militar barros = militar(2, "Barros", 5, hoje.minusDays(1)); // quem entrou no serviço pago
        barros.setServicoPagoTemporario(true);

        escalaService.avancarDiaDaEscala();

        assertEquals(0, alves.getFolga());
        assertEquals(hoje.minusDays(1), alves.getDataUltimoServico());
        assertFalse(alves.isPagouServico());
        assertEquals(6, barros.getFolga());               // não zerou: ganhou +1
        assertFalse(barros.isServicoPagoTemporario());
    }

    private Usuario usuario(Long id, String login, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setLogin(login);
        u.setNomeCompleto(login);
        u.setPerfil(perfil);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }
}
