# Mais Ofertas

Backend que capta ofertas (Amazon e Shopee), gera legenda com IA e publica automaticamente num canal de Telegram e num grupo de WhatsApp (via Evolution API), monetizando por link de afiliado.

## Arquitetura

```
maisofertas/
  backend/     # Spring Boot 4.1 (Java 21) - todo o pipeline
  infra/       # docker-compose (app + postgres + redis + evolution-api)
```

Pipeline: `POST /deals/manual` (Fase 0, qualquer loja) ou sync automático via Canopy API (Fase 1,
Amazon) / Shopee Affiliate Open API (Fase 2, ambas desligadas até a key estar configurada) → grava
no Postgres com dedup → a cada `PUBLISH_INTERVAL_MS`, gera conteúdo estruturado (hook + nome do
produto + specs) via OpenAI, com fallback determinístico se a IA falhar → cada canal formata a
própria mensagem (preço, desconto e link são sempre calculados de forma determinística, nunca pela
IA, com o nome da loja certo - "Ver oferta na Amazon"/"Ver oferta na Shopee") → publica no Telegram
(MarkdownV2, com texto riscado de verdade no preço "de") e no WhatsApp (marcação nativa), cada canal
marcado como postado independentemente.

**Por que Fase 0 é manual (e por que Canopy, não a API oficial da Amazon):** a PA-API da Amazon foi
desativada em 15/mai/2026. O substituto (Creators API) só libera acesso com 10 vendas qualificadas nos
últimos 30 dias — a conta ainda não bateu esse número. Em vez de esperar, o Fase 1 usa a
[Canopy API](https://www.canopyapi.co) (`GET /api/amazon/deals`, REST) como fonte de dados: ela não
exige esse gate, só uma API key. O app só monta o link de afiliado (`AMAZON_ASSOCIATE_TAG`) em cima da
URL que a Canopy devolve — nenhuma lógica de afiliado depende da Canopy. Até configurar
`CANOPY_API_KEY`/`CANOPY_SYNC_ENABLED=true`, as ofertas entram via `POST /deals/manual` — mesmo formato
que o Fase 1 preenche sozinho, então nada no resto do pipeline muda quando a sync for ligada.

## Pacotes (`backend/src/main/java/com/maisofertas/`)

| Pacote | Responsabilidade |
|---|---|
| `deals` | Entidade `Deal`, repositório, dedup (mesma URL não posta 2x em N dias), `PriceFormatter` (preço/desconto em `R$ 1.499,00`, padrão brasileiro), `POST /deals/manual` (`AmazonController`) |
| `ai` | `DealContentGenerator` → `OpenAiDealContentGenerator` (modelo econômico, configurável, `response_format=json_object`, devolve hook + nome do produto + specs extraídas do título) com fallback determinístico automático em `FallbackDealContentGenerator` |
| `telegram` | `TelegramBotClient` — Bot API via REST direto (`parse_mode=MarkdownV2`) — e `TelegramMessageFormatter`, que monta a mensagem e escapa os caracteres reservados do MarkdownV2 |
| `whatsapp` | `EvolutionApiClient` — instância self-hosted do Evolution API — e `WhatsAppMessageFormatter`, que monta a mensagem com a marcação nativa do WhatsApp (`*negrito*`, `~riscado~`) |
| `publish` | `PublishOrchestrator` — `@Scheduled`, gera o conteúdo uma vez e publica pendentes, idempotente por canal, cada canal com sua própria formatação |
| `amazon` | `AmazonController` — `POST /deals/manual` (Fase 0, aceita qualquer `store`) |
| `canopy` | `CanopyClient` (REST, `GET /api/amazon/deals`) e `CanopySyncScheduler` (Fase 1, Amazon, `@Scheduled`, desligado por padrão) |
| `shopee` | `ShopeeClient` (GraphQL, `POST /graphql`, query `productOfferV2`, autenticação HMAC-SHA256) e `ShopeeSyncScheduler` (Fase 2, Shopee, `@Scheduled`, desligado por padrão) |

## Rodando localmente

Pré-requisito: Java 21+ (o projeto foi gerado/testado com JDK 26 instalado em
`C:\Program Files\Java\jdk-26.0.1`, compilando com target 21).

```bash
cd backend
./mvnw test                    # gate tests (rápidos, sem chamada real de API) - ~1s, 60 testes
./mvnw spring-boot:run          # sobe a app (precisa de Postgres - veja infra/)
```

### Subindo a stack completa (Postgres + Redis + Evolution API + app)

```bash
cd infra
cp .env.example .env            # preencha as chaves reais
docker compose up -d --build
```

Por padrão sobe só Postgres + app (fluxo Telegram-only). Redis e Evolution API (WhatsApp) ficam atrás
do profile `whatsapp` — só sobem com:

```bash
docker compose --profile whatsapp up -d --build
```

- App: `http://localhost:8081`
- Evolution API (manager/QR code, só com o profile `whatsapp` ativo): `http://localhost:8080`

### Rodando o eval de qualidade do conteúdo gerado (chamada real e paga à OpenAI)

Não roda no `mvn test` normal. Exige `OPENAI_API_KEY` no ambiente:

```bash
OPENAI_API_KEY=sk-... ./mvnw test -DexcludedGroups= -Dgroups=eval -Dtest=OpenAiDealContentGeneratorEvalTest
```

Roda 12 produtos fixos contra uma rubrica (hook não vazio com emoji e sem preço, nome do produto não
vazio, no máximo 4 specs, sem placeholder vazando) e exige >=90% de aprovação. Sem a chave, o teste é
pulado (não falha o build).

> A suíte completa `mvn test` (sem filtro) também inclui `MaisofertasBackendApplicationTests`, que sobe
> um Postgres real via Testcontainers — precisa do Docker Desktop rodando. Os outros 59 gate tests dos
> pacotes acima **não** precisam de Docker.

## Testando o fluxo manual ponta a ponta

```bash
curl -X POST http://localhost:8081/deals/manual \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Livro de autoconfiança",
    "url": "https://amazon.com.br/dp/EXEMPLO?tag=seu-tag-20",
    "imageUrl": "https://images.example/capa.jpg",
    "price": 39.90,
    "originalPrice": 59.90,
    "store": "AMAZON"
  }'
```

O `PublishOrchestrator` pega esse deal na próxima rodada (`PUBLISH_INTERVAL_MS`, padrão 5 min) e publica
nos dois canais. Reenviar a mesma `url` dentro da janela de dedup (`DEAL_DEDUP_WINDOW_DAYS`, padrão 7
dias) retorna `409 Conflict`.

## Variáveis de ambiente

Ver `infra/.env.example` — cobre banco, dedup/agendamento, OpenAI, Telegram, Evolution API, Canopy e
Shopee.

## Antes de ir pra produção (checklist da Fase 0)

1. Canal do Telegram criado + bot via @BotFather, bot como admin do canal → `TELEGRAM_BOT_TOKEN` e
   `TELEGRAM_CHAT_ID`. Publique algumas ofertas reais (`POST /deals/manual`) antes do passo 2, pra ter
   conteúdo no canal quando a Amazon revisar o cadastro.
2. Cadastro de Associados Amazon aprovado, usando a URL pública do canal (`https://t.me/seu_canal`) no
   campo "site ou app" → SiteStripe habilitado para gerar link manual.
3. (Opcional, fora do Fase 0 inicial) WhatsApp: número dedicado (não usar o pessoal — risco de ban
   recai só nesse número), subir com `docker compose --profile whatsapp up -d`, escanear QR do
   Evolution API, entrar no grupo de ofertas → pegar o JID do grupo para `EVOLUTION_GROUP_JID`. Sem
   isso configurado, o `PublishOrchestrator` só publica no Telegram (WhatsApp fica com erro esperado
   nos logs, sem travar o resto do pipeline).
4. VPS com Docker pra rodar 24/7 — feito, ver [Deploy na VPS](#deploy-na-vps) abaixo.
5. Chave da OpenAI → `OPENAI_API_KEY`. Sem ela, o app funciona normalmente com o fallback determinístico.

## Deploy em produção

Stack pensada pra rodar 24/7 numa VPS qualquer via Docker Compose, perfil `whatsapp` ligado quando o
canal de WhatsApp estiver ativo (Telegram sozinho não precisa dele). Cada container sobe com
`restart: unless-stopped`, então sobrevive a reboot sem precisar de systemd unit própria — só o Docker
do host precisa iniciar no boot (`systemctl enable docker`).

As portas do Compose (app e o manager do Evolution API) não precisam ficar expostas à internet: deixe
só a porta da própria app (ou um reverse proxy na frente dela) liberada no firewall, e acesse o manager
do Evolution API por um túnel SSH quando precisar reconectar o WhatsApp (ex: depois de um logout):

```bash
ssh -N -L 8082:localhost:8082 usuario@sua-vps
# depois abra http://localhost:8082/manager no navegador local
```

### Deploy inicial

```bash
git clone https://github.com/jonathanrenz/maisofertas.git
cd maisofertas/infra
cp .env.example .env            # preencha com os secrets reais, nunca commite
docker compose --profile whatsapp up -d --build
```

Depois, criar a instância do WhatsApp pelo manager (túnel acima): "New instance" com nome
`maisofertas` (mesmo valor de `EVOLUTION_INSTANCE`), canal Baileys, sem número fixo → "Get QR Code" →
escanear com o WhatsApp que já está no grupo de ofertas alvo (`EVOLUTION_GROUP_JID`).

### Atualizar (deploys seguintes)

```bash
cd maisofertas
git pull
cd infra
docker compose --profile whatsapp up -d --build
```

Não precisa recriar a instância do WhatsApp nem o `.env` — só o container da app é rebuildado; Postgres,
Redis e a sessão do Evolution API continuam com os volumes/dados existentes.

## Fase 1 (sync automático via Canopy API)

1. Crie a conta em [canopyapi.co](https://www.canopyapi.co), escolha a interface **REST** no cadastro
   (não GraphQL nem MCP — o backend é um job agendado determinístico, não um agente de IA) e pegue a
   API key no painel. O plano free dá **100 requests/mês**.
2. Preencha `CANOPY_API_KEY` no `.env` e ligue com `CANOPY_SYNC_ENABLED=true`.
3. Confira o orçamento antes de mudar `CANOPY_SYNC_INTERVAL_MS` ou `CANOPY_SYNC_PAGES_PER_RUN`: o
   default (8h de intervalo × 1 página/rodada × 3 rodadas/dia × 30 dias) consome ~90 requests/mês,
   dentro do free tier. Aumentar qualquer um dos dois sem recalcular estoura a cota.
4. `CANOPY_MIN_DISCOUNT_PERCENT` (padrão 20) filtra quais ofertas viram `Deal` — abaixo disso a Canopy
   ainda retorna o produto, mas o `CanopySyncScheduler` ignora.
5. `CANOPY_DOMAIN` (padrão `BR`) escolhe o marketplace (`amazon.com.br`). Outros valores suportados:
   `US`, `UK`, `CA`, `DE`, `FR`, `IT`, `ES`, `AU`, `IN`, `MX`, `JP`, `PL`.

Nada em `deals`, `ai`, `telegram`, `whatsapp` ou `publish` precisa mudar — o `CanopySyncScheduler` só
chama `DealService.createDeal(request, DealSource.CANOPY)`, mesmo contrato que o `POST /deals/manual`
já usa.

## Fase 2 (sync automático via Shopee Affiliate Open API)

Mesmo padrão da Fase 1: `ShopeeSyncScheduler` chama `DealService.createDeal(request, DealSource.SHOPEE)`,
mesmo contrato do `POST /deals/manual` (`store: "SHOPEE"`) e do resto do pipeline — nada em `deals`,
`ai`, `telegram`, `whatsapp` ou `publish` precisou mudar além de trocar o texto fixo "Ver oferta na
Amazon" pelo nome da loja do deal (`Store.displayName()`).

**Contrato validado com uma chamada real em 08/ago/2026** (`ShopeeClientLiveSmokeTest`, lane `live`,
mesmo mecanismo da lane `eval` — fica fora do `mvn test` comum): `priceMin`, `priceDiscountRate`,
`offerLink`, `imageUrl` e a fórmula `originalPrice = atual / (1 - desconto/100)` bateram exatamente
com o retorno real da API pra 5 produtos. Pra rodar de novo (ex: depois de qualquer mudança no
`ShopeeClient`, ou se a Shopee mudar o schema no futuro):

```bash
SHOPEE_APP_ID=... SHOPEE_SECRET=... \
  ./mvnw test -DexcludedGroups= -Dgroups=live -Dtest=ShopeeClientLiveSmokeTest
```

Imprime cada oferta buscada (título, preço, `originalPrice`, link) pra conferência visual e falha se
algum campo essencial vier nulo — sem tocar Postgres, Telegram ou WhatsApp.

Pra ligar de verdade:

1. `SHOPEE_APP_ID`/`SHOPEE_SECRET` no `.env` (painel `affiliate.shopee.com.br` → Open API).
2. `SHOPEE_SYNC_ENABLED=true`. A partir daí o `ShopeeSyncScheduler` grava deals `PENDING` no Postgres
   e o `PublishOrchestrator` já existente os publica nos canais reais na próxima rodada — não é mais
   um teste, é produção. Se só quiser validar antes de publicar de verdade, confira primeiro no
   Postgres (`SELECT * FROM deals WHERE store = 'SHOPEE'`) com `PUBLISH_INTERVAL_MS` alto o bastante
   pra dar tempo de olhar.
3. `SHOPEE_MIN_DISCOUNT_PERCENT` (padrão 20) e `SHOPEE_SYNC_PAGES_PER_RUN`/`SHOPEE_SYNC_INTERVAL_MS`
   funcionam como no Canopy — calcule o consumo de requests do seu plano antes de aumentar qualquer um.
4. `SHOPEE_KEYWORD` vazio busca o catálogo geral (sem filtro de termo); `SHOPEE_SORT_TYPE` (padrão `5`
   = maior comissão) aceita `1` (relevância), `2` (mais vendidos), `3` (maior preço) e `4` (menor preço)
   — nenhum ordena por desconto direto, então o filtro de qualidade real é o `SHOPEE_MIN_DISCOUNT_PERCENT`
   aplicado depois.

**Como a comissão funciona:** diferente da Amazon (link normal + `tag=` colado por cima), a Shopee
Affiliate API já devolve o link com o tracking de afiliado embutido no campo `offerLink` (link curto
`s.shopee.com.br/...`, atrelado ao seu `SHOPEE_APP_ID`) - o `productLink` que vem junto é só a página
normal do produto, sem comissão nenhuma. O `ShopeeClient` usa **só** `offerLink`; se algum produto vier
sem ele, o `ShopeeSyncScheduler` descarta o item (conta como "ignorado" no log) em vez de publicar uma
oferta que não renderia comissão.

Testar manualmente um deal Shopee sem esperar o sync (mesmo endpoint da Fase 0, só troca o `store`) -
**use sempre o `offerLink`** (link curto `s.shopee.com.br/...`), nunca a URL normal do produto: gere um
pelo app Shopee Afiliados (ícone de compartilhar em qualquer produto → "Copiar link" com a conta de
afiliado logada) ou via `productOfferV2(itemId: X)` na mesma API:

```bash
curl -X POST http://localhost:8081/deals/manual \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Fone de Ouvido Bluetooth TWS",
    "url": "https://s.shopee.com.br/exemplo",
    "imageUrl": "https://cf.shopee.com.br/file/exemplo.jpg",
    "price": 65.00,
    "originalPrice": 100.00,
    "store": "SHOPEE"
  }'
```
