from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from prometheus_fastapi_instrumentator import Instrumentator

from .config import settings
from .engine import evaluate
from .models import EvaluateRequest, EvaluateResponse, Product
from .qdrant_store import ProductVectorStore

store = ProductVectorStore()


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


app = FastAPI(
    title="ReEngageAI Intelligence API",
    version="1.0.0",
    description="Explainable purchase intent and hybrid product recommendations.",
    lifespan=lifespan,
)
Instrumentator().instrument(app).expose(app, endpoint="/metrics")


@app.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "up"}


@app.get("/health/ready")
def ready() -> dict[str, str]:
    try:
        store.client.get_collections()
        return {"status": "ready", "qdrant": "up"}
    except Exception:
        return {"status": "degraded", "qdrant": "unavailable", "fallback": "in-process ranking"}


@app.post("/v1/evaluate", response_model=EvaluateResponse)
def evaluate_user(request: EvaluateRequest) -> EvaluateResponse:
    return evaluate(request)


@app.put("/v1/catalog/index")
def index_catalog(products: list[Product]) -> dict[str, int]:
    try:
        return {"indexed": store.sync(products)}
    except Exception as error:
        raise HTTPException(status_code=503, detail=f"Qdrant unavailable: {error}") from error
