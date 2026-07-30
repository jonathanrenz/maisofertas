# Mais Ofertas

Backend que capta ofertas (começando pela Amazon), gera legenda com IA e publica automaticamente num canal de Telegram e num grupo de WhatsApp (via Evolution API), monetizando por link de afiliado.

## Arquitetura

```
maisofertas/
  backend/     # Spring Boot 4.1 (Java 21) - todo o pipeline
  infra/       # docker-compose (app + postgres + redis + evolution-api)
```

Pipeline: `POST /deals/manual` (Fase 0) ou sync automático via Canopy API (Fase 1, desligado até a
key estar configurada) → grava no Postgres com dedup → a cada `PUBLISH_INTERVAL_MS`, gera legenda via
OpenAI (com fallback determinístico se a IA falhar) → publica no Telegram e no WhatsApp, cada canal
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
| `deals` | Entidade `Deal`, repositório, dedup (mesma URL não posta 2x em N dias), `POST /deals/manual` (`AmazonController`) |
| `ai` | `CaptionGenerator` → `OpenAiCaptionGenerator` (modelo econômico, configurável) com fallback determinístico automático em `FallbackCaptionGenerator` |
| `telegram` | `TelegramBotClient` — Bot API via REST direto |
| `whatsapp` | `EvolutionApiClient` — instância self-hosted do Evolution API |
| `publish` | `PublishOrchestrator` — `@Scheduled`, publica pendentes, idempotente por canal |
| `amazon` | `AmazonController` — `POST /deals/manual` (Fase 0) |
| `canopy` | `CanopyClient` (REST, `GET /api/amazon/deals`) e `CanopySyncScheduler` (Fase 1, `@Scheduled`, desligado por padrão) |

## Rodando localmente

Pré-requisito: Java 21+ (o projeto foi gerado/testado com JDK 26 instalado em
`C:\Program Files\Java\jdk-26.0.1`, compilando com target 21).

```bash
cd backend
./mvnw test                    # gate tests (rápidos, sem chamada real de API) - ~1s, 29 testes
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

### Rodando o eval de qualidade da legenda (chamada real e paga à OpenAI)

Não roda no `mvn test` normal. Exige `OPENAI_API_KEY` no ambiente:

```bash
OPENAI_API_KEY=sk-... ./mvnw test -DexcludedGroups= -Dgroups=eval -Dtest=OpenAiCaptionGeneratorEvalTest
```

Roda 12 produtos fixos contra uma rubrica (tem emoji, menciona preço, tamanho ok, sem placeholder
vazando) e exige >=90% de aprovação. Sem a chave, o teste é pulado (não falha o build).

> A suíte completa `mvn test` (sem filtro) também inclui `MaisofertasBackendApplicationTests`, que sobe
> um Postgres real via Testcontainers — precisa do Docker Desktop rodando. Os 29 gate tests dos pacotes
> acima **não** precisam de Docker.

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

Ver `infra/.env.example` — cobre banco, dedup/agendamento, OpenAI, Telegram, Evolution API e Canopy.

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
4. VPS com Docker pra rodar 24/7 (hoje só documentado rodando local).
5. Chave da OpenAI → `OPENAI_API_KEY`. Sem ela, o app funciona normalmente com o fallback determinístico.

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
