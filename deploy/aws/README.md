# AWS + EKS Deployment Starter

This repository can run on Amazon EKS with:

- Amazon ECR for the application image
- Amazon EKS for Kubernetes orchestration
- Amazon RDS for PostgreSQL
- Amazon MSK for Kafka
- AWS Secrets Manager plus External Secrets Operator for application secrets
- AWS Load Balancer Controller for ingress

## What is included

- A Helm chart at `deploy/helm/fraud-detection-engine`
- An EKS-oriented values file at `deploy/helm/fraud-detection-engine/values-eks.yaml`
- Support for:
  - IRSA via `serviceAccount.annotations`
  - External Secrets via `templates/externalsecret.yaml`
  - health probes, HPA, and PDB

## Prerequisites

- An EKS cluster
- `kubectl` configured for the cluster
- `helm` installed locally or in CI
- AWS Load Balancer Controller installed if you want ingress
- External Secrets Operator installed if you want secrets pulled from Secrets Manager
- Reachable PostgreSQL and Kafka endpoints

## 1. Build and push the image to ECR

```bash
aws ecr create-repository --repository-name fraud-detection-engine

aws ecr get-login-password --region <region> \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

docker build -t fraud-detection-engine:latest .
docker tag fraud-detection-engine:latest <account-id>.dkr.ecr.<region>.amazonaws.com/fraud-detection-engine:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/fraud-detection-engine:latest
```

## 2. Store application secrets in AWS Secrets Manager

Create one JSON secret with these keys:

```json
{
  "dbUser": "fraud_user",
  "dbPassword": "change-me",
  "jwtSecret": "replace-with-32-plus-char-secret",
  "jwtIssuer": "fraud-detection-engine",
  "jwtAudience": "fraud-api"
}
```

Suggested secret name:

```text
fraud-detection-engine/application
```

## 3. Configure External Secrets

Create a `ClusterSecretStore` or `SecretStore` that can read from Secrets Manager using IRSA.

The Helm chart already supports an `ExternalSecret` resource. Set:

- `externalSecret.enabled=true`
- `externalSecret.secretStoreRef.name`
- `externalSecret.data.*.key`

The generated Kubernetes secret will be used by the deployment as:

- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_ISSUER`
- `JWT_AUDIENCE`

## 4. Set runtime endpoints

Update the EKS values file with:

- `image.repository`
- `database.host`
- `database.name`
- `kafka.bootstrapServers`
- `ingress.hosts`
- `serviceAccount.annotations.eks.amazonaws.com/role-arn`

For production you would typically point these at:

- RDS PostgreSQL endpoint for `database.host`
- MSK bootstrap brokers for `kafka.bootstrapServers`

## 5. Deploy with Helm

```bash
helm upgrade --install fraud-detection-engine \
  deploy/helm/fraud-detection-engine \
  --namespace fraud \
  --create-namespace \
  -f deploy/helm/fraud-detection-engine/values-eks.yaml
```

If you are not using External Secrets yet, you can instead create a Kubernetes secret manually and set:

- `secrets.create=false`
- `secrets.existingSecretName=<your-secret-name>`

## 6. Deploy from GitHub Actions

This repository also includes a manual workflow at `.github/workflows/deploy-eks.yml`.

Before using it, configure:

- GitHub environment secrets with `AWS_DEPLOY_ROLE_ARN`
- An AWS IAM role trusted by GitHub OIDC
- Access for that role to ECR, EKS, and any required cluster auth mappings

Then run the workflow manually and provide:

- `aws_region`
- `ecr_repository`
- `eks_cluster_name`
- `kubernetes_namespace`
- `helm_values_file`

The workflow will:

- build the Docker image
- push it to ECR
- update kubeconfig for the selected EKS cluster
- deploy the Helm chart with the new image tag

## 7. Verify rollout

```bash
kubectl rollout status deployment/fraud-detection-engine -n fraud
kubectl get pods -n fraud
kubectl get ingress -n fraud
kubectl port-forward svc/fraud-detection-engine 8080:8080 -n fraud
curl http://127.0.0.1:8080/actuator/health
```

## Operational notes

- The chart enables Spring Boot health probes through environment variables.
- The app is stateless, so horizontal scaling is straightforward once PostgreSQL and Kafka are externalized.
- Flyway runs on startup, so coordinate rollouts carefully if you expect destructive schema changes.
- If you expose `/actuator/metrics` to Prometheus later, review SecurityConfig to decide whether to keep it private or allow only cluster-local scraping.
