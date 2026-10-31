# ZUNEX

Um launcher Android nativo (Kotlin + Jetpack Compose) que recria a experiência do **Windows Phone 8.1** — Start Screen com blocos dinâmicos, lista de aplicativos, Central de Ações, controle de volume, tela de bloqueio estilo Lumia e a família de aplicativos ZUNEX: Telefone, Pessoas, Mensagens, Música, Fotos, Câmera, Calendário, Notas, Calculadora, Clima, Teclado Word Flow, Configurações e a ZUNEX Store.

**Desenvolvedor:** João Gabriel

> Projeto de fã, sem afiliação com Microsoft ou Nokia. A fonte Segoe WP não pode ser redistribuída; usamos a **Open Sans** (licença SIL OFL — veja `third_party/opensans/OFL.txt`).

## Instalação

Há duas edições:

- **Lite** (`ZUNEX-Lite.apk`, pacote `com.wpreborn.launcher.lite`): sem permissões de SMS, registro de chamadas e chamada direta, o que reduz os bloqueios do Play Protect. Mensagens abre o app de mensagens do Android; o histórico de chamadas fica no app Telefone do Android; os contadores dos blocos Telefone/Mensagens vêm das notificações desses apps.
- **Completa** (`ZUNEX.apk`): Mensagens próprio (pode ser o app de SMS padrão) e histórico de chamadas.

O link direto está em **GitHub → Releases**. Se o Play Protect bloquear: "Mais detalhes" → "Instalar mesmo assim" (ou desative temporariamente a verificação em Play Store › Play Protect › engrenagem). No Android 13+, se uma permissão aparecer como "configuração restrita": Configurações › Apps › ZUNEX › ⋮ › "Permitir configurações restritas".

1. Baixe o APK em **GitHub → Actions → Android build → (último run) → Artifacts → `windows-phone-reborn-apk`**.
   - `app-release.apk` (otimizado com R8) ou `app-debug.apk`.
2. Instale (permita "instalar apps desconhecidos" para o navegador/gerenciador de arquivos).
3. Abra o **ZUNEX**. Na tela de boas-vindas toque em **definir como padrão**.
   - Ou: Configurações do Android › Apps › Apps padrão › **App de tela inicial** › ZUNEX.
4. Recomendado: em *Configurações › notificações+ações*, ative o **acesso às notificações** (Central de Ações, contadores nos blocos, bloco Música com outros players).

Como app de tela inicial padrão, o launcher volta automaticamente após reiniciar o telefone.

### Compilar localmente

Requer JDK 17 e Android SDK (API 35).

```
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Os APKs de CI são assinados com a chave de debug do runner (cada build tem uma chave diferente; para atualizar de um build de CI para outro pode ser preciso desinstalar antes). Para distribuição, configure uma chave própria em `app/build.gradle.kts`.

## Etapa 1 — Pesquisa e decisões de design

Referências estudadas: os guias de UI do Windows Phone 7 (UI Design and Interaction Guide v2.0), o artigo da MSDN Magazine sobre princípios de design do WP, e os projetos `ducbao414/wphone.dev` (simulador web do 8.1), `DymnStudio/DymnMetro` (launcher Compose), `lorddie/MetroLauncherAndroid` (launcher Metro W10M) e `qw75106/square-home` (Flutter). Nenhum código foi copiado desses projetos; aproveitamos ideias de arquitetura (repositório de apps via `LauncherApps`, contadores via `NotificationListenerService`, layout em grade de unidades pequenas).

Valores reproduzidos do sistema original:

| Elemento | Windows Phone 8.1 | Implementação |
|---|---|---|
| Cores de destaque | 20 cores (lime #A4C400 … taupe #87794E) | `core/theme/WpColors.kt`, nomes oficiais em PT-BR |
| Pincéis do tema | PhoneSubtle 60%, PhoneChrome #1F1F1F/#DDDDDD, etc. | `WpPalette.Dark/Light` |
| Tipografia | Segoe WP Light/SemiLight, títulos minúsculos, app title em caixa alta | `WpTypography.kt` (Open Sans) |
| Grade do Start | blocos 1×1, 2×2, 4×2 (+4×4), 4 ou 6 colunas ("mostrar mais blocos") | `data/tiles/Tiles.kt`, empacotamento em blocos 2×2 |
| Turnstile | ForwardIn −80°→0 (350 ms), ForwardOut 0→50° (250 ms), easing exponencial 6 (WP Toolkit) | `core/motion/Turnstile.kt` |
| Feather | blocos giram a partir da borda esquerda da tela, de cima para baixo, o tocado por último | `turnstileItem()` + `StartScreen` |
| Tilt | o elemento pressionado inclina em direção ao dedo | `core/motion/Tilt.kt` |
| Pivot / Panorama | cabeçalhos que rolam com o conteúdo, circulares; título com paralaxe | `core/ui/Pivot.kt`, `Panorama.kt` |
| Barra de aplicativos | botões redondos, "…" expande rótulos e menu | `core/ui/AppBar.kt` |
| Indicador de progresso | cinco pontos que aceleram/desaceleram | `core/motion/LoadingDots.kt` |

## Arquitetura

```
app/src/main/java/com/wpreborn/
├── WpApp.kt                    Application (carrega tema, registra tela de bloqueio)
├── core/
│   ├── theme/                  cores de destaque, paleta, tipografia, WpTheme
│   ├── motion/                 turnstile, tilt, entradas escalonadas, swivel, loading dots, easings
│   ├── icons/                  glifos vetoriais estilo Segoe (sem emojis)
│   └── ui/                     componentes: texto, botões, toggle, slider, textbox, pivot,
│                               panorama, app bar, message box, context menu, jump list,
│                               barra de status, thumbnails
├── navigation/                 pilha de páginas com turnstile (estilo NavigationService)
├── data/
│   ├── prefs/                  DataStore (tema, destaque, opções)
│   ├── tiles/                  modelo, empacotamento e persistência do layout
│   ├── apps/                   apps instalados (LauncherApps) + apps embutidos
│   ├── contacts/ calls/ sms/   provedores Android
│   ├── calendar/ media/        CalendarContract, MediaStore (música e fotos), now playing
│   ├── notifications/          espelho das notificações (Central de Ações / contadores)
│   └── system/                 bateria, rede, sinal, rádios, relógio
├── permissions/                pedidos sob demanda + PermissionGate no estilo WP
├── services/                   NotificationListener, MusicService (Media3), receptores SMS
├── launcher/                   MainActivity (Home), Start, lista de apps, Central de Ações,
│                               volume, tela de bloqueio, boas-vindas
└── apps/                       Phone, Messaging, Music, People, Calendar, Photos, Settings
                                (cada um com sua Activity/tarefa, como apps reais)
