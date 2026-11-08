# ADR 0007: Despliegue en Azure con AKS

Estado: aceptado

## Contexto

El enunciado pide manifiestos de Kubernetes y despliegue en Azure, con costo mínimo por tratarse de una prueba. El sistema necesita PostgreSQL, Kafka y Redis además de los dos servicios.

## Decisión

- **AKS con dos nodos de 2 vCPU** (`Standard_B2ms` por defecto, parámetros `nodeVmSize` y `nodeCount`) y **ACR** para las imágenes, definidos en `infra/azure/main.bicep`. El ACR se enlaza al clúster con el rol AcrPull. Con un solo nodo de 2 vCPU no alcanza: los complementos de AKS (ingress, drivers de discos, DNS, métricas) reservan casi toda su CPU y los pods quedan en `Pending`.
- **Manifiestos** en `k8s/` con kustomize: namespace, configmap, secret (lo crea el script, no está versionado), deployments de los servicios, StatefulSets de PostgreSQL, Kafka (KRaft, un solo broker) y Redis con volumen, un job que crea los topics y el receptor de webhooks de demo.
- **Ingress** con el complemento de enrutamiento de aplicaciones de AKS (nginx administrado): `/triage` y `/asistente` hacia cada servicio. Swagger se publica bajo esos prefijos con variables en los Deployments; compose lo mantiene en la raíz.
- **Scripts:** `infra/azure/deploy.sh` crea el grupo de recursos, ACR y AKS, construye las imágenes con `az acr build` (o con Docker en local y `docker push` si la suscripción no permite ACR Tasks, como la prueba gratuita), crea el secret, toma del entorno la URL y el modelo del LLM si se indican, aplica los manifiestos, espera los rollouts e imprime las URLs. `destroy.sh` borra el grupo de recursos completo, con confirmación.
- **Datos en el clúster por costo.** PostgreSQL, Kafka y Redis administrados multiplican el costo mensual de una demo.

## Alternativas consideradas

- Azure Container Apps: más barato y simple, pero el enunciado pide manifiestos de Kubernetes.
- Servicios de datos administrados desde ya: es lo correcto en producción; aquí se descartó por costo.
- Varios nodos y réplicas: el sistema los soporta (UPDATE condicional, advisory lock, `SKIP LOCKED`), pero no se justifica en una demo.

## Consecuencias

- La prueba gratuita no admite la serie B; ahí se usa `Standard_D2as_v4` (`NODE_VM_SIZE`). Dos nodos de 2 vCPU caben en la cuota de 4 vCPU de esa suscripción.
- Un pod por StatefulSet: sin alta disponibilidad ni tolerancia a la caída del nodo, y los datos viven en discos del clúster sin backups configurados. No es un entorno productivo.
- El ingress expone HTTP, sin TLS. Para producción hace falta certificado (cert-manager con Let's Encrypt, o certificado en Key Vault) y dominio propio.
- Los secretos pasan por variables de entorno al script y quedan en un secret de Kubernetes. En producción irían en Key Vault con el CSI driver.
- Equivalentes administrados para producción: Azure Database for PostgreSQL Flexible Server con alta disponibilidad, Event Hubs con API Kafka (o Kafka administrado con replicación 3 y `min.insync.replicas=2`) y Azure Cache for Redis.
