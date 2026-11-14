# gateway-service

API Gateway do projeto **pma26**: ponto único de entrada da aplicação. Roteia as requisições para os microsserviços e valida o token JWT nas rotas protegidas.

Stack: Java 21, Spring Boot 4.1, Spring Cloud Gateway (WebFlux), Actuator + Prometheus.

## Rotas

| Prefixo | Serviço | Variável de ambiente (padrão) |
|---------|---------|-------------------------------|
| `/auth/**` | auth | `AUTH_URL` (`http://auth:8080`) |
| `/accounts/**` | account | `ACCOUNT_URL` (`http://account:8080`) |
| `/products/**` | product | `PRODUCT_URL` (`http://product:8080`) |
| `/orders/**` | order | `ORDER_URL` (`http://order:8080`) |
| `/exchange/**` | exchange | `EXCHANGE_URL` (`http://exchange:8080`) |

Endpoints do próprio gateway:

| Endpoint | Uso |
|----------|-----|
| `GET /health-check` | probes do Kubernetes |
| `GET /info` | nome + hostname do pod (teste de carga / HPA) |
| `GET /gateway/actuator/prometheus` | métricas para o Prometheus |
| `GET /gateway/actuator/health/liveness` e `/readiness` | probes do Kubernetes |

## Autorização

O `AuthorizationFilter` roda antes de todas as rotas:

1. Remove os headers `id-account` e `role-account` vindos do cliente, para ninguém se passar por outro usuário.
2. **Rotas abertas** (sem token): `POST /auth/login`, `POST /auth/register`, `/health-check`, `/info`, `/**/health-check`, `/gateway/actuator/**` e preflight `OPTIONS` de CORS.
3. As demais exigem `Authorization: Bearer <token>`. O gateway chama o Auth:
   ```
   POST {AUTH_URL}/auth/solve   {"jwt": "<token>"}
   200 -> {"idAccount": "...", "role": "..."}
   4xx -> token inválido/expirado
   ```
4. **RBAC**: rotas só de admin (`GET /accounts`, `POST /accounts`, `POST /accounts/login`, `DELETE /accounts/**`) devolvem **403** se o `role` do token não for `admin`.
5. Acesso liberado: repassa `id-account` e `role-account` para o serviço de destino.
   Token ausente ou inválido: **401**. Auth fora do ar: **503**.


## Variáveis de ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `AUTH_URL`, `ACCOUNT_URL`, `PRODUCT_URL`, `ORDER_URL`, `EXCHANGE_URL` | `http://<servico>:8080` | URL de cada serviço |
| `CORS_ALLOWED_ORIGINS` | `*` | origens liberadas no CORS |

## Rodando

```bash
./mvnw verify                 # build + testes
./mvnw spring-boot:run        # sobe na porta 8080

docker build -t gateway .
docker run -p 8080:8080 gateway
```

## Testes

- `RouterValidatorTest`: quais rotas são abertas e quais são protegidas (e quais são só de admin).
- `GatewayIntegrationTest`: sobe o gateway com um servidor HTTP falso no lugar do Auth/Account e testa health-check, info, métricas, 401 sem token ou com token inválido,  em rota de admin com token de user, remoção de headers forjados e preflight de CORS.

## Uso de IA

Código gerado com auxílio do Claude Code (Anthropic) e revisado pelo grupo. Detalhes em `docs/ia.md` no repositório principal (`pma26`).
