# Sistema de Escala Militar

Sistema web que substitui as planilhas manuais da escala de serviço de uma
organização militar. Ele calcula automaticamente quem é o próximo a tirar
serviço e o substituto, considerando folgas, afastamentos, feriados e trocas.

Antes, a escala ficava em planilhas espalhadas, difíceis de consultar e
sujeitas a erro humano. Agora a informação fica em um só lugar: qualquer
pessoa consulta o painel, e só o administrador altera a escala.

**Veja funcionando:** https://escala-militar-demo.onrender.com (usuário `demo`, senha `demo`).
A demonstração usa dados fictícios e fica em hospedagem gratuita, então a
primeira visita pode levar cerca de um minuto para abrir.

## Funcionalidades

- **Painel público** com o militar de permanência do dia, o substituto e a
  situação de cada um (apto, afastado ou em serviço externo).
- **Cálculo automático da escala:** o próximo é quem tem mais dias de folga;
  no empate, quem tirou serviço há mais tempo. Quem saiu de serviço ontem
  não entra hoje.
- **Virada do dia à meia-noite** (tarefa agendada): em dia útil, quem tirou
  serviço zera a folga e os demais ganham +1; em fim de semana e feriado,
  todos ganham +1. Afastamentos vencidos são reativados sozinhos.
- **Afastamentos** (férias, dispensa médica etc.) com data de início e fim,
  e impedimento manual com justificativa.
- **Serviço externo** com período agendado e a regra das 48 horas: o militar
  fica fora da escala durante o serviço e no dia seguinte.
- **Trocas de serviço** de três tipos:
  - *Pago:* quem entra assume o dia sem perder a folga acumulada.
  - *Missão:* o substituto assume o dia normalmente.
  - *Permuta:* os dois militares trocam folga e data do último serviço.
- **Cadastro de feriados**, que o cálculo usa para saber o que é dia útil.
- **Login e perfis de acesso** (`ADM` e `MILITAR`) com Spring Security e
  senhas criptografadas com BCrypt. A área `/admin` é restrita ao ADM.

## Tecnologias

- Java 21
- Spring Boot 3.1.5 (Web, Data JPA, Security, Validation)
- Thymeleaf + Thymeleaf Extras Spring Security
- PostgreSQL
- Lombok
- Maven

## Estrutura

```
src/main/java/br/mil/eb/escala
├── config/        # Spring Security (rotas públicas e restritas, login, BCrypt)
├── controller/    # Páginas do painel, área administrativa e feriados
├── model/         # Militar, Usuario, Feriado, Perfil
├── repository/    # Interfaces Spring Data JPA
├── security/      # Carrega o usuário para autenticação
└── service/       # EscalaService: regras da escala, trocas e virada do dia
```

As regras de negócio ficam concentradas em `EscalaService` e no método
`Militar.estaAptoParaServico`, que decide se um militar pode tirar serviço
em uma data.

## Como rodar

Pré-requisitos: Java 21 e um banco PostgreSQL.

1. Clone o repositório:

   ```bash
   git clone https://github.com/paulomec777/SistemaEscalaMilitar.git
   cd SistemaEscalaMilitar
   ```

2. Defina as variáveis de ambiente. Nenhuma senha fica no código.

   | Variável         | Exemplo                                        |
   | ---------------- | ---------------------------------------------- |
   | `DB_URL`         | `jdbc:postgresql://localhost:5432/escala`      |
   | `DB_USERNAME`    | `postgres`                                     |
   | `DB_PASSWORD`    | a senha do seu banco                           |
   | `ADMIN_PASSWORD` | senha inicial do usuário administrador `root`  |

   No Linux ou macOS:

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/escala
   export DB_USERNAME=postgres
   export DB_PASSWORD=sua_senha
   export ADMIN_PASSWORD=senha_do_admin
   ```

   No Windows (PowerShell), use `$env:DB_URL="..."` e assim por diante.

3. Suba a aplicação:

   ```bash
   ./mvnw spring-boot:run
   ```

   No Windows: `mvnw.cmd spring-boot:run`.

4. Acesse `http://localhost:8080`. O painel abre sem login; para a área
   administrativa, entre com o usuário `root` e a senha de `ADMIN_PASSWORD`.

As tabelas são criadas automaticamente pelo Hibernate na primeira execução.
O usuário `root` só é criado se `ADMIN_PASSWORD` estiver definida.

## Modo de demonstração

Para mostrar o sistema sem dados reais, ative o perfil `demo`:

```bash
export SPRING_PROFILES_ACTIVE=demo
./mvnw spring-boot:run
```

Nesse modo o sistema:

- cria 11 militares fictícios (um de férias e um em serviço externo) e os
  feriados nacionais do ano atual e do próximo;
- cria o usuário de visitante `demo` / `demo`, com acesso à área
  administrativa, e mostra esse login na própria tela de entrada;
- apaga e recria esses dados a cada hora, para que nada do que um visitante
  alterar fique permanente.

O modo de demonstração usa um banco H2 em memória, então não precisa de
PostgreSQL nem das variáveis `DB_*`, e nunca toca no banco real.

## Vídeo de fundo

As telas têm um vídeo de fundo atrás de um véu verde-oliva (pensado para vídeos
gerados no [Higgsfield](https://higgsfield.ai)). Para usar o seu:

- coloque o MP4 em `src/main/resources/static/videos/fundo.mp4` e defina
  `APP_VIDEO_FUNDO=/videos/fundo.mp4`, **ou**
- defina `APP_VIDEO_FUNDO` com o endereço público do vídeo.

Sem vídeo, o sistema mostra só o gradiente. Quem prefere menos movimento
(configuração do sistema operacional) também vê só o gradiente.

## Configurações de produção

| Variável | Para que serve | Padrão |
| --- | --- | --- |
| `APP_PAINEL_PUBLICO` | `false` exige login também no painel e no mapa (use se o sistema ficar acessível pela internet) | `true` |
| `COOKIE_SEGURO` | `true` faz o cookie de sessão trafegar só por HTTPS (use no Railway) | `false` |
| `APP_VIDEO_FUNDO` | Endereço do vídeo de fundo | vazio |

Segurança já embutida: proteção CSRF, bloqueio de 15 minutos após 5 senhas erradas,
senha mínima de 8 caracteres, tela "Minha senha", sessão que cai após 30 minutos parada
e trava que impede o perfil `demo` de rodar fora do banco H2 em memória.

## Deploy

O repositório tem um `Dockerfile`, então qualquer serviço que rode contêineres
(Railway, Render, Fly.io) consegue publicar o projeto direto do GitHub:

- **Demonstração:** crie um serviço a partir deste repositório e defina
  `SPRING_PROFILES_ACTIVE=demo`. Não precisa de banco.
- **Uso real:** crie também um banco PostgreSQL e defina `DB_URL`,
  `DB_USERNAME`, `DB_PASSWORD` e `ADMIN_PASSWORD`.

A porta é lida da variável `PORT`, que esses serviços já definem.

Cada envio para o GitHub é compilado automaticamente pelo GitHub Actions
(`.github/workflows/build.yml`).

## Autor

Desenvolvido por **Paulo Henrique Tomaz Martins**.

- [LinkedIn](https://www.linkedin.com/in/paulo-henrique-517268269)
- Contato: paulo92176914@gmail.com
