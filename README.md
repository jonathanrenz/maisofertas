# Mais Ofertas

Backend que capta ofertas (começando pela Amazon), gera legenda com IA e publica automaticamente num canal de Telegram e num grupo de WhatsApp (via Evolution API), monetizando por link de afiliado.

## Arquitetura

```
maisofertas/
  backend/     # Spring Boot 4.1 (Java 21) - todo o pipeline
  infra/       # docker-compose (app + postgres + redis + evolution-api)
```

Pipeline: `POST /deals/manual` (Fase 0) ou sync automático da Amazon (Fase 1, ainda desligado)
→ grava no Postgres com dedup → a cada `PUBLISH_INTERVAL_MS`, gera legenda via OpenAI (com fallback
determinístico se a IA falhar) → publica no Telegram e no WhatsApp, cada canal marcado como postado
independentemente.

**Por que Fase 0 é manual:** a PA-API da Amazon foi desativada em 15/mai/2026. O substituto (Creators
API) só libera acesso com 10 vendas qualificadas nos últimos 30 dias. Até bater esse número (ou decidir
pagar a Keepa antes disso), as ofertas entram via `POST /deals/manual` — mesmo formato que a Fase 1 vai
preencher sozinha, então nada no resto do pipeline muda quando a API for ligada.

## Pacotes (`backend/src/main/java/com/maisofertas/`)

| Pacote | Responsabilidade |
|---|---|
| `deals` | Entidade `Deal`, repositório, dedup (mesma URL não posta 2x em N dias), `POST /deals/manual` (`AmazonController`) |
| `ai` | `CaptionGenerator` → `OpenAiCaptionGenerator` (modelo econômico, configurável) com fallback determinístico automático em `FallbackCaptionGenerator` |
| `telegram` | `TelegramBotClient` — Bot API via REST direto |
| `whatsapp` | `EvolutionApiClient` — instância self-hosted do Evolution API |
| `publish` | `PublishOrchestrator` — `@Scheduled`, publica pendentes, idempotente por canal |
| `amazon` | `AmazonController` (entrada manual) e `AmazonSyncScheduler` (Fase 1, desligado) |

## Rodando localmente

Pré-requisito: Java 21+ (o projeto foi gerado/testado com JDK 26 instalado em
`C:\Program Files\Java\jdk-26.0.1`, compilando com target 21).

```bash
cd backend
./mvnw test                    # gate tests (rápidos, sem chamada real de API) - ~1s, 18 testes
./mvnw spring-boot:run          # sobe a app (precisa de Postgres - veja infra/)
```

### Subindo a stack completa (Postgres + Redis + Evolution API + app)

```bash
cd infra
cp .env.example .env            # preencha as chaves reais
docker compose up -d --build
```

- App: `http://localhost:8081`
- Evolution API (manager/QR code): `http://localhost:8080`

### Rodando o eval de qualidade da legenda (chamada real e paga à OpenAI)

Não roda no `mvn test` normal. Exige `OPENAI_API_KEY` no ambiente:

```bash
OPENAI_API_KEY=sk-... ./mvnw test -DexcludedGroups= -Dgroups=eval -Dtest=OpenAiCaptionGeneratorEvalTest
```

Roda 12 produtos fixos contra uma rubrica (tem emoji, menciona preço, tamanho ok, sem placeholder
vazando) e exige >=90% de aprovação. Sem a chave, o teste é pulado (não falha o build).

> A suíte completa `mvn test` (sem filtro) também inclui `MaisofertasBackendApplicationTests`, que sobe
> um Postgres real via Testcontainers — precisa do Docker Desktop rodando. Os 18 gate tests dos pacotes
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

Ver `infra/.env.example` — cobre banco, dedup/agendamento, OpenAI, Telegram e Evolution API.

## Antes de ir pra produção (checklist da Fase 0)

1. Cadastro de Associados Amazon aprovado → SiteStripe habilitado para gerar link manual.
2. Canal do Telegram criado + bot via @BotFather, bot como admin do canal → `TELEGRAM_BOT_TOKEN` e
   `TELEGRAM_CHAT_ID`.
3. Número dedicado para o WhatsApp (não usar o pessoal — risco de ban recai só nesse número).
4. VPS com Docker, `docker compose up -d`, escanear QR do Evolution API, entrar no grupo de ofertas →
   pegar o JID do grupo para `EVOLUTION_GROUP_JID`.
5. Chave da OpenAI → `OPENAI_API_KEY`. Sem ela, o app funciona normalmente com o fallback determinístico.

## Fase 1 (quando destravar API da Amazon)

Implementar a lógica real dentro de `AmazonSyncScheduler` (Creators API ou Keepa) chamando
`DealService.createDeal(request, DealSource.CREATORS_API)` (ou `KEEPA`), e ligar com
`AMAZON_SYNC_ENABLED=true`. Nada em `deals`, `ai`, `telegram`, `whatsapp` ou `publish` precisa mudar.
