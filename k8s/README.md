* Login to Azure from CLI
* Ensure you have ssh keypair in ~/.ssh/id_rsa and ~/.ssh/id_rsa.pub (ssh-keygen -t rsa)
* From /terraform run`terraform init` then `terraform apply` TODO: add ACR creation
* Connect to ACR `az aks update -n traderx-cluster -g traderx_rg --attach-acr traderx`
* Install kubectl
* Login to Azure AKS
* Run `kubectl apply -f k8s/database`
* Run `kubectl apply -f k8s/...`