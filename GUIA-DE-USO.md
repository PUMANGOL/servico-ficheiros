# Guia de utilização do serviço de ficheiros

Este guia explica como registar uma aplicação, enviar e consultar ficheiros e integrar o serviço
com um backend ou um browser. A API usa JSON para metadados e `multipart/form-data` para uploads.

## 1. Endereço e credenciais

Defina o endereço base do serviço. Em desenvolvimento, normalmente é
`http://localhost:8085`; em produção, use o endereço HTTPS configurado no Nginx.

```bash
BASE="https://portal.pumangol.co.ao/ficheiros"
```

Há dois tipos de credenciais:

- **Administração:** `X-Admin-Key`, configurada no servidor como `FILESERVICE_ADMIN_KEY`.
  Só deve ser usada por administradores e ferramentas internas.
- **Aplicação cliente:** `X-App-Id` e `X-Api-Key`. O backend da aplicação guarda estes valores
  num cofre de segredos ou configuração protegida. Nunca inclua a chave de API no frontend.

## 2. Registar uma aplicação

Crie uma aplicação com a chave de administração. `code` é o identificador que será usado em
`X-App-Id`; `allowedOrigins` contém as origens dos frontends que podem usar o serviço.

```bash
curl -i -X POST "$BASE/api/v1/admin/applications" \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "portal-exemplo",
    "name": "Portal de Exemplo",
    "allowedOrigins": ["https://portal.exemplo.co.ao"],
    "maxFileSizeBytes": 10485760,
    "allowedContentTypes": ["image/*", "application/pdf"],
    "storageQuotaBytes": 5368709120,
    "active": true
  }'
```

A resposta `201 Created` inclui `application` e `apiKey`. Guarde `apiKey` imediatamente: só é
mostrada na criação ou na rotação da chave. `allowedOrigins`, limites e quota são opcionais;
quando omitidos, aplicam-se os limites globais do serviço (por omissão, 20 MB por ficheiro).

## 3. Rotas principais

### Administração — requer `X-Admin-Key`

| Método | Rota | Utilização |
|---|---|---|
| `POST` | `/api/v1/admin/applications` | Registar aplicação; a resposta inclui a chave de API, apresentada uma única vez. |
| `GET` | `/api/v1/admin/applications` | Listar aplicações registadas. |
| `GET` | `/api/v1/admin/applications/{code}` | Consultar uma aplicação. |
| `PUT` | `/api/v1/admin/applications/{code}` | Actualizar nome, origens, limites, quota e estado. Enviar os dados da aplicação no corpo JSON. |
| `POST` | `/api/v1/admin/applications/{code}/rotate-key` | Gerar uma nova chave; a anterior deixa imediatamente de funcionar. Guardar a nova chave da resposta. |

O corpo de criação/actualização aceita `name`, `allowedOrigins`, `maxFileSizeBytes`,
`allowedContentTypes`, `storageQuotaBytes` e `active`. Na criação, incluir também `code`.
Definir `active` como `false` desactiva a aplicação.

### Ficheiros — requer `X-App-Id` e `X-Api-Key`

| Método | Rota | Utilização |
|---|---|---|
| `POST` | `/api/v1/files` | Enviar ficheiro directamente do backend (`multipart/form-data`): campo obrigatório `file`; campos opcionais `folder`, `visibility` (`PRIVATE` ou `PUBLIC`) e `uploadedBy`. |
| `GET` | `/api/v1/files` | Listar ficheiros da aplicação. Parâmetros opcionais: `folder`, `page` (começa em 0) e `size` (1–100; por omissão 20). |
| `GET` | `/api/v1/files/{id}` | Obter metadados de um ficheiro. |
| `GET` | `/api/v1/files/{id}/content` | Obter conteúdo; `?download=true` força o download. Suporta pedidos `Range` e `If-None-Match`. |
| `DELETE` | `/api/v1/files/{id}` | Eliminar o conteúdo do ficheiro. Devolve `204 No Content`; a operação não deve ser tratada como reversível. |
| `POST` | `/api/v1/files/{id}/signed-url` | Criar link temporário para ler um ficheiro privado. Corpo opcional: `{"expiresInSeconds":300}`. |
| `POST` | `/api/v1/upload-urls` | Criar link temporário para upload pelo browser. Corpo opcional: `{"folder":"imagens","visibility":"PRIVATE","uploadedBy":"utilizador-42","expiresInSeconds":300}`. |

Cada aplicação só pode consultar e alterar os seus próprios ficheiros. Um ficheiro inexistente
ou pertencente a outra aplicação responde `404`.

### Acesso pelo browser — sem chave de API

| Método | Rota | Utilização |
|---|---|---|
| `GET` | `/api/v1/public/files/{id}` | Ler ficheiro `PUBLIC` sem assinatura ou ficheiro `PRIVATE` com os parâmetros assinados `exp` e `sig`. `download=true` força o download. |
| `POST` | `/api/v1/public/uploads?...` | Enviar ficheiro usando o URL assinado devolvido por `/api/v1/upload-urls`. Enviar `multipart/form-data`, campo `file`. |
| `GET` | `/actuator/health` | Verificar o estado do serviço; resposta saudável: `{"status":"UP"}`. Em produção, este endpoint destina-se a verificações locais e não é exposto pelo Nginx. |

