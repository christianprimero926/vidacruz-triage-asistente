@description('Región de los recursos')
param location string = resourceGroup().location

@description('Nombre del registro de contenedores (5-50 caracteres alfanuméricos, único en Azure)')
@minLength(5)
@maxLength(50)
param acrName string

@description('Nombre del cluster AKS')
param aksName string = 'vidacruz-aks'

@description('Tamaño de la VM de los nodos')
param nodeVmSize string = 'Standard_B2ms'

@description('Número de nodos. Los complementos de AKS reservan casi todo un nodo de 2 vCPU, así que con nodos de ese tamaño hacen falta 2')
@minValue(1)
param nodeCount int = 2

// Rol integrado AcrPull
var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

resource aks 'Microsoft.ContainerService/managedClusters@2024-09-01' = {
  name: aksName
  location: location
  sku: {
    name: 'Base'
    tier: 'Free'
  }
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    dnsPrefix: aksName
    agentPoolProfiles: [
      {
        name: 'system'
        mode: 'System'
        count: nodeCount
        vmSize: nodeVmSize
        osType: 'Linux'
        type: 'VirtualMachineScaleSets'
      }
    ]
    // Complemento de enrutamiento de aplicaciones (ingress nginx administrado)
    ingressProfile: {
      webAppRouting: {
        enabled: true
      }
    }
  }
}

// El kubelet del cluster baja las imágenes del ACR con su identidad administrada
resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, aks.id, acrPullRoleId)
  scope: acr
  properties: {
    principalId: aks.properties.identityProfile.kubeletidentity.objectId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
  }
}

output acrLoginServer string = acr.properties.loginServer
output aksNombre string = aks.name
