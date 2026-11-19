# Testar o Serviço de Ficheiros com Postman

Este roteiro cobre o fluxo local de ponta a ponta: verificar o serviço, registar uma aplicação,
enviar/listar/ler um ficheiro privado, testar links assinados e enviar/ler um ficheiro público.

## 1. Iniciar o serviço local

Requisitos: JDK 21, Maven e PostgreSQL em execução. O perfil `dev` liga à base de dados
`fileservice` em `localhost:5432` com o utilizador e palavra-passe `fileservice`. Se ainda não
existir, crie a base e o utilizador (pode usar `deploy/criar-base-de-dados.sql` ou configurar os
valores em `src/main/resources/application-dev.yml`).

Na raiz do projecto, inicie a aplicação:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Mantenha esse terminal aberto. A primeira inicialização pode demorar enquanto o Spring Boot
inicia e aplica a migração da base de dados. Confirme no Postman que
`GET http://localhost:8085/actuator/health` devolve `200` e `{"status":"UP"}` antes de avançar.
Se a aplicação não arrancar, verifique a mensagem do terminal e se o PostgreSQL aceita ligação
em `localhost:5432`.

> O perfil `dev` usa uma chave de administração e segredos fixos destinados exclusivamente a
> testes locais. Nunca os reutilize em produção.

## 2. Importar a coleção

1. Abra o Postman e escolha **Import**.
2. Seleccione o ficheiro [servico-ficheiros.postman_collection.json](./postman/servico-ficheiros.postman_collection.json).
3. Na coleção importada, abra **Variables** e confirme:
   - `baseUrl`: `http://localhost:8085`
   - `adminKey`: `dev-admin-key-apenas-para-desenvolvimento`
4. Guarde as alterações.

O exemplo assume que o serviço está na porta local `8085`. Se usar outra porta, actualize
`baseUrl`. A chave acima só funciona quando a aplicação é iniciada com o perfil `dev`.

## 3. Executar na ordem

Execute os pedidos pela ordem apresentada. Pode enviar cada pedido individualmente ou usar
**Run collection**; os testes de resposta guardam automaticamente os identificadores e a chave
de API nas variáveis da coleção.

1. **Health check** — confirma que a aplicação responde.
2. **Registar aplicação de teste** — cria um código único e guarda `appCode` e `apiKey`.
3. **Consultar aplicação criada** — confirma que a autenticação de administração funciona.
4. **Upload privado** — na linha `file`, seleccione um ficheiro permitido (por exemplo, PNG, PDF
   ou TXT). Os outros campos já vêm preenchidos.
5. **Listar ficheiros** e **Consultar metadados** — verificam a listagem e o `fileId` guardado.
6. **Descarregar conteúdo privado** — confirma o conteúdo; use **Save Response** no Postman para
   guardar o ficheiro recebido.
7. **Criar link de leitura assinado** e **Ler ficheiro pelo link assinado** — execute-os em
   sequência; o link expira após 5 minutos.
8. **Criar link de upload público** — emite um URL assinado sem expor a chave ao browser.
9. **Enviar ficheiro com link assinado** — seleccione um ficheiro permitido na linha `file`.
10. **Ler ficheiro público sem credenciais** — confirma que o `publicUrl` do upload público pode
    ser acedido sem os cabeçalhos de API.

Os pedidos de upload não devem ter um cabeçalho `Content-Type` definido manualmente: o Postman
gera o `multipart/form-data` com o `boundary` correcto. A coleção inclui testes que esperam
`201` nos uploads, `200` nas consultas e `204` na eliminação.

## 4. Variáveis da coleção

| Variável | Como é preenchida | Finalidade |
|---|---|---|
| `baseUrl` | Manualmente | Endereço base do serviço. |
| `adminKey` | Manualmente | Chave de administração local do perfil `dev`. |
| `appCode` | Pedido de registo | Identificador único da aplicação de teste. |
| `apiKey` | Resposta do registo | Chave da aplicação para os pedidos autenticados. |
| `fileId` | Resposta do upload privado | ID usado nas consultas e no download privado. |
| `signedDownloadUrl` | Resposta do pedido de link de leitura | URL temporário para ler o ficheiro privado. |
| `signedUploadUrl` | Resposta do pedido de link de upload | URL temporário para o upload directo. |
| `publicFileId`, `publicUrl` | Resposta do upload assinado | ID e URL estável do ficheiro público. |

Ao executar novamente **Registar aplicação de teste**, a coleção cria outro código único e
actualiza a chave correspondente. Os ficheiros e aplicações de testes permanecem na base de
dados até serem removidos pela administração; **Eliminar ficheiro privado de teste** remove
apenas o ficheiro privado criado no fluxo actual. O upload público também permanece guardado.

## 5. Rotas abrangidas

| Método | Rota | Credencial usada |
|---|---|---|
| `GET` | `/actuator/health` | Nenhuma |
| `POST` | `/api/v1/admin/applications` | `X-Admin-Key` |
| `GET` | `/api/v1/admin/applications/{code}` | `X-Admin-Key` |
| `POST` | `/api/v1/files` | `X-App-Id` + `X-Api-Key` |
| `GET` | `/api/v1/files` | `X-App-Id` + `X-Api-Key` |
| `GET` | `/api/v1/files/{id}` | `X-App-Id` + `X-Api-Key` |
| `GET` | `/api/v1/files/{id}/content` | `X-App-Id` + `X-Api-Key` |
| `POST` | `/api/v1/files/{id}/signed-url` | `X-App-Id` + `X-Api-Key` |
| `POST` | `/api/v1/upload-urls` | `X-App-Id` + `X-Api-Key` |
| `POST` | `/api/v1/public/uploads` | URL assinado; sem chave de API |
| `GET` | `/api/v1/public/files/{id}` | Ficheiro público ou URL assinado |
| `DELETE` | `/api/v1/files/{id}` | `X-App-Id` + `X-Api-Key`; limpeza opcional |

Para a referência completa da API e resolução de erros, consulte o [guia de utilização](./GUIA-DE-USO.md).
