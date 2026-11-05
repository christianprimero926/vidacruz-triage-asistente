#!/usr/bin/env bash
# Borra el grupo de recursos completo (ACR, AKS y todo lo desplegado).
set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:-rg-vidacruz}"

read -r -p "Se borrará el grupo '$RESOURCE_GROUP' con todo su contenido. Escribe el nombre para confirmar: " respuesta
if [ "$respuesta" != "$RESOURCE_GROUP" ]; then
  echo "Cancelado."
  exit 1
fi

az group delete --name "$RESOURCE_GROUP" --yes --no-wait
echo "Borrado en curso (az group wait --name $RESOURCE_GROUP --deleted)."
