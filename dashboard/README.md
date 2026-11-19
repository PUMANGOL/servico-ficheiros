# Painel de gestão do Serviço de Ficheiros

Aplicação Next.js para administrar as aplicações cliente, acompanhar métricas e verificar a saúde do Serviço de Ficheiros.

## Funcionalidades

- Dashboard responsivo com estado do serviço, tempo activo, aplicações, ficheiros e armazenamento.
- Gráfico de uploads, consultas e eliminações, mais feed dos eventos recentes.
- Listar, pesquisar e filtrar ficheiros por aplicação/visibilidade; visualizar e descarregar ficheiros.
- Criar e editar aplicações, origens CORS, limites, quotas e estado.
- Rodar chaves de API e apresentá-las apenas uma vez, com opção para copiar.
- Página de diagnóstico com variáveis necessárias e exemplo de proxy Nginx.
- Sessão de administrador cifrada no servidor Next.js em cookie `HttpOnly`, `SameSite=Strict`, com validade de 8 horas.

Os eventos são registos de actividade da aplicação no PostgreSQL; não são logs do sistema operativo, do Nginx ou do systemd. O painel não altera ficheiros `.env`, serviços systemd, PostgreSQL nem configurações Nginx.

## Desenvolvimento local

Requisitos: Node.js 20.9 ou superior e o Serviço de Ficheiros iniciado.

1. Copie `.env.example` para `.env.local`.
2. Gere um segredo de sessão local e substitua o valor de exemplo:

   ```powershell
   $bytes = New-Object byte[] 48
   [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
   [Convert]::ToBase64String($bytes)
   ```

3. Para testar com o serviço local, altere `FILESERVICE_API_URL=http://127.0.0.1:8085`. Sem essa alteração, o painel usa a API de produção em `https://portal.pumangol.co.ao/ficheiros`.
4. Instale e arranque:

   ```powershell
   npm.cmd install
   npm.cmd run dev
   ```

5. Abra <http://localhost:3000/gestao-ficheiros> e entre com a chave `FILESERVICE_ADMIN_KEY` do serviço.

Não introduza a chave administrativa em variáveis `NEXT_PUBLIC_*`, ficheiros versionados ou código do browser. O Next.js valida a chave no servidor e guarda-a apenas cifrada no cookie de sessão.

## Publicação no servidor do portal

O painel é um processo Node.js separado, recomendado em `127.0.0.1:3000`. Configure no ambiente do processo:

```dotenv
FILESERVICE_API_URL=https://portal.pumangol.co.ao/ficheiros
FILESERVICE_PUBLIC_URL=https://portal.pumangol.co.ao/ficheiros
DASHBOARD_SESSION_SECRET=<segredo-aleatorio-com-pelo-menos-32-caracteres>
NODE_ENV=production
PORT=3000
```

O painel envia os pedidos administrativos para `https://portal.pumangol.co.ao/ficheiros/api/v1/admin/...` no servidor Next.js; a chave de administração nunca é enviada ao browser. Se Next.js e Java estiverem no mesmo servidor, pode optar pelo endereço interno `http://127.0.0.1:18085` para evitar tráfego pelo proxy público. Gere `DASHBOARD_SESSION_SECRET` no próprio servidor com `openssl rand -base64 48`. Não reutilize a chave administrativa nem o segredo HMAC do serviço.

Instale dependências e crie a versão de produção a partir desta pasta:

```bash
npm ci
npm run build
npm start
```

No bloco HTTPS de `portal.pumangol.co.ao`, pode adicionar o proxy abaixo para servir o painel em `/gestao-ficheiros/`. Mantenha o snippet dentro do `server` HTTPS correcto e valide antes de recarregar Nginx:

```nginx
location /gestao-ficheiros/ {
    proxy_pass http://127.0.0.1:3000/gestao-ficheiros/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

Como o painel administra credenciais e configurações de acesso, limite a publicação a HTTPS e restrinja o acesso por VPN, allowlist de IP ou autenticação adicional do portal, conforme as políticas da organização. O endpoint Spring `X-Admin-Key` e as métricas não devem ser expostos sem protecção.

## API administrativa adicionada

Os endpoints abaixo herdam a protecção `X-Admin-Key` já aplicada a `/api/v1/admin/**`:

| Método | Endpoint | Conteúdo |
|---|---|---|
| `GET` | `/api/v1/admin/dashboard/health` | Estado geral do Actuator, instante de verificação e uptime |
| `GET` | `/api/v1/admin/dashboard/metrics` | Aplicações, ficheiros activos, espaço lógico e visibilidade |
| `GET` | `/api/v1/admin/dashboard/activity?limit=50` | Eventos recentes; `limit` entre 1 e 200 |
| `GET` | `/api/v1/admin/dashboard/daily?days=14` | Actividade agregada por dia; `days` entre 1 e 90 |
| `GET` | `/api/v1/admin/dashboard/files` | Lista paginada de ficheiros activos; filtros `q`, `applicationCode`, `visibility`, `page`, `size` |
| `GET` | `/api/v1/admin/dashboard/files/{id}/content` | Visualiza ou descarrega conteúdo (`?download=true`) |

Uma migração Flyway cria `fs_audit_event`; os registos são retidos durante 90 dias. A manutenção automática remove eventos expirados diariamente.

O painel valida a sessão através de `/api/v1/admin/applications`, que também permite iniciar sessão em versões anteriores do serviço. As métricas, monitorização e visualização administrativa de ficheiros requerem a versão Java que inclui as rotas acima. Se a API devolver `404` para essas rotas, actualize o JAR no servidor com `mvn clean package` e `sudo ./deploy/instalar.sh`; a migração Flyway é aplicada no arranque.
