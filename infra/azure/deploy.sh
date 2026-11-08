#!/usr/bin/env bash
# Despliega VidaCruz en Azure: infraestructura, imágenes y manifiestos.
# Requiere az (con sesión iniciada) y kubectl. Variables obligatorias de entorno:
#   DB_PASSWORD, TRIAGE_API_KEY, ASISTENTE_API_KEY
# Opcionales: LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, WEBHOOK_ALERTAS_URL
#   CONSTRUCCION=acr (construye en Azure) o local (docker build y push; requiere Docker).
#   Por defecto acr, y si la suscripción no permite ACR Tasks (prueba gratuita) pasa a local.
#   NODE_VM_SIZE: tamaño del nodo de AKS (por defecto el de main.bicep). La prueba gratuita no permite la serie B;
#   ahí sirve Standard_D2as_v4. NODE_COUNT: número de nodos (por defecto 2).
set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:-rg-vidacruz}"
LOCATION="${LOCATION:-eastus}"
# Nombre estable entre ejecuciones y único por suscripción
ACR_NAME="${ACR_NAME:-vidacruz$(az account show --query id --output tsv | tr -d '\r-' | cut -c1-8)}"
AKS_NAME="${AKS_NAME:-vidacruz-aks}"
NAMESPACE="${NAMESPACE:-vidacruz}"
SECUENCIA="${SECUENCIA:-01}"
TAG="${TAG:-$(date +%Y%m%d)${SECUENCIA}}"

: "${DB_PASSWORD:?Falta DB_PASSWORD}"
: "${TRIAGE_API_KEY:?Falta TRIAGE_API_KEY}"
: "${ASISTENTE_API_KEY:?Falta ASISTENTE_API_KEY}"
LLM_API_KEY="${LLM_API_KEY:-}"
LLM_BASE_URL="${LLM_BASE_URL:-}"
LLM_MODEL="${LLM_MODEL:-}"
WEBHOOK_ALERTAS_URL="${WEBHOOK_ALERTAS_URL:-http://webhook-echo:8080/alertas}"
CONSTRUCCION="${CONSTRUCCION:-acr}"

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

construir_local() {
  local servicio="$1"
  echo "Construyendo $servicio:$TAG con Docker y subiéndolo al ACR"
  docker build --tag "$ACR_SERVER/$servicio:$TAG" "$RAIZ/$servicio"
  docker push "$ACR_SERVER/$servicio:$TAG"
}

construir() {
  local servicio="$1" salida
  if [ "$CONSTRUCCION" = "local" ]; then
    construir_local "$servicio"
    return
  fi
  echo "Construyendo $servicio:$TAG en el ACR"
  if ! salida="$(az acr build --registry "$ACR_NAME" --image "$servicio:$TAG" "$RAIZ/$servicio" 2>&1)"; then
    case "$salida" in
      *TasksOperationsNotAllowed*)
        echo "La suscripción no permite construir en el ACR; se construye en local"
        CONSTRUCCION=local
        az acr login --name "$ACR_NAME"
        construir_local "$servicio" ;;
      *) echo "$salida" >&2; exit 1 ;;
    esac
  fi
}

# Una suscripción nueva no tiene registrados los proveedores; registrarlos no tiene costo
for proveedor in Microsoft.ContainerService Microsoft.ContainerRegistry Microsoft.Compute Microsoft.Network; do
  az provider register --namespace "$proveedor" --wait --output none
done

echo "Grupo de recursos $RESOURCE_GROUP en $LOCATION"
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none

echo "Desplegando ACR y AKS"
az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --template-file "$RAIZ/infra/azure/main.bicep" \
  --parameters acrName="$ACR_NAME" aksName="$AKS_NAME" ${NODE_VM_SIZE:+nodeVmSize="$NODE_VM_SIZE"} \
               ${NODE_COUNT:+nodeCount="$NODE_COUNT"} \
  --output none

# En Windows, az puede terminar la salida con \r
ACR_SERVER="$(az acr show --name "$ACR_NAME" --query loginServer --output tsv | tr -d '\r')"

[ "$CONSTRUCCION" = "local" ] && az acr login --name "$ACR_NAME"
for servicio in triage-service asistente-service; do
  construir "$servicio"
done

az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$AKS_NAME" --overwrite-existing

# El namespace debe existir antes del secret; el resto lo aplica kustomize
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic vidacruz-secrets \
  --namespace "$NAMESPACE" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=TRIAGE_API_KEY="$TRIAGE_API_KEY" \
  --from-literal=ASISTENTE_API_KEY="$ASISTENTE_API_KEY" \
  --from-literal=LLM_API_KEY="$LLM_API_KEY" \
  --from-literal=WEBHOOK_ALERTAS_URL="$WEBHOOK_ALERTAS_URL" \
  --dry-run=client -o yaml | kubectl apply -f -

# Se trabaja sobre una copia para no modificar los manifiestos versionados
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp -r "$RAIZ/k8s/." "$TMP/"
sed -i "s|REGISTRO_ACR|$ACR_SERVER|g; s|ETIQUETA|$TAG|g" "$TMP/kustomization.yaml"
[ -z "$LLM_BASE_URL" ] || sed -i "s|^\( *LLM_BASE_URL:\).*|\1 $LLM_BASE_URL|" "$TMP/configmap.yaml"
[ -z "$LLM_MODEL" ] || sed -i "s|^\( *LLM_MODEL:\).*|\1 $LLM_MODEL|" "$TMP/configmap.yaml"
kubectl apply -k "$TMP"

for recurso in statefulset/postgres statefulset/kafka statefulset/redis \
               deployment/webhook-echo deployment/triage-service deployment/asistente-service; do
  kubectl rollout status "$recurso" --namespace "$NAMESPACE" --timeout=600s
done

echo "Esperando la IP pública del ingress"
IP=""
for _ in $(seq 1 60); do
  IP="$(kubectl get ingress vidacruz --namespace "$NAMESPACE" \
        -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null | tr -d '\r' || true)"
  [ -n "$IP" ] && break
  sleep 10
done

if [ -z "$IP" ]; then
  echo "El ingress aún no tiene IP: revisa 'kubectl get ingress -n $NAMESPACE'" >&2
  exit 1
fi

echo "Triage:    http://$IP/triage/swagger-ui.html"
echo "Asistente: http://$IP/asistente/swagger-ui.html"
echo "Imágenes:  $ACR_SERVER/*:$TAG"
