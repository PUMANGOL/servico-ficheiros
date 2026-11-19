#!/usr/bin/env bash
#
# Instala ou actualiza o serviço de ficheiros num servidor Ubuntu.
#
#   sudo ./instalar.sh [caminho/para/file-service.jar]
#
# Pode ser executado várias vezes: na primeira instala tudo; nas seguintes actualiza o JAR
# e a unidade systemd, sem tocar em /etc/fileservice/fileservice.env nem nos ficheiros guardados.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${1:-$SCRIPT_DIR/../target/file-service.jar}"

SERVICE_USER=fileservice
APP_DIR=/opt/fileservice
DATA_DIR=/var/lib/fileservice
CONF_DIR=/etc/fileservice
ENV_FILE="$CONF_DIR/fileservice.env"

if [[ $EUID -ne 0 ]]; then
    echo "Executar como root: sudo $0 $*" >&2
    exit 1
fi
if [[ ! -f "$JAR" ]]; then
    echo "JAR não encontrado: $JAR (correr primeiro 'mvn clean package')." >&2
    exit 1
fi
if ! command -v java >/dev/null 2>&1; then
    echo "Java não encontrado. Instalar com: apt install openjdk-21-jre-headless" >&2
    exit 1
fi

# Utilizador de sistema, sem login, dono apenas da pasta de dados.
if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
    useradd --system --home-dir "$APP_DIR" --no-create-home --shell /usr/sbin/nologin "$SERVICE_USER"
    echo ">> Utilizador de sistema '$SERVICE_USER' criado."
fi

install -d -o root -g root -m 755 "$APP_DIR"
install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 750 "$DATA_DIR" "$DATA_DIR/storage"
install -d -o root -g "$SERVICE_USER" -m 750 "$CONF_DIR"

install -o root -g root -m 644 "$JAR" "$APP_DIR/file-service.jar"
echo ">> JAR instalado em $APP_DIR/file-service.jar"

FIRST_INSTALL=false
if [[ ! -f "$ENV_FILE" ]]; then
    install -o root -g "$SERVICE_USER" -m 640 "$SCRIPT_DIR/fileservice.env.example" "$ENV_FILE"
    FIRST_INSTALL=true
    echo ">> Criado $ENV_FILE a partir do exemplo."
fi

install -o root -g root -m 644 "$SCRIPT_DIR/fileservice.service" /etc/systemd/system/fileservice.service
systemctl daemon-reload
systemctl enable fileservice >/dev/null

if [[ "$FIRST_INSTALL" == true ]]; then
    cat <<EOF

Instalação concluída. Antes de arrancar:
  1. Criar a base de dados:   sudo -u postgres psql -v ON_ERROR_STOP=1 -f $SCRIPT_DIR/criar-base-de-dados.sql
  2. Preencher os segredos:   sudoedit $ENV_FILE
  3. Arrancar:                sudo systemctl start fileservice
  4. Verificar:               curl -s http://127.0.0.1:8085/actuator/health
EOF
else
    systemctl restart fileservice
    echo ">> Serviço reiniciado com a nova versão. Registos: journalctl -u fileservice -f"
fi
