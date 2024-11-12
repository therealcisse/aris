helm repo add jaegertracing https://jaegertracing.github.io/helm-charts
helm install jaeger jaegertracing/jaeger --namespace observability --create-namespace

kubectl apply -f deployment.yaml
kubectl apply -f service.yaml

kubectl logs <fluent-bit-pod> -n kube-logging

export POD_NAME=$(kubectl get pods --namespace observability -l "app.kubernetes.io/instance=jaeger,app.kubernetes.io/component=query" -o jsonpath="{.items[0].metadata.name}")
echo http://127.0.0.1:8080/
kubectl port-forward --namespace observability $POD_NAME 8080:16686

docker run --rm -it \                                                                                                                                        0.04s [main●●] ~/code/youtoo/k8s/terraform-kind-cluster
  -d \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 4317:4317 \
  -p 16686:16686 \
  jaegertracing/all-in-one:1.47
