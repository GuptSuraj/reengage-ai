.PHONY: install build test audit smoke up down clean logs ps load

install:
	npm ci
	python3 -m pip install -r services/ai-service/requirements.txt

build:
	npm run typecheck
	npm run build:web
	cd services/core-api && mvn -B -DskipTests package

test:
	cd services/core-api && mvn -B test
	cd services/ai-service && python3 -m pytest -q
	npm run typecheck

audit:
	npm audit --omit=dev --audit-level=high

smoke:
	bash tests/smoke.sh

up:
	docker compose up --build -d

down:
	docker compose down

clean:
	docker compose down --volumes --remove-orphans

logs:
	docker compose logs -f frontend core-api ai-service

ps:
	docker compose ps

load:
	python3 tests/load_test.py --events 10000 --batch-size 50 --concurrency 20
