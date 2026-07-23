# Kubernetes deployment

`platform.yaml` deploys the three stateless application workloads. PostgreSQL, Kafka, Redis, Qdrant, Prometheus, and the OpenTelemetry backend are expected to be managed services in production.

1. Build and push `reengage-frontend`, `reengage-core-api`, and `reengage-ai-service` images from the included Dockerfiles.
2. Replace every `ghcr.io/your-org/...` image name.
3. Replace ConfigMap endpoints with private service endpoints.
4. Create `reengage-secrets` through External Secrets/your cloud secret manager; do not apply the example secret to production.
5. Configure an ingress controller, certificate manager, DNS, and provider credentials.
6. Apply and watch the rollout:

```bash
kubectl apply -f infra/k8s/platform.yaml
kubectl -n reengage rollout status deployment/core-api
kubectl -n reengage get pods,hpa,pdb
```

The Spring replicas run Flyway during startup. Flyway serializes migrations, but high-risk migrations should still be executed by a dedicated pre-deploy job and remain backward-compatible during a rolling update.
