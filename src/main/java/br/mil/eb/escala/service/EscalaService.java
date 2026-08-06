@Transactional
    public void processarTroca(Long idSai, Long idEntra, String tipoTroca) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);
        
        if (sai == null || entra == null) return;
        
        LocalDate hoje = LocalDate.now();

        if (tipoTroca == null) {
            tipoTroca = "MISSAO";
        }

        switch (tipoTroca) {
            case "PAGO":
                // Serviço Pago: Titular zera folga, substituto mantém a folga.
                sai.setFolga(0);
                sai.setDataUltimoServico(hoje);
                break;
                
            case "MISSAO":
                // Missão: Titular preserva folga, substituto zera folga.
                entra.setFolga(0);
                entra.setDataUltimoServico(hoje);
                break;
                
            case "PERMUTA":
                // Permuta Casada: Inverte as folgas e datas entre os dois.
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
