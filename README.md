# Mais Ofertas

Backend que capta ofertas (começando pela Amazon), gera legenda com IA e publica automaticamente num canal de Telegram e num grupo de WhatsApp (via Evolution API), monetizando por link de afiliado.

## Arquitetura

```
maisofertas/
  backend/     # Spring Boot 4.1 (Java 21) - todo o pipeline
  infra/       # docker-compose (app + postgres + redis + evolution-api)
```

Pipeline: `POST /deals/manual` (Fase 0) ou sync automático via Canopy API (Fase 1, desligado até a
key estar configurada) → grava no Postgres com dedup → a cada `PUBLISH_INTERVAL_MS`, gera conteúdo
estruturado (hook + nome do produto + specs) via OpenAI, com fallback determinístico se a IA falhar →
cada canal formata a própria mensagem (preço, desconto e link são sempre calculados de forma
determinística, nunca pela IA) → publica no Telegram (MarkdownV2, com texto riscado de verdade no preço
"de") e no WhatsApp (marcação nativa), cada canal marcado como postado independentemente.

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
| `amazon` | `AmazonController` — `POST /deals/manual` (Fase 0) |
| `canopy` | `CanopyClient` (REST, `GET /api/amazon/deals`) e `CanopySyncScheduler` (Fase 1, `@Scheduled`, desligado por padrão) |

## Rodando localmente

Pré-requisito: Java 21+ (o projeto foi gerado/testado com JDK 26 instalado em
`C:\Program Files\Java\jdk-26.0.1`, compilando com target 21).

```bash
cd backend
./mvnw test                    # gate tests (rápidos, sem chamada real de API) - ~1s, 46 testes
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
> um Postgres real via Testcontainers — precisa do Docker Desktop rodando. Os outros 45 gate tests dos
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
4. VPS com Docker pra rodar 24/7 — feito, ver [Deploy na VPS](#deploy-na-vps) abaixo.
5. Chave da OpenAI → `OPENAI_API_KEY`. Sem ela, o app funciona normalmente com o fallback determinístico.

## Deploy na VPS

Stack roda em `deploy@<ip-da-vps>:~/maisofertas` via Docker Compose, perfil `whatsapp` sempre ligado
(Telegram + WhatsApp). O usuário `deploy` está no grupo `docker` e tem sudo sem senha; o Docker do
host já inicia no boot (`systemctl enable docker`), e cada container sobe com `restart: unless-stopped`
— não precisa de systemd unit própria.

O repo é privado, então o clone na VPS usa uma **deploy key read-only** dedicada
(`~/.ssh/maisofertas_deploy_key`, alias `github-maisofertas` no `~/.ssh/config` do usuário `deploy`),
cadastrada nas Deploy Keys do repo no GitHub. Ela só permite `git pull`, nunca push.

Portas: só 22/80/443 estão liberadas no firewall (`ufw`) da VPS — a mesma VPS já hospeda outra
aplicação (nginx + certbot ocupando 80/443). As portas do compose (8081 app, 8082 Evolution API
manager) ficam só em loopback/rede interna do Docker; não são expostas à internet. Pra acessar o
manager do Evolution API (ex: reconectar o WhatsApp depois de um logout), abra um túnel SSH:

```bash
ssh -N -L 8082:localhost:8082 deploy@<ip-da-vps>
# depois abra http://localhost:8082/manager no navegador local
```

### Deploy inicial (já feito)

```bash
ssh deploy@<ip-da-vps>
git clone git@github-maisofertas:jonathanrenz/maisofertas.git ~/maisofertas
# copiar infra/.env local (com os secrets reais) pro mesmo caminho na VPS via scp, chmod 600
cd ~/maisofertas/infra
docker compose --profile whatsapp up -d --build
```

Depois, criar a instância do WhatsApp pelo manager (túnel acima): "New instance" com nome
`maisofertas` (mesmo valor de `EVOLUTION_INSTANCE`), canal Baileys, sem número fixo → "Get QR Code" →
escanear com o WhatsApp que já está no grupo de ofertas alvo (`EVOLUTION_GROUP_JID`).

### Atualizar (deploys seguintes)

```bash
ssh deploy@<ip-da-vps>
cd ~/maisofertas
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
