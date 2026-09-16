# Kubernetes (Rancher Desktop)

Compose is the primary path; this runs the _same_ four services — Postgres,
Mailpit, the Spring API and Next.js — on Rancher Desktop's cluster, so nothing is
duplicated: the same images built from `backend/Dockerfile` and
`frontend/Dockerfile`, no Helm chart, no kustomize overlay.
`kubectl apply -f k8s/` is the whole deploy.

Same shape as the compose stack — only the front door differs: Traefik replaces
the host port mappings, and cluster DNS replaces compose's service names.

## Prerequisites

1. **Kubernetes enabled** in Rancher Desktop: _Preferences → Kubernetes → Enable
   Kubernetes_. The API server listens on `:6443`.
2. **Docker running** — images are still built with `docker compose build`.
3. `kubectl` on `PATH` (Rancher Desktop installs its own into `~/.rd/bin`).
4. **Ports 80 and 443 free** on the host: Traefik publishes them, and that is
   what makes `template.localhost` reachable from the browser.

## Quick start

```bash
./manage.sh k8s:up          # or: make k8s-up
open http://template.localhost
```

`k8s:up` builds the images if they don't exist yet, applies `k8s/`, then waits
for each rollout.

## Why `imagePullPolicy: Never`

Rancher Desktop's k3s uses **Docker's own daemon** as its container runtime —
`kubectl get nodes -o wide` reports `docker://29.5.3`, and there is no separate
k3s containerd to load images into. So the image store k3s runs from _is_ the one
`docker compose build` writes to: a freshly built image is visible to the cluster
immediately, with no push, no load step and no registry.

That only works because the images are never pulled — `spring-template-api` and
`spring-template-frontend` don't exist in any registry. `imagePullPolicy: Never`
is what points the cluster at the local copies; if one is missing the pod fails
loudly with `ErrImageNeverPull` instead of quietly pulling something else.

One consequence: both images keep the `latest` tag and pods never pull, so a
rebuilt image is invisible to a pod that is already running. That is why a code
change needs `k8s:rebuild`, which rebuilds **and** runs `kubectl rollout restart`.
`k8s:up` on its own only creates objects; it will not move a running pod onto new
code.

Sharing the runtime has a second, visible effect: every pod's containers also
appear in `docker ps`, named `k8s_<container>_<pod>_<namespace>_<uid>_0`. It is a
handy way to inspect a pod's container directly, and it also means `docker ps` is
noisy once the stack is up.

## Commands

| Command                                     | What it does                                                                |
| ------------------------------------------- | --------------------------------------------------------------------------- |
| `./manage.sh k8s:up`                        | Apply the manifests and wait for the rollout (builds the images if missing) |
| `./manage.sh k8s:rebuild`                   | Rebuild after a code change, then restart the pods onto the result          |
| `./manage.sh k8s:down`                      | Delete the workloads and Services (keeps the volume)                        |
| `./manage.sh k8s:status`                    | Pods, Services and Ingress in the namespace                                 |
| `./manage.sh k8s:logs [svc]`                | Follow a deployment's logs (`api`, `frontend`, …)                           |
| `./manage.sh k8s:port-forward mailpit 8025` | Tunnel to Mailpit's web UI                                                  |
| `./manage.sh k8s:psql`                      | `psql` inside the Postgres pod                                              |
| `./manage.sh k8s:reset [--yes]`             | **Destructive**: delete the namespace and all data                          |

Each has a Makefile target (`make k8s-up`, `make k8s-status`, …); `make` with no
argument lists them.

## Reaching things

| What               | How                                                                                                             |
| ------------------ | --------------------------------------------------------------------------------------------------------------- |
| The app            | <http://template.localhost> — no `/etc/hosts` edit needed; macOS resolves any `*.localhost` name to `127.0.0.1` |
| The API directly   | <http://api.template.localhost> — for `curl`; the browser never needs it                                        |
| Mailpit            | `./manage.sh k8s:port-forward mailpit 8025`, then <http://localhost:8025>                                       |
| The database       | `./manage.sh k8s:psql`                                                                                          |
| The API on `:8080` | `./manage.sh k8s:port-forward api 8080`                                                                         |

Unlike compose, **Postgres is not published to the host** — there is no `5433`
mapping. Use the tunnel, or `k8s:psql`.

## Data and lifecycle

Postgres is a StatefulSet owning a `volumeClaimTemplate`, so its data lives in a
PVC (`data-postgres-0`) provisioned by k3s's default `local-path` class.

- `k8s:down` deletes the workloads and Services but **not** the namespace or the
  PVC — the data is still there on the next `k8s:up`.
- `k8s:reset` deletes the namespace, and the PVC goes with it. That is the "start
  completely fresh" command; `k8s:up` recreates everything empty and Flyway
  re-applies from scratch.

Migrations need no separate Job: Flyway runs inside the API's own startup, so the
schema applies when the pod comes up. The API's init container blocks on
`pg_isready` so it doesn't crash-loop waiting for Postgres — the Kubernetes
equivalent of compose's `depends_on: condition: service_healthy`.

## Configuration

`k8s/10-config.yaml` splits the API's environment exactly the way compose does:

- **ConfigMap** `template-config` — `NODE_ENV`, `DATABASE_URL`, `FRONTEND_URL`,
  `SMTP_HOST`, `SMTP_PORT`, `MAIL_FROM`. Service names (`postgres`, `mailpit`)
  resolve as cluster DNS, the counterpart of compose's service-name resolution.
- **Secret** `template-secrets` — `JWT_SECRET`, set to the same dev-only value
  compose uses. The API refuses a short one; see `.env.example`.

`FRONTEND_URL` is `http://template.localhost`, so verification and password-reset
links in outgoing email point at the address the browser actually uses.

## Smoke test

```bash
# the UI is up
curl -s -o /dev/null -w '%{http_code}\n' http://template.localhost/          # 200

# the API is up behind it, through the UI's own proxy
curl -s http://template.localhost/api/me                                     # {"message":"No token provided"}

# end to end: 2FA login, with the code landing in Mailpit
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"admin@mail.com","password":"Password1234!"}' \
  http://template.localhost/api/login
```

The credentials in it are the dev admin from the README, seeded on startup
because `NODE_ENV=development`.

## Probes

The API exposes no health endpoint and the API contract is frozen, so probes stay
at the transport level. For Spring Boot a listening port is a strong signal:
Tomcat only opens it after the context refreshes, which is after Flyway has
applied every migration. The frontend has no liveness probe on purpose — a
`next dev` server under a cold compile can stall long enough to trip one, and a
restart would only restart the compile.

## Scope

This is a local development cluster, deliberately not production-shaped:

- one replica of everything, no `HorizontalPodAutoscaler`, no PodDisruptionBudget
- no TLS, no cert-manager
- dev credentials committed in `10-config.yaml`
- the frontend image runs `next dev`, not a production build
- no image registry: images are built locally and run straight out of Docker's
  image store

Making it production-shaped would mean a registry push, a real Secret, a
production frontend build, and replicas for the API.
