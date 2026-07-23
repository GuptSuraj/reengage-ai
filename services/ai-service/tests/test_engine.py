from datetime import datetime, timezone

from app.engine import evaluate
from app.models import EvaluateRequest, Event, Product


def product(identifier: str, price: int = 8990) -> Product:
    return Product(id=identifier, name=identifier, brand="Sony", category="Headphones",
                   description="wireless noise cancelling", priceInr=price, rating=4.6,
                   stock=10, features=["ANC", "Bluetooth"])


def test_high_intent_is_explainable_and_filtered():
    now = datetime.now(timezone.utc)
    kinds = ["SEARCH_PERFORMED", "FILTER_APPLIED", "PRODUCT_VIEWED", "PRODUCT_VIEWED",
             "PRODUCT_COMPARED", "TIME_SPENT", "ADD_TO_CART"]
    events = [Event(eventId=str(i), eventType=kind, productId="anchor", timestamp=now,
                    metadata={"seconds": 240} if kind == "TIME_SPENT" else {}) for i, kind in enumerate(kinds)]
    result = evaluate(EvaluateRequest(userId="u1", events=events,
                      products=[product("anchor"), product("alternative"), product("purchased")],
                      purchasedProductIds={"purchased"}))
    assert result.intentLevel == "HIGH"
    assert result.signals
    assert [item.productId for item in result.recommendations] == ["alternative"]
