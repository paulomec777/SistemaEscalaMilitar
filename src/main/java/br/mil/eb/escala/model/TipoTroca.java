package br.mil.eb.escala.model;

/**
 * Os 3 tipos de troca da permanência do dia (feitas só pelo ADM).
 *
 * MISSAO   - ordem do escalante: quem entra tira o serviço e zera a folga;
 *            quem sai mantém a folga e continua primeiro da fila.
 * PAGO     - acordo particular, quem sai "paga": quem entra tira o serviço SEM zerar a folga;
 *            quem sai zera a folga como se tivesse tirado.
 * PERMUTA  - troca de datas: quem entra tira hoje e zera; quem sai assume o lugar
 *            de quem entrou na fila e fica devendo o próximo serviço dele.
 */
public enum TipoTroca {
    MISSAO("Necessidade do serviço / Missão",
            "Ordem do escalante. Quem entra tira o serviço e zera a folga. Quem sai mantém a folga e continua primeiro da fila."),
    PAGO("Serviço pago",
            "Acordo particular: quem sai paga o serviço. Quem entra tira o serviço sem zerar a folga. Quem sai zera a folga como se tivesse tirado."),
    PERMUTA("Permuta",
            "Troca de datas. Quem entra tira hoje e zera a folga. Quem sai assume o lugar dele na fila e fica devendo o próximo serviço de quem entrou.");

    private final String rotulo;
    private final String explicacao;

    TipoTroca(String rotulo, String explicacao) {
        this.rotulo = rotulo;
        this.explicacao = explicacao;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getExplicacao() {
        return explicacao;
    }
}
