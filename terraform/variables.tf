variable "agent_count" {
  default = 3
}
variable "cluster_name" {
  default = "traderx-cluster"
}
variable "dns_prefix" {
  default = "arm-aks"
}
variable "resource_group_location" {
  default     = "uksouth"
  description = "Location of the resource group."
}
variable "resource_group_name_prefix" {
  default     = "arm-aks-rg"
  description = "Prefix of the resource group name that's combined with a random ID so name is unique in your Azure subscription."
}
variable "ssh_public_key" {
  default = "~/.ssh/id_rsa.pub"
}