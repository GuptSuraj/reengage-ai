from qdrant_client import QdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

from .config import settings
from .engine import _embedding, _product_text
from .models import Product


class ProductVectorStore:
    """Qdrant adapter. Ranking remains deterministic when Qdrant is temporarily unavailable."""

    def __init__(self) -> None:
        self.client = QdrantClient(url=settings.qdrant_url, timeout=2)

    def sync(self, products: list[Product]) -> int:
        if not self.client.collection_exists(settings.qdrant_collection):
            self.client.create_collection(
                settings.qdrant_collection,
                vectors_config=VectorParams(size=settings.vector_size, distance=Distance.COSINE),
            )
        points = [
            PointStruct(
                id=index + 1,
                vector=_embedding(_product_text(product)).tolist(),
                payload=product.model_dump(),
            )
            for index, product in enumerate(products)
        ]
        if points:
            self.client.upsert(settings.qdrant_collection, points=points, wait=True)
        return len(points)
