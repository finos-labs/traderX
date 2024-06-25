# Generate random resource group name
resource "azurerm_resource_group" "rg" {
  location = var.resource_group_location
  name     = "traderx_rg"
}
resource "azurerm_kubernetes_cluster" "k8s" {
  location            = azurerm_resource_group.rg.location
  name                = var.cluster_name
  resource_group_name = azurerm_resource_group.rg.name
  dns_prefix          = var.dns_prefix
  tags                = {
    Environment = "Demo"
  }
  default_node_pool {
    name       = "armpool"
    vm_size    = "Standard_D2ps_v5"
    node_count = var.agent_count
  }
  linux_profile {
    admin_username = "ubuntu"
    ssh_key {
      key_data = file(var.ssh_public_key)
    }
  }
  identity {
    type = "SystemAssigned"
  }
}