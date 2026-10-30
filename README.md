# Assistente 99

App Android que lê, em tempo real, a solicitação de corrida na tela do app de motorista da **99** ou da **Uber** e mostra sobre ela, na hora, o **R$ por km** (e opcionalmente R$/h).

## Como funciona

- Um **Serviço de Acessibilidade** (`RideAccessibilityService`) observa só os pacotes dos apps de motorista (`com.app99.driver` e `com.ubercab.driver`).
- A cada mudança de tela ele coleta os textos visíveis e o `RideParser` extrai preço, km até o passageiro e km da viagem.
- O `OverlayView` desenha um card sobre o app (sem capturar toques, então não atrapalha o botão de aceitar):
  - verde: R$/km acima do valor "bom"
  - amarelo: entre o mínimo e o "bom"
  - vermelho: abaixo do mínimo
- Tudo roda no aparelho. O app não pede internet.

## Como compilar

1. Instale o Android Studio.
2. `File > Open` e escolha esta pasta (`Assistente99`). Ele baixa o Gradle e o SDK sozinho.
3. Conecte o celular com depuração USB e clique em Run, ou use `Build > Build APK(s)`.

## Como usar

1. Abra o Assistente 99 e toque em **Ativar o serviço de acessibilidade**; ligue "Assistente 99".
2. Ajuste as faixas de R$/km e se o km até o passageiro entra na conta. Toque em Salvar.
3. Abra o app da 99 ou da Uber, fique online e aguarde uma solicitação. O mesmo card funciona nos dois.

## Uber: leitura por imagem (OCR)

**Android 11 ou mais novo:** a leitura por imagem usa o próprio serviço de acessibilidade (`takeScreenshot`). Não pede autorização, não mostra o aviso de "gravando a tela" e **não esconde as notificações do celular**. Liga sozinha com o serviço.

**Android 10 ou mais antigo:** usa a captura de tela do sistema, descrita abaixo.

### Captura de tela do sistema (só Android 10 ou menos)

O card de solicitação da Uber não expõe o texto ao serviço de acessibilidade (o modo diagnóstico mostra "tela sem nenhum texto legível"). Para esse caso o app lê a imagem da tela:

1. Na tela principal, toque em **2) Ligar leitura por captura de tela** e aceite o aviso do Android ("Começar a gravar/transmitir").
2. Com **Ligar a captura ao abrir o app** (padrão), o app pede essa autorização sozinho toda vez que você o abre e a captura está desligada. O Android exige a confirmação a cada vez (celular reiniciado ou app fechado), então é só tocar em "Começar agora". Uma notificação "Assistente 99" fica ativa enquanto a captura está ligada.
3. Só quando um app monitorado mostra uma tela sem texto o app tira um print (na memória, nada é salvo nem enviado), lê com OCR no aparelho e mostra o R$/km. Nas outras telas não gasta bateria extra.

O texto lido por OCR aparece em **Ver o que o app leu da tela**, na seção "LEITURA POR OCR".

## Tema claro e escuro

Na tela principal, em Ajustes, **Tema do app**: Automático (segue o celular), Claro ou Escuro. Vale para todas as telas e para a janelinha do botão flutuante.

## O card

Corpo branco com borda **preta na Uber** e **laranja na 99**, selo com o nome do app, R$/km grande na cor da faixa (verde, amarelo, vermelho) e uma etiqueta Boa, Média ou Baixa.

## Personalizar o card

Na tela principal, **Personalizar o card** permite ajustar, com prévia ao vivo:

- **Tema do card**: claro, escuro ou o mesmo do app (no escuro, a borda da Uber fica branca para aparecer);
- **Tempo na tela** (3 a 12 s): quanto tempo o card continua visível depois que a solicitação some;
- **Tamanho** (60% a 200%) e **opacidade** (10% a 100%, vale para o fundo e a borda);
- **O que mostrar**: R$/km, valor da corrida, distância, R$/h e tempo total;
- **Nota e corridas do passageiro**: a nota (★) e a quantidade de corridas, quando o card do app mostra (99 e Uber);
- **Posição livre**: toque em "Posicionar livremente", arraste o card para qualquer ponto da tela (dá para abrir a Uber ou a 99 antes, para ver o lugar real) e toque em "Salvar posição".; "Voltar à posição padrão" leva o card de volta ao topo;
- **Testar sobre outros apps**: mostra o card por 6 s para você ver o resultado de verdade.

As mudanças valem já na próxima solicitação. Na leitura por OCR (Uber), o app tapa a área do próprio card na imagem antes de ler, para não ler a si mesmo. Mas isso também esconde da leitura o que estiver embaixo dele: se o card cobrir o valor ou os km da solicitação, a Uber deixa de ser lida. Escolha uma posição que não tape esses dados (topo ou borda) e reduza o tamanho se precisar.

## 99: solicitação única e lista de "Solicitações"

