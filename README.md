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
