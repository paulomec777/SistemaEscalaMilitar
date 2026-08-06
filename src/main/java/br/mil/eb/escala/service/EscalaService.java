public void salvarNovoMilitar(Militar militar) {
        militar.setAtivoNaEscala(true);
        
        // Mantém a data enviada pelo administrador na tela.
        // Se por algum motivo bizarro ele não enviou (ex: erro no HTML), cai pro fallback (hoje)
        if (militar.getDataUltimoServico() == null) {
            militar.setDataUltimoServico(LocalDate.now());
        }

        // Calcula a folga real com base na data informada pelo ADM
        int folgaCalculada = 0;
        LocalDate hoje = LocalDate.now();
        LocalDate dataUltimoSv = militar.getDataUltimoServico();
        
        // Verifica quantos dias se passaram entre o último serviço informado e o dia de hoje.
        // E tira os finais de semana e feriados do cálculo (opcional, mas mais justo se a escala congela).
        // Se a data for antiga, ele ganha a folga equivalente.
        if (dataUltimoSv.isBefore(hoje)) {
           LocalDate current = dataUltimoSv.plusDays(1);
           while(!current.isAfter(hoje)) {
               // Conta a folga apenas em dias que a escala roda (para não dar folgas gigantescas se a escala trava no FDS)
               // (Opcional: você pode apenas usar um ChronoUnit.DAYS.between para pegar os dias corridos)
                folgaCalculada++;
                current = current.plusDays(1);
           }
        }
        
        militar.setFolga(folgaCalculada);
        
        militarRepository.save(militar);
    }
