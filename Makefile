# Thin task runner over manage.sh.
#
# Every target maps to exactly one manage.sh subcommand, so each step has a
# single implementation — this file is a discoverable namespace (`make help`),
# not a second build system. Compose is the primary path, so `make up` brings up
# the Docker stack.
#
# Caveat: GNU Make 3.81 (the version bundled with macOS) has no .ONESHELL and no
# .RECIPEPREFIX, so every recipe line is its own shell and the indentation must
# stay tabs.

.DEFAULT_GOAL := help
SHELL := /bin/sh

# Target arguments:
#   make role EMAIL=you@mail.com ROLE=admin
#   make logs WHAT=api
#   make db-reset YES=1
EMAIL ?=
ROLE ?=
WHAT ?=
YES ?=
YESFLAG := $(if $(filter 1,$(YES)),--yes,)

.PHONY: help up down logs compose-build status setup test build fmt \
        native-up native-down native-backend native-frontend native-logs role \
        db-reset db-reseed

help: ## Show this help
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Docker: start the stack (API :8080, UI :3000, Postgres :5433, Mailpit :8025)
	./manage.sh compose:up

down: ## Docker: stop the stack (the database volume is kept)
	./manage.sh compose:down

logs: ## Docker: follow the stack's logs (WHAT=api for a single service)
	./manage.sh compose:logs $(WHAT)

compose-build: ## Docker: rebuild the images after a code change
	./manage.sh compose:build

status: ## Show what is running (covers both the Docker and native paths)
	./manage.sh status

native-up: ## Native: start backend (Gradle) + frontend (npm); needs a local Postgres
	./manage.sh up

native-down: ## Native: stop the backend + frontend
	./manage.sh down

native-backend: ## Native: start only the backend (Gradle, :8080)
	./manage.sh backend

native-frontend: ## Native: start only the frontend (npm, :3000)
	./manage.sh frontend

native-logs: ## Native: follow a service log (WHAT=backend|frontend|both)
	./manage.sh logs $(WHAT)

setup: ## First-time install: frontend dependencies + the Gradle build
	./manage.sh setup

test: ## Backend tests + frontend build (export TEST_DATABASE_URL for the DB-backed tests)
	./manage.sh test

build: ## Build the API jar (build/libs/app.jar) and the frontend bundle
	./manage.sh build

fmt: ## Format with prettier
	./manage.sh fmt

role: ## Set a role: make role EMAIL=you@mail.com ROLE=admin
	./manage.sh role $(EMAIL) $(ROLE)

db-reset: ## Native: drop and recreate the schema (YES=1 skips the confirmation)
	./manage.sh db:reset $(YESFLAG)

db-reseed: ## Native: drop the schema and restart the backend so it migrates and re-seeds
	./manage.sh db:reseed $(YESFLAG)
