# Serviço de Ficheiros

Serviço único e genérico de upload e acesso a ficheiros, partilhado por várias aplicações.
Cada aplicação tem o seu identificador e a sua chave, e só vê os seus próprios ficheiros.

- **Um serviço, um servidor**: os ficheiros ficam no disco do servidor, numa pasta por aplicação; os metadados ficam em PostgreSQL.
- **Backend e browser**: os backends autenticam-se com identificador + chave de API; os browsers usam links temporários assinados, sem nunca conhecerem a chave.
- **Stack**: Java 21, Spring Boot 4 (Web MVC, JDBC, Flyway, Actuator), PostgreSQL, Apache Tika. Corre como serviço systemd em Ubuntu, atrás de Nginx.

## Índice

1. [Testar com Postman](./POSTMAN-TESTES.md)
2. [Guia prático de utilização](./GUIA-DE-USO.md)
3. [Painel de gestão Next.js](./dashboard/README.md)
4. [Conceitos](#conceitos)
5. [Arranque em desenvolvimento](#arranque-em-desenvolvimento)
6. [Como as aplicações usam o serviço](#como-as-aplicações-usam-o-serviço)
7. [Referência da API](#referência-da-api)
8. [Erros](#erros)
9. [Configuração](#configuração)
10. [Deploy em Ubuntu](#deploy-em-ubuntu)
11. [Segurança](#segurança)
12. [Estrutura do projecto](#estrutura-do-projecto)
13. [Testes](#testes)
14. [Evolução prevista](#evolução-prevista)

## Conceitos

| Conceito | Descrição |
|---|---|
| **Aplicação** | Sistema cliente registado no serviço. Tem um `code` (ex.: `portal-exemplo`), que é o seu identificador público, e uma chave de API secreta. |
| **Chave de API** | Segredo `fsk_…` gerado pelo serviço. É mostrado **uma única vez**, na criação ou na rotação; na base de dados fica apenas o seu SHA-256. |
| **Ficheiro** | Conteúdo no disco + metadados (nome original, tipo detectado, tamanho, SHA-256, pasta lógica, autor). Identificado por um UUID. |
| **Visibilidade** | `PRIVATE` (por omissão): só com credenciais ou link assinado. `PUBLIC`: URL estável sem credenciais, para imagens de sites públicos, por exemplo. |
| **Pasta** | Agrupamento lógico definido pela aplicação (ex.: `facturas/2026`). Não corresponde a pastas no disco. |
| **Link assinado** | URL temporário com assinatura HMAC. Há dois tipos: de **download** (ler um ficheiro privado) e de **upload** (o browser envia ficheiros em nome da aplicação). |

No disco, os ficheiros ficam em `{raiz}/{aplicação}/{ano}/{mês}/{uuid}`. O nome no disco é sempre o UUID: nada do que o cliente envia entra no caminho.

## Arranque em desenvolvimento

Requisitos: JDK 21, Maven 3.9+ e PostgreSQL 14 ou superior.

```bash
# 1. Base de dados local (utilizador e base "fileservice", password "fileservice")
sudo -u postgres psql -c "create role fileservice login password 'fileservice'"
sudo -u postgres psql -c "create database fileservice owner fileservice"

# 2. Compilar, correr os testes e arrancar com o perfil de desenvolvimento
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Verificar
curl -s http://localhost:8085/actuator/health
```

As tabelas são criadas no primeiro arranque (migrações Flyway em `src/main/resources/db/migration`).
O perfil `dev` guarda os ficheiros em `./data/storage` e usa segredos fixos, só para a máquina de desenvolvimento.

> **Versão do Spring Boot.** O `pom.xml` aponta para a linha 4.x. Antes do primeiro deploy, actualizar
> `spring-boot-starter-parent` para o patch mais recente dessa linha.

### Primeira aplicação e primeiro ficheiro

```bash
ADMIN_KEY=dev-admin-key-apenas-para-desenvolvimento
BASE=http://localhost:8085

# Registar a aplicação (a resposta traz a apiKey: guardar, não volta a ser mostrada)
curl -s -X POST $BASE/api/v1/admin/applications \
  -H "X-Admin-Key: $ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{"code":"portal-exemplo","name":"Portal de Exemplo","allowedOrigins":["http://localhost:5173"]}'

API_KEY=fsk_...   # valor de "apiKey" na resposta anterior

# Enviar um ficheiro
curl -s -X POST $BASE/api/v1/files \
  -H "X-App-Id: portal-exemplo" -H "X-Api-Key: $API_KEY" \
  -F "file=@relatorio.pdf" -F "folder=relatorios/2026"

# Descarregar (trocar {id} pelo "id" devolvido no upload)
curl -s -OJ $BASE/api/v1/files/{id}/content \
  -H "X-App-Id: portal-exemplo" -H "X-Api-Key: $API_KEY"
```

## Como as aplicações usam o serviço

### A. A partir do backend (servidor a servidor)

O backend da aplicação guarda a chave de API em configuração segura e chama a API com os cabeçalhos
`X-App-Id` e `X-Api-Key`. É o modo mais simples e dá acesso a todas as operações.

### B. A partir do browser (links assinados)

A chave de API **não deve** ir para o código do frontend. Em vez disso:

```
Browser                    Backend da aplicação            Serviço de ficheiros
   │  1. quero enviar um ficheiro  │                               │
   │ ─────────────────────────────▶│  2. POST /api/v1/upload-urls  │
   │                               │ ─────────────────────────────▶│
   │                               │◀───── { url, expiresAt } ─────│
   │◀──────── { url } ─────────────│                               │
   │  3. POST url (multipart, campo "file")                        │
   │ ─────────────────────────────────────────────────────────────▶│
   │◀──────────────── 201 { id, filename, ... } ───────────────────│
   │  4. guarda o id               │                               │
   │ ─────────────────────────────▶│                               │
```

**Passo 2 — o backend pede o link** (exemplo em Node; em Java é o mesmo pedido HTTP):

```js
const resposta = await fetch(`${FILES_URL}/api/v1/upload-urls`, {
  method: 'POST',
  headers: { 'X-App-Id': APP_ID, 'X-Api-Key': API_KEY, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    folder: 'avatars',          // opcional
    visibility: 'PRIVATE',      // PRIVATE (por omissão) ou PUBLIC
    uploadedBy: utilizador.id,  // opcional: fica registado no ficheiro
    expiresInSeconds: 300,      // opcional: por omissão 5 minutos
  }),
});
const { url } = await resposta.json();   // entregar este url ao browser
```

A pasta, a visibilidade e o autor ficam fixados na assinatura: o browser não os consegue alterar.

**Passo 3 — o browser envia o ficheiro directamente:**

```js
const form = new FormData();
form.append('file', input.files[0]);

const resposta = await fetch(url, { method: 'POST', body: form });
if (!resposta.ok) {
  const problema = await resposta.json();      // { status, code, detail, ... }
  throw new Error(problema.detail);
}
const ficheiro = await resposta.json();        // { id, filename, contentType, sizeBytes, ... }
```

**Mostrar ou descarregar um ficheiro privado:** o backend pede um link de leitura e entrega-o ao browser.

```js
// Backend
const r = await fetch(`${FILES_URL}/api/v1/files/${id}/signed-url`, {
  method: 'POST',
  headers: { 'X-App-Id': APP_ID, 'X-Api-Key': API_KEY, 'Content-Type': 'application/json' },
  body: JSON.stringify({ expiresInSeconds: 300 }),
});
const { url } = await r.json();

// Browser
// <img src={url} />                      mostra a imagem
// <a href={url + '&download=true'}>      força o download
```

**Ficheiros públicos:** um ficheiro enviado com `visibility=PUBLIC` devolve um `publicUrl` estável,
que pode ser usado directamente em `<img src>` sem qualquer assinatura.

**CORS:** para o browser poder chamar o serviço, a origem do frontend (ex.: `https://portal.exemplo.co.ao`)
tem de estar em `allowedOrigins` da aplicação. Um pedido vindo de uma origem que não pertence à aplicação
é recusado com `ORIGIN_NOT_ALLOWED`, mesmo com credenciais válidas.

## Referência da API

### Administração — cabeçalho `X-Admin-Key`

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/v1/admin/applications` | Regista uma aplicação. Devolve `{ application, apiKey }`. |
| `GET` | `/api/v1/admin/applications` | Lista as aplicações. |
| `GET` | `/api/v1/admin/applications/{code}` | Detalhe de uma aplicação. |
| `PUT` | `/api/v1/admin/applications/{code}` | Substitui nome, origens, limites e estado. O código e a chave não mudam. |
| `POST` | `/api/v1/admin/applications/{code}/rotate-key` | Gera uma chave nova; a anterior deixa de funcionar de imediato. |

Corpo de `POST` e `PUT`:

```json
{
  "code": "portal-exemplo",
  "name": "Portal de Exemplo",
  "allowedOrigins": ["https://portal.exemplo.co.ao"],
  "maxFileSizeBytes": 10485760,
  "allowedContentTypes": ["image/*", "application/pdf"],
  "storageQuotaBytes": 5368709120,
  "active": true
}
```

Só `code` (na criação) e `name` são obrigatórios. Sem `maxFileSizeBytes` e `allowedContentTypes`
valem os limites globais; sem `storageQuotaBytes` não há quota. `"active": false` desactiva a aplicação:
deixa de autenticar e os seus ficheiros deixam de ser servidos.

A API de administração não aceita pedidos de browsers noutra origem (CORS): destina-se a `curl`,
scripts e ferramentas internas.

### Ficheiros — cabeçalhos `X-App-Id` + `X-Api-Key`

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/v1/files` | Upload (`multipart/form-data`). Campos: `file` (obrigatório), `folder`, `visibility`, `uploadedBy`. Devolve `201` com os metadados. |
| `GET` | `/api/v1/files` | Lista os ficheiros da aplicação, mais recentes primeiro. Parâmetros: `folder`, `page` (desde 0), `size` (1–100, por omissão 20). |
| `GET` | `/api/v1/files/{id}` | Metadados. |
| `GET` | `/api/v1/files/{id}/content` | Conteúdo. `?download=true` força o download. Suporta `Range` e `If-None-Match`. |
| `DELETE` | `/api/v1/files/{id}` | Elimina o conteúdo; o registo fica marcado como eliminado, para auditoria. Devolve `204`. |
| `POST` | `/api/v1/files/{id}/signed-url` | Link temporário de leitura. Corpo opcional: `{ "expiresInSeconds": 300 }`. |
| `POST` | `/api/v1/upload-urls` | Link temporário de upload. Corpo opcional: `{ folder, visibility, uploadedBy, expiresInSeconds }`. |

Metadados de um ficheiro:

```json
{
  "id": "0b6f1c1e-5a3b-4f0e-9a51-2f6f3a2d9c11",
  "filename": "relatorio.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 184320,
  "checksumSha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "visibility": "PRIVATE",
  "folder": "relatorios/2026",
  "uploadedBy": "u-42",
  "createdAt": "2026-11-17T10:00:00Z",
  "publicUrl": null
}
```

### Acesso sem chave — pensado para o browser

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/api/v1/public/files/{id}` | Ficheiros `PUBLIC`, ou privados com `?exp=…&sig=…` (link assinado). `&download=true` força o download. |
| `POST` | `/api/v1/public/uploads?app=…&exp=…&sig=…` | Upload com link assinado (`multipart/form-data`, campo `file`). Usar o `url` devolvido por `/api/v1/upload-urls` tal como está. |

### Monitorização

`GET /actuator/health` devolve `{"status":"UP"}` quando a base de dados e o disco estão disponíveis.

## Erros

Todas as respostas de erro usam `application/problem+json` (RFC 9457):

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Ficheiro não encontrado.", "code": "NOT_FOUND" }
```

O campo `code` é estável e serve para o frontend decidir o que mostrar.

| HTTP | `code` | Quando |
|---|---|---|
| 400 | `EMPTY_FILE`, `INVALID_FOLDER`, `INVALID_VISIBILITY`, `INVALID_UPLOADED_BY`, `INVALID_PAGINATION`, `INVALID_EXPIRATION`, `INVALID_MULTIPART` | Pedido mal formado. |
| 400 | `INVALID_APP_CODE`, `INVALID_APP_NAME`, `INVALID_ORIGIN`, `INVALID_CONTENT_TYPE`, `INVALID_LIMIT`, `INVALID_REQUEST` | Dados de aplicação inválidos (administração). |
| 401 | `UNAUTHORIZED` | Credenciais em falta ou inválidas. |
| 403 | `APP_DISABLED` | Aplicação desactivada. |
| 403 | `ORIGIN_NOT_ALLOWED` | Origem do browser não registada na aplicação. |
| 403 | `INVALID_SIGNATURE` | Link assinado inválido, alterado ou expirado. |
| 403 | `ADMIN_DISABLED` | Servidor sem chave de administração configurada. |
| 404 | `NOT_FOUND` | Ficheiro ou aplicação inexistente — ou pertencente a outra aplicação. |
| 409 | `APP_CODE_EXISTS` | Já existe uma aplicação com esse código. |
| 413 | `FILE_TOO_LARGE`, `QUOTA_EXCEEDED` | Limite por ficheiro ou quota da aplicação ultrapassados. |
| 415 | `CONTENT_TYPE_NOT_ALLOWED` | Tipo de ficheiro não permitido (detectado pelo conteúdo, não pela extensão). |
| 500 | `INTERNAL_ERROR`, `FILE_MISSING` | Erro interno; os detalhes ficam nos registos do servidor. |

Os erros gerados pelo próprio Spring MVC (JSON inválido, parâmetro em falta, método não suportado,
upload acima do tecto global) seguem o mesmo formato, sem o campo `code`.

## Configuração

Tudo se configura por variáveis de ambiente (em produção, no ficheiro `/etc/fileservice/fileservice.env`).

| Variável | Por omissão | Descrição |
|---|---|---|
| `FILESERVICE_PORT` | `8085` | Porta HTTP do serviço. |
| `SERVER_ADDRESS` | todas as interfaces | Em produção usar `127.0.0.1`, para o acesso passar sempre pelo Nginx. |
| `FILESERVICE_PUBLIC_BASE_URL` | derivado do pedido | URL público usado nos links devolvidos (ex.: `https://portal.pumangol.co.ao/ficheiros` nesta configuração). |
| `FILESERVICE_STORAGE_PATH` | `/var/lib/fileservice/storage` | Pasta raiz dos ficheiros. |
| `FILESERVICE_DB_URL` | `jdbc:postgresql://localhost:5432/fileservice` | Ligação à base de dados. |
| `FILESERVICE_DB_USER` / `FILESERVICE_DB_PASSWORD` | `fileservice` / vazio | Credenciais da base de dados. |
| `FILESERVICE_ADMIN_KEY` | vazio | Chave de administração (mín. 24 caracteres). Vazia desliga a API de administração. |
| `FILESERVICE_SIGNING_SECRET` | — | **Obrigatório.** Segredo HMAC dos links (mín. 32 caracteres). O serviço não arranca sem ele. |

Limites globais em `application.yml` (podem ser sobrepostos por variáveis de ambiente do Spring,
por exemplo `FILESERVICE_LIMITS_MAX_FILE_SIZE=50MB`):

| Propriedade | Por omissão | Descrição |
|---|---|---|
| `fileservice.limits.max-file-size` | `20MB` | Tamanho máximo por ficheiro, para aplicações sem limite próprio. |
| `fileservice.limits.allowed-content-types` | imagens, PDF, texto, CSV, Office | Tipos permitidos, para aplicações sem lista própria. |
| `fileservice.signed-urls.default-ttl` / `max-ttl` | `5m` / `24h` | Validade por omissão e máxima dos links assinados. |
| `spring.servlet.multipart.max-file-size` | `100MB` | Tecto absoluto do servidor. Nenhum limite de aplicação o pode ultrapassar. |

## Deploy em Ubuntu

A pasta `deploy/` tem tudo o que é preciso: unidade systemd, ficheiro de ambiente de exemplo,
configuração Nginx (`nginx-fileservice.conf` para domínio dedicado ou
`nginx-fileservice-location.conf` para acrescentar uma rota ao portal existente), script SQL e
um instalador.

### Publicar em `portal.pumangol.co.ao/ficheiros`

Esta opção reutiliza o HTTPS e o Nginx já configurados para o portal. No servidor de destino,
a porta `8085` já é usada pelo WildFly; por isso, o serviço de ficheiros escuta em
`127.0.0.1:18085`. O Nginx encaminha `/ficheiros/...` para essa porta, removendo o prefixo
antes de enviar o pedido. Não substitua a configuração existente do portal pela configuração
autónoma `nginx-fileservice.conf`.

1. Na máquina de desenvolvimento, na raiz deste projecto, compile o JAR:

   ```powershell
   mvn clean package
   ```

2. Copie o JAR e a pasta `deploy/` para um directório temporário no servidor. Substitua
   `<UTILIZADOR_SSH>` pelo utilizador Linux autorizado:

   ```powershell
   ssh <UTILIZADOR_SSH>@4.221.139.32 "mkdir -p ~/fileservice"
   scp .\target\file-service.jar <UTILIZADOR_SSH>@4.221.139.32:~/fileservice/
   scp -r .\deploy <UTILIZADOR_SSH>@4.221.139.32:~/fileservice/
   ```

3. No servidor, instale os requisitos, se ainda não estiverem instalados, e instale a aplicação:

   ```bash
   sudo apt update
   sudo apt install -y openjdk-21-jre-headless postgresql nginx
   sudo bash ~/fileservice/deploy/instalar.sh ~/fileservice/file-service.jar
   ```

   Na primeira instalação, o instalador cria `/etc/fileservice/fileservice.env` a partir do
   exemplo e **não inicia** o serviço antes de configurar a base de dados e os segredos.

4. Crie a base e o utilizador PostgreSQL, se ainda não existirem. Gere uma palavra-passe forte,
   abra o PostgreSQL como administrador e use a mesma palavra-passe no passo seguinte:

   ```bash
   openssl rand -hex 32
   sudo -u postgres psql
   ```

   No prompt `psql`, crie o role e a base apenas se ainda não existirem. O comando `\password`
   pede a palavra-passe sem a guardar no histórico SQL. Se o role já existir, execute apenas
   `\password fileservice` para definir a palavra-passe usada no ficheiro de ambiente:

   ```sql
   CREATE ROLE fileservice LOGIN;
   CREATE DATABASE fileservice OWNER fileservice ENCODING 'UTF8' TEMPLATE template0;
   \password fileservice
   \q
   ```

5. Gere os segredos de administração e assinatura e edite a configuração:

   ```bash
   openssl rand -hex 36
   openssl rand -hex 48
   sudoedit /etc/fileservice/fileservice.env
   ```

   Defina `FILESERVICE_DB_PASSWORD` para a palavra-passe do role PostgreSQL, `FILESERVICE_ADMIN_KEY`
   para o primeiro segredo gerado, `FILESERVICE_SIGNING_SECRET` para o segundo e mantenha
   `FILESERVICE_PUBLIC_BASE_URL=https://portal.pumangol.co.ao/ficheiros`. Confirme também
   `FILESERVICE_PORT=18085` e `SERVER_ADDRESS=127.0.0.1`. A porta `8085` está ocupada pelo
   WildFly neste servidor. O ficheiro contém segredos: não o envie por email nem o coloque no Git.

6. Instale o fragmento Nginx sem substituir os blocos existentes do portal:

   ```bash
   sudo install -o root -g root -m 644 \
     ~/fileservice/deploy/nginx-fileservice-location.conf \
     /etc/nginx/snippets/fileservice-location.conf
   ```

   Na configuração HTTPS existente de `portal.pumangol.co.ao`, dentro do bloco `server { ... }`
   que escuta na porta 443, acrescente:

   ```nginx
   include /etc/nginx/snippets/fileservice-location.conf;
   ```

   Coloque a inclusão nesse bloco HTTPS (não no bloco HTTP que apenas redirecciona para HTTPS).
   O certificado TLS existente tem de ser válido para `portal.pumangol.co.ao`. Valide e aplique:

   ```bash
   sudo nginx -t
   sudo systemctl reload nginx
   ```

7. Inicie o serviço e teste primeiro localmente no servidor, depois através do portal:

   ```bash
   sudo systemctl start fileservice
   sudo systemctl status fileservice --no-pager
   curl -i http://127.0.0.1:18085/actuator/health
   curl -i https://portal.pumangol.co.ao/ficheiros/api/v1/admin/applications
   ```

   O health check local deve devolver `200` e `{"status":"UP"}`. O último comando, sem
   `X-Admin-Key`, deve devolver `401`; isso confirma que o pedido chegou à API protegida, e não
   que a autenticação de administração já foi validada. Para investigar falhas:

   ```bash
   sudo journalctl -u fileservice -n 100 --no-pager
   sudo tail -n 100 /var/log/nginx/error.log
   ```

```bash
# No servidor (Ubuntu 22.04 ou 24.04)
sudo apt update
sudo apt install -y openjdk-21-jre-headless postgresql nginx

# 1. Compilar (na máquina de build) e copiar o projecto ou o JAR + pasta deploy/ para o servidor
mvn clean package            # gera target/file-service.jar

# 2. Instalar: cria o utilizador "fileservice", as pastas, o JAR e o serviço systemd
sudo ./deploy/instalar.sh target/file-service.jar

# 3. Base de dados (editar primeiro a password dentro do ficheiro)
sudo -u postgres psql -v ON_ERROR_STOP=1 -f deploy/criar-base-de-dados.sql

# 4. Segredos e configuração
openssl rand -base64 36      # valor para FILESERVICE_ADMIN_KEY
openssl rand -base64 48      # valor para FILESERVICE_SIGNING_SECRET
sudoedit /etc/fileservice/fileservice.env

# 5. Arrancar e verificar
sudo systemctl start fileservice
curl -s http://127.0.0.1:8085/actuator/health
journalctl -u fileservice -f

# 6. Nginx (ajustar server_name e certificado dentro do ficheiro)
sudo cp deploy/nginx-fileservice.conf /etc/nginx/sites-available/fileservice
sudo ln -s /etc/nginx/sites-available/fileservice /etc/nginx/sites-enabled/fileservice
sudo nginx -t && sudo systemctl reload nginx
```

**Actualizar para uma nova versão:** voltar a correr `sudo ./deploy/instalar.sh target/file-service.jar`.
O instalador substitui o JAR, reinicia o serviço e não toca na configuração nem nos ficheiros guardados.

| Onde | O quê |
|---|---|
| `/opt/fileservice/file-service.jar` | Aplicação. |
| `/etc/fileservice/fileservice.env` | Configuração e segredos (`root:fileservice`, modo 640). |
| `/var/lib/fileservice/storage` | Ficheiros guardados. |
| `journalctl -u fileservice` | Registos. |

**Cópias de segurança.** O estado do serviço são duas coisas que têm de ser salvaguardadas em conjunto:
a base de dados `fileservice` (`pg_dump`) e a pasta `/var/lib/fileservice/storage`.

## Segurança

- **Isolamento entre aplicações.** Todas as consultas filtram pela aplicação autenticada. Pedir um ficheiro de outra aplicação devolve `404`, igual a um ficheiro que não existe.
- **Chaves de API.** 256 bits aleatórios; só o SHA-256 é guardado; comparação em tempo constante; a resposta é a mesma para "aplicação desconhecida" e "chave errada".
- **Autenticação antes do conteúdo.** O ficheiro só é interpretado e gravado em disco depois de as credenciais (ou a assinatura do link) serem validadas.
- **Tipo real do ficheiro.** O tipo é detectado pelo conteúdo com o Apache Tika; o `Content-Type` e a extensão enviados pelo cliente não são de confiança. Um HTML chamado `foto.png` é recusado.
- **Sem execução de conteúdo.** Só imagens, PDF, texto simples, áudio e vídeo são servidos *inline*; tudo o resto é sempre download. Todas as respostas levam `X-Content-Type-Options: nosniff`.
- **Caminhos no disco.** Construídos apenas com o código da aplicação (validado) e UUID; o nome original só existe nos metadados.
- **Links assinados.** HMAC-SHA256 com segredo do servidor, validade curta e condições (ficheiro, pasta, visibilidade, autor) incluídas na assinatura.
- **CORS por aplicação.** Só as origens registadas recebem cabeçalhos CORS, e cada pedido é verificado contra as origens da aplicação que o faz.
- **Erros.** Um erro inesperado nunca devolve detalhes internos; ficam apenas nos registos.

Limites conhecidos desta versão base, a ter em conta:

- Um link de upload pode ser usado várias vezes até expirar. Manter a validade curta (minutos).
- A quota é verificada no momento do upload; dois uploads simultâneos no limite podem ultrapassá-la ligeiramente.
- Não há análise antivírus nem limite de pedidos por minuto (o segundo pode ser feito no Nginx com `limit_req`).
- O serviço confia nos cabeçalhos `X-Forwarded-*`: em produção tem de escutar apenas em `127.0.0.1`, atrás do Nginx.

## Estrutura do projecto

```
src/main/java/ao/co/pumangol/fileservice/
├── FileServiceApplication.java
├── config/      propriedades tipadas, ligação das peças (ServiceConfig) e mapa de segurança (WebConfig)
├── apps/        aplicações clientes: registo, autenticação, API de administração
├── files/       ficheiros: regras de negócio (FileService), repositório, controllers
├── storage/     armazenamento em disco (FileStorage) e detecção de tipo (Tika)
├── security/    chaves de API, assinatura de links, interceptores, CORS
└── common/      erros (ApiException + handler), validações utilitárias
src/main/resources/
├── application.yml, application-dev.yml
└── db/migration/V1__esquema_inicial.sql
deploy/          systemd, Nginx, instalador, script da base de dados
```

Duas decisões de desenho que convém conhecer:

- **Núcleo sem framework.** `FileService`, `AppService`, `LocalFileStorage`, `SignedUrlService` e os utilitários não dependem do Spring; são instanciados em `ServiceConfig`. Isto permite testar todas as regras de negócio em milissegundos, sem base de dados nem servidor.
- **Pontos de extensão.** `FileStorage` isola o destino físico (disco hoje; NFS, S3/MinIO ou Azure Blob amanhã) e `ContentTypeDetector` isola a detecção de tipo. O esquema da base de dados evolui com novas migrações `V2__…sql`.

## Testes

```bash
mvn test       # testes unitários + teste de ponta a ponta
```

- **Unitários** (sem Spring): assinaturas, chaves, validações, armazenamento em disco e todas as regras de `FileService` e `AppService`, com repositórios em memória.
- **Ponta a ponta** (`FileServiceApiTest`): levanta a aplicação completa numa porta aleatória, com H2 em modo PostgreSQL, e exercita a API por HTTP — autenticação, isolamento, upload/download, links assinados e CORS.

## Evolução prevista

Fora do âmbito desta versão base, por ordem sugerida:

1. Documentação OpenAPI/Swagger para as equipas de frontend.
2. Links de upload de utilização única e limite de pedidos por aplicação.
3. Análise antivírus (ClamAV) antes de confirmar o upload.
4. Miniaturas e redimensionamento de imagens.
5. Tarefa de reconciliação entre disco e base de dados (ficheiros órfãos).
6. Métricas (Prometheus) e registo de acessos por ficheiro.