- A solicitação única da 99 mostra o R$/km que o próprio app calcula (por exemplo "R$1,53/km"), e esse valor é o preço dividido pelo km até o passageiro somado ao km da viagem. O app usa isso para conferir a leitura por OCR.
- A tela **Solicitações** da 99 lista várias corridas ao mesmo tempo. O app separa cada uma (cada corrida começa na linha que é só o preço) e mostra um card com uma linha por corrida: R$/km na cor da sua faixa, valor e km. Todas entram no histórico.
- A lista também é lida quando o preço vem na mesma linha de "100% dos ganhos" e do "R$/km", ou quando a tela entrega tudo num bloco de texto só. Na leitura por imagem da 99, só a barra de status é ignorada (para ler a 1ª corrida da lista).
- Se a tela da 99 tem algum texto, mas a corrida não é reconhecida, o app também tenta ler por imagem (OCR). Isso gasta mais bateria e pode ser desligado na tela principal em **Ler por imagem também quando a tela tiver texto**. Para funcionar, a captura de tela precisa estar ligada.

## Essa é boa (aviso e aceite automático)

Na tela principal, **Essa é boa**. Uma corrida é boa quando tem pelo menos o **R$/km** que você definiu (padrão R$ 2,00) e o passageiro está a no máximo a **distância** definida (padrão 1,5 km). Quando isso acontece:

- o card mostra **ESSA É BOA!** e o app toca **dois bipes** (volume de alarme), uma vez por corrida;
- com o **aceite automático** ligado (desligado por padrão), o app espera o tempo escolhido (1 a 10 s, padrão 2 s) e aperta **Aceitar** sozinho, só em solicitação única. Ele usa o botão que o app expõe ou toca no lugar onde a leitura por imagem viu o botão. O resultado aparece em "Ver o que o app leu".

Atenção: a Uber e a 99 podem proibir o aceite automático nos termos de uso. Você liga por sua conta e risco, e o app pede confirmação.

## Botão flutuante

Um botão redondo "R$" fica sobre os apps (com o serviço de acessibilidade ativo). Arraste para mudar de lugar: ao soltar, ele gruda na lateral mais próxima (esquerda ou direita). Tocando, abre uma janelinha para o lado de dentro da tela (para a esquerda se o botão está na direita, e vice-versa), com:

- **Últimas viagens**: abre o histórico;
- **Abrir o aplicativo**: abre a tela principal do Assistente 99;
- **Fechar o app**: fecha as telas, esconde o botão flutuante e o card e para a leitura. O serviço de acessibilidade continua ativado no Android (só você pode desligá-lo). Ao abrir o app de novo, o botão volta sozinho e a leitura retoma;
- chaves para **ligar ou desligar a leitura da Uber e da 99** separadamente (desligada, o app ignora aquele app).

Toque fora da janelinha para fechá-la. Para esconder o botão, desligue **Botão flutuante** na tela principal e toque em Salvar. A posição fica guardada.

## Notificação da última corrida

Cada solicitação nova lida pelo app atualiza uma notificação **silenciosa** na barra de notificações (sem som, sem vibração e sem aparecer por cima da tela). Fechada, mostra o app (Uber ou 99), o R$/km, o valor, o tempo e a distância. Aberta (puxe para baixo ou toque na seta), mostra também os endereços, e tem os botões **Embarque no mapa** e **Destino no mapa**. Tocar na notificação abre as últimas viagens. Dá para tirá-la deslizando, e ela volta na próxima corrida.

Para desligar, use **Mostrar a última corrida na barra de notificações** na tela principal. No Android 13 ou mais novo o app pede a permissão de notificações.

## Histórico das últimas 10 viagens

Na tela principal, **Últimas 10 viagens (Uber e 99)** abre a lista das solicitações recebidas. Cada item mostra o app de origem (Uber ou 99), o horário, o R$/km, o valor, o tempo (até o passageiro + viagem), a distância e os endereços de embarque e destino, com um botão para abrir cada endereço no app de mapas do celular. O histórico fica só no aparelho, e "Limpar histórico" apaga tudo.

Os endereços são lidos pela posição no card (o que vem depois do 1º "N min (X km)" é o embarque; depois do 2º é o destino). Isso foi ajustado para o card da Uber. Na 99 o endereço pode aparecer como "não lido" até eu ver o texto real do card dela.

## Ajuste importante: o parser é heurístico

O layout do app da 99 não é público e muda. O parser assume:

- preço = primeiro `R$ xx,xx` da tela;
- 1ª distância = até o passageiro, 2ª = viagem (se houver só uma, é a viagem).

Se o card não aparecer ou mostrar valores errados:

0. Sem computador: ligue o **Modo diagnóstico** na tela principal, salve, abra o app (com a solicitação na tela) e volte em **Ver o que o app leu da tela** > **Compartilhar**.
1. Com computador: conecte o celular e rode `adb logcat -s Assistente99`.
2. Receba uma solicitação e copie a linha `Textos da tela: ...`.
3. Ajuste os regex/ordem em `RideParser.kt`. Os testes em `RideParserTest.kt` mostram o formato esperado; adicione um com o texto real.
4. Se o pacote de algum app for diferente de `com.app99.driver` / `com.ubercab.driver`, mude `DEFAULT_PACKAGES` em `Prefs.kt`. Os tempos aceitam "min" (99) e "minutos" (Uber).

Antes de distribuir, coloque `LOG_TEXTS = false` em `RideAccessibilityService.kt`.

## Avisos

- Os termos de uso da 99 e da Uber podem restringir ferramentas que leem a tela do app. Use por sua conta e risco.
- Alguns celulares (Xiaomi, Samsung, etc.) matam serviços em segundo plano. Desative a otimização de bateria para o Assistente 99 se o card parar de aparecer.