Links assinados são válidos até `expiresAt` e não são de utilização única. O prazo por omissão é
5 minutos e o máximo configurado é 24 horas. Use prazos curtos e não altere os parâmetros do URL.

## 4. Exemplos de utilização

### Upload pelo backend

O backend autentica o pedido e envia o ficheiro como parte multipart:

```bash
curl -i -X POST "$BASE/api/v1/files" \
  -H "X-App-Id: portal-exemplo" \
  -H "X-Api-Key: $API_KEY" \
  -F "file=@relatorio.pdf" \
  -F "folder=relatorios/2026" \
  -F "visibility=PRIVATE" \
  -F "uploadedBy=utilizador-42"
```

Uma resposta `201 Created` contém metadados, incluindo o `id` UUID, `filename`, `contentType`,
`sizeBytes`, `checksumSha256`, `visibility`, `folder` e `createdAt`. Para ficheiros privados,
`publicUrl` é `null`.

### Listar e descarregar pelo backend

```bash
curl -s "$BASE/api/v1/files?folder=relatorios%2F2026&page=0&size=20" \
  -H "X-App-Id: portal-exemplo" \
  -H "X-Api-Key: $API_KEY"

curl -f "$BASE/api/v1/files/$FILE_ID/content?download=true" \
  -H "X-App-Id: portal-exemplo" \
  -H "X-Api-Key: $API_KEY" \
  -o relatorio.pdf
```

### Upload a partir do browser

O backend pede o URL assinado com as credenciais da aplicação e devolve apenas esse URL ao
frontend. O browser envia o ficheiro directamente para o serviço, sem receber a chave de API.

```javascript
// Backend da aplicação
const resposta = await fetch(`${FILES_URL}/api/v1/upload-urls`, {
  method: "POST",
  headers: {
    "X-App-Id": APP_ID,
    "X-Api-Key": API_KEY,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    folder: "avatars",
    visibility: "PRIVATE",
    uploadedBy: utilizador.id,
    expiresInSeconds: 300,
  }),
});
if (!resposta.ok) throw new Error("Não foi possível criar o link de upload");
const { url } = await resposta.json();
```

```javascript
// Frontend do browser
const form = new FormData();
form.append("file", ficheiroSelecionado);
const resposta = await fetch(url, { method: "POST", body: form });
if (!resposta.ok) {
  const problema = await resposta.json();
  throw new Error(problema.detail);
}
const ficheiro = await resposta.json(); // Guardar o id na aplicação cliente
```

### Leitura de ficheiros privados e públicos

Para ler um ficheiro privado no browser, o backend pede um URL assinado:

```bash
curl -s -X POST "$BASE/api/v1/files/$FILE_ID/signed-url" \
  -H "X-App-Id: portal-exemplo" \
  -H "X-Api-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"expiresInSeconds":300}'
```

Entregue o `url` devolvido ao browser e use-o, por exemplo, como `src` de uma imagem ou `href` de
uma ligação. Para forçar download, acrescente `&download=true` ao URL. Para um ficheiro enviado
com `visibility=PUBLIC`, use directamente o `publicUrl` dos metadados; não precisa de assinatura.

## 5. Erros e cuidados

As respostas de erro usam `application/problem+json`, normalmente com `status`, `detail` e `code`:

```json
{
  "status": 403,
  "detail": "A origem não está autorizada.",
  "code": "ORIGIN_NOT_ALLOWED"
}
```

| HTTP | Códigos frequentes | O que verificar |
|---|---|---|
| `400` | `INVALID_VISIBILITY`, `INVALID_PAGINATION`, `INVALID_EXPIRATION`, `INVALID_MULTIPART` | Campos, parâmetros, prazo e formato multipart. |
| `401` | `UNAUTHORIZED` | Credenciais de administração ou aplicação em falta/incorretas. |
| `403` | `APP_DISABLED`, `ORIGIN_NOT_ALLOWED`, `INVALID_SIGNATURE`, `ADMIN_DISABLED` | Estado da aplicação, origem CORS, assinatura/prazo ou configuração do servidor. |
| `404` | `NOT_FOUND` | ID inexistente, eliminado ou pertencente a outra aplicação. |
| `413` | `FILE_TOO_LARGE`, `QUOTA_EXCEEDED` | Limite de ficheiro ou quota da aplicação. |
| `415` | `CONTENT_TYPE_NOT_ALLOWED` | Tipo detectado no conteúdo não permitido pela aplicação. |

- Registe a origem exacta do frontend em `allowedOrigins`, incluindo esquema e porta quando
  aplicável. Um pedido de browser de outra origem é recusado.
- A visibilidade por omissão é `PRIVATE`. Use `PUBLIC` apenas se o conteúdo puder ser acedido por
  qualquer pessoa que conheça o URL.
- O tipo de conteúdo é detectado a partir do ficheiro; mudar a extensão não permite contornar
  os tipos autorizados.
- Em produção, use HTTPS e mantenha as chaves e URLs assinados fora de registos, URLs públicos
  permanentes e código cliente. Siga [README.md](./README.md) e os ficheiros em `deploy/` para
  instalação, configuração e cópias de segurança.
