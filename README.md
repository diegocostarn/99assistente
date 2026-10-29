# Assistente 99

App Android que lê, em tempo real, a solicitação de corrida na tela do app de motorista da **99** ou da **Uber** e mostra sobre ela, na hora, o **R$ por km** (e opcionalmente R$/h e lucro estimado).

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
2. Ajuste as faixas de R$/km, o custo do carro por km (opcional) e se o km até o passageiro entra na conta. Toque em Salvar.
3. Abra o app da 99 ou da Uber, fique online e aguarde uma solicitação. O mesmo card funciona nos dois.

## Uber: leitura por captura de tela (OCR)

O card de solicitação da Uber não expõe o texto ao serviço de acessibilidade (o modo diagnóstico mostra "tela sem nenhum texto legível"). Para esse caso o app lê a imagem da tela:

1. Na tela principal, toque em **2) Ligar leitura por captura de tela** e aceite o aviso do Android ("Começar a gravar/transmitir").
2. Uma notificação "Assistente 99" fica ativa enquanto a captura está ligada. Se tirar o app da memória, ligue de novo.
3. Só quando um app monitorado mostra uma tela sem texto o app tira um print (na memória, nada é salvo nem enviado), lê com OCR no aparelho e mostra o R$/km. Nas outras telas não gasta bateria extra.

O texto lido por OCR aparece em **Ver o que o app leu da tela**, na seção "LEITURA POR OCR".

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
4. Se o pacote de algum app for diferente (`com.app99.driver`, `com.ubercab.driver`), mude na tela de configuração. Os tempos aceitam "min" (99) e "minutos" (Uber).

Antes de distribuir, coloque `LOG_TEXTS = false` em `RideAccessibilityService.kt`.

## Avisos

- Os termos de uso da 99 e da Uber podem restringir ferramentas que leem a tela do app. Use por sua conta e risco.
- Alguns celulares (Xiaomi, Samsung, etc.) matam serviços em segundo plano. Desative a otimização de bateria para o Assistente 99 se o card parar de aparecer.