```

## Funcionalidades

- **Start**: blocos pequeno/médio/largo (e grande no modo 4 colunas), cor por bloco, rolagem vertical, feather ao abrir apps e ao voltar, pressionar e segurar → modo de edição (outros blocos recuam, botões desafixar/redimensionar/cor), arrastar e soltar com auto‑rolagem, fundo do Início visto através dos blocos (8.1), botão Início leva ao topo.
- **Blocos dinâmicos reais**: Telefone (chamadas perdidas), Mensagens (SMS não lidos), Pessoas (mosaico de fotos de contatos que giram), Fotos (ciclo de fotos da galeria), Música (capa/faixa atual, também de outros players), Calendário (data e próximo compromisso, com flip), apps Android (contador e última notificação no verso).
- **Lista de apps**: ordem alfabética, seção "novos", letras de grupo, jump list, pesquisa, menu contextual (fixar, desinstalar, info), animação de entrada.
- **Central de Ações**: arrastar a barra de status para baixo; data/bateria, 8 ações rápidas, slider de brilho, notificações agrupadas por app (tocar abre, deslizar para a direita descarta, limpar tudo).
- **Volume**: as teclas de volume nas telas do launcher mostram o painel do 8.1 (toque/mídia, vibrar, controles de mídia).
- **Tela de bloqueio**: relógio grande, dia, data, próximo compromisso, status dos apps, papel de parede, entrada animada, toque faz o "quique", deslizar para cima desbloqueia.
- **Telefone**: histórico, discagem rápida (favoritos), teclado com tons DTMF, detalhes da chamada, ligar/enviar SMS.
- **Mensagens**: conversas, balões com "rabicho" no estilo WP, envio/recebimento, notificações, contador, nova mensagem com sugestão de contatos, função de app de SMS padrão.
- **Música**: artistas, álbuns, músicas, listas de reprodução (criar/adicionar/excluir), reprodução em segundo plano (Media3), aleatório, repetir, barra de progresso, mini player, controles na tela de bloqueio e notificação.
- **Pessoas**: todos/favoritos, grupos por letra, jump list, pesquisa, perfil com ligar/SMS/email/mapa, histórico, favoritar, editar.
- **Calendário**: agenda (30 dias), mês com marcação de dias ocupados, detalhes do evento, novo evento/editar (app de calendário do sistema).
- **Fotos**: coleção por mês, álbuns, visualizador com deslize, zoom (pinça/duplo toque), compartilhar, editar, excluir (Android 11+).
- **Configurações**: pivots "sistema" e "aplicativos"; início+tema, cor de destaque, tela de bloqueio, notificações, brilho, armazenamento, sobre, tela inicial padrão — o resto abre a tela nativa via intents oficiais.
- Interface em **português (Brasil)** e inglês.

## Teclado ZUNEX (Word Flow)

Teclado do sistema no estilo do Windows Phone 8.1. Abra o app **Teclado** e siga os dois passos: ativar e escolher.

- **Idiomas e escritas:** mais de 50 idiomas, com layouts próprios para:
  - alfabeto latino (QWERTY, AZERTY, QWERTZ e as letras de cada país);
  - cirílico, grego, árabe, persa, urdu e hebraico;
  - devanágari, bengali, tâmil e tailandês;
  - armênio e georgiano;
  - coreano, com composição Hangul, e japonês, digitado em romaji e convertido para kana.
- **Acentos:** em qualquer idioma de alfabeto latino, segure a tecla para ver os acentos.
- **Sugestões e correção automática:** dicionários de frequência para 20 idiomas, mais as palavras que você digita e a previsão da próxima palavra. Apertar o apagar logo depois de uma correção a desfaz.
- **Word Flow:** deslize sobre as letras para escrever; o rastro aparece na cor de destaque e as alternativas surgem na barra de sugestões.
- **Outros recursos:**
  - símbolos (2 páginas), emoji por categoria e teclado numérico ou de telefone;
  - a tecla Enter muda conforme o campo (ir, pesquisar, enviar);
  - maiúscula automática no início da frase e dois espaços para inserir um ponto;
  - deslizar na barra de espaço move o cursor; deslizar para a esquerda no apagar apaga a palavra;
  - som e vibração opcionais.
- **Limitação:** não há chinês, porque a conversão pinyin para caracteres exige dicionários grandes. Para chinês, use outro teclado; segurar a barra de espaço abre o seletor de teclados.

## Créditos

- Fonte Open Sans: SIL Open Font License (`third_party/opensans/OFL.txt`).
- Listas de palavras: [FrequencyWords](https://github.com/hermitdave/FrequencyWords), de Hermit Dave. O código é MIT (`third_party/frequencywords/LICENSE`) e os dados vêm das legendas do OpenSubtitles (CC-BY-SA 4.0).
- Ícones de clima: [Meteocons](https://github.com/basmilius/weather-icons), de Bas Milius, licença MIT (`third_party/meteocons/LICENSE`).
- Fotos de fundo do Clima: Wikimedia Commons, domínio público / CC0; arquivo, autor e licença de cada uma em `third_party/weather-photos/photos.tsv` (baixadas no build).

## Limitações do Android (e alternativas adotadas)

| Recurso | Limitação | Alternativa |
|---|---|---|
| Tela de bloqueio | Apps não podem substituir o keyguard seguro | Tela WP mostrada **na frente** do keyguard (`showWhenLocked`); deslizar chama `requestDismissKeyguard`, mantendo PIN/biometria. Precisa da permissão "sobrepor a outros apps" para aparecer ao desligar a tela. Nada privado é exibido antes do desbloqueio. |
| Central de Ações | Não é possível substituir a gaveta de notificações do sistema | Central própria dentro do launcher e dos apps WP (gesto na barra de status). Em outros apps vale a gaveta do Android. |
| Wi‑Fi, Bluetooth, modo avião | Desde o Android 10 apps comuns não podem alternar | Abrem os painéis/telas oficiais. Brilho, rotação, lanterna e vibrar funcionam diretamente (brilho/rotação pedem "modificar configurações do sistema"). |
| Volume | Teclas de volume só podem ser interceptadas em nossas próprias telas | Painel WP no launcher e apps WP; nos demais apps, o do Android. |
| Barra de status | A do sistema não pode ser trocada globalmente | Escondida (modo imersivo) nas nossas telas e substituída pela do WP; desativável. |
| SMS | Gravar/receber exige ser o app de SMS padrão | Sem ser padrão: lê e envia. Sendo padrão: recebe, notifica, marca como lido, exclui. **MMS não é suportado** (aviso e notificação). |
| Chamadas | Interface em chamada é do sistema (InCallService exige ser o discador padrão e é crítico) | Discagem por `ACTION_CALL` (ou `ACTION_DIAL` sem permissão) — chamadas de emergência e recursos da operadora preservados. |
| Segoe WP | Fonte proprietária | Selawik (OFL), métrica compatível. |
| Panorama circular | — | O componente `WpPanorama` existe; os hubs usam Pivot, como o 8.1. |
| Turnstile ao abrir apps Android | Animações de janela do sistema não suportam rotação 3D | Feather dos blocos + entrada deslizante/esmaecida do app. |

## Etapa 7 — Testes

A compilação é verificada em CI (GitHub Actions) a cada push. Este ambiente de desenvolvimento não tinha emulador nem dispositivo Android, então **não houve teste em aparelho real**; recomenda-se testar em Android 10, 12, 14/15, telas com e sem recorte, navegação por gestos e por botões. Problemas encontrados podem ser abertos como issues.
