from __future__ import annotations

import hashlib
import math
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from typing import Optional

import numpy as np

from .config import settings
from .models import EvaluateRequest, EvaluateResponse, Profile, Recommendation, Signal

WEIGHTS = {
    "PAGE_VIEWED": 0.01,
    "PRODUCT_VIEWED": 0.08,
    "SEARCH_PERFORMED": 0.08,
    "FILTER_APPLIED": 0.07,
    "PRODUCT_COMPARED": 0.12,
    "ADD_TO_CART": 0.34,
    "REMOVE_FROM_CART": -0.18,
    "CHECKOUT_STARTED": 0.42,
    "PURCHASE_COMPLETED": -1.0,
    "TIME_SPENT": 0.05,
    "SESSION_STARTED": 0.02,
}
LABELS = {
    "ADD_TO_CART": "Added product to cart",
    "CHECKOUT_STARTED": "Started checkout",
    "PRODUCT_COMPARED": "Compared products",
    "PRODUCT_VIEWED": "Viewed product details",
    "SEARCH_PERFORMED": "Used a specific search",
    "FILTER_APPLIED": "Narrowed product choices",
    "TIME_SPENT": "Spent time evaluating",
    "REPEAT_INTEREST": "Returned to products",
}


def _age_hours(timestamp: datetime) -> float:
    value = timestamp if timestamp.tzinfo else timestamp.replace(tzinfo=timezone.utc)
    return max(0.0, (datetime.now(timezone.utc) - value).total_seconds() / 3600)


def _embedding(text: str, dimensions: Optional[int] = None) -> np.ndarray:
    size = dimensions or settings.vector_size
    vector = np.zeros(size, dtype=np.float32)
    for token in re.findall(r"[a-z0-9]+", text.lower()):
        digest = hashlib.blake2b(token.encode(), digest_size=8).digest()
        index = int.from_bytes(digest[:4], "little") % size
        vector[index] += 1 if digest[4] % 2 else -1
    norm = np.linalg.norm(vector)
    return vector / norm if norm else vector


def _product_text(product) -> str:
    return " ".join([product.name, product.brand, product.category, product.description, *product.features])


def evaluate(request: EvaluateRequest) -> EvaluateResponse:
    by_id = {product.id: product for product in request.products}
    contributions: dict[str, float] = defaultdict(float)
    category_scores, brand_scores, views = Counter(), Counter(), Counter()
    searches: list[str] = []
    prices: list[int] = []
    latest_purchase = max(
        (event.timestamp for event in request.events if event.eventType == "PURCHASE_COMPLETED"),
        default=None,
    )

    for event in request.events:
        if latest_purchase and event.timestamp <= latest_purchase:
            continue
        recency = math.exp(-math.log(2) * _age_hours(event.timestamp) / settings.half_life_hours)
        value = WEIGHTS.get(event.eventType, 0.0) * recency
        if event.eventType == "TIME_SPENT":
            value *= min(3.0, max(1.0, float(event.metadata.get("seconds", 0)) / 60))
        contributions[event.eventType] += value
        product = by_id.get(event.productId or "")
        if product:
            affinity = max(0.2, abs(WEIGHTS.get(event.eventType, 0.03)) * 8) * recency
            category_scores[product.category] += affinity
            brand_scores[product.brand] += affinity
            prices.append(product.priceInr)
            if event.eventType == "PRODUCT_VIEWED":
                views[product.id] += 1
        if event.eventType == "SEARCH_PERFORMED" and event.metadata.get("query"):
            searches.append(str(event.metadata["query"]))

    repeat_bonus = min(0.15, sum(max(0, count - 1) for count in views.values()) * 0.04)
    contributions["REPEAT_INTEREST"] += repeat_bonus
    raw = max(0.0, sum(contributions.values()))
    intent = max(0.0, min(1.0, 1 - math.exp(-raw * 1.55)))
    level = "HIGH" if intent >= 0.67 else "MEDIUM" if intent >= 0.34 else "LOW"
    profile = Profile(
        preferredCategories=[name for name, _ in category_scores.most_common(3)],
        preferredBrands=[name for name, _ in brand_scores.most_common(3)],
        expectedPriceRange=[int(min(prices) * 0.8), int(max(prices) * 1.2)] if prices else [0, 100_000],
        recentSearches=searches[-5:][::-1],
        recentlyViewed=[name for name, _ in views.most_common(5)],
    )
    anchor_id = request.anchorProductId or _anchor(request.events)
    recommendations = _recommend(request, profile, by_id.get(anchor_id or ""))
    signals = [
        Signal(name=LABELS.get(name, name.replace("_", " ").title()), impact=round(value, 4))
        for name, value in sorted(contributions.items(), key=lambda pair: pair[1], reverse=True)
        if value > 0
    ][:5]
    return EvaluateResponse(
        intentScore=round(intent, 4), intentLevel=level, signals=signals, profile=profile,
        recommendations=recommendations, anchorProductId=anchor_id, modelVersion=settings.model_version,
    )


def _anchor(events) -> Optional[str]:
    weights = {"CHECKOUT_STARTED": 8, "ADD_TO_CART": 6, "PRODUCT_COMPARED": 3, "PRODUCT_VIEWED": 2}
    scores = Counter()
    for event in events:
        if event.productId:
            scores[event.productId] += weights.get(event.eventType, 0)
    return scores.most_common(1)[0][0] if scores else None


def _recommend(request: EvaluateRequest, profile: Profile, anchor) -> list[Recommendation]:
    anchor_vector = _embedding(_product_text(anchor)) if anchor else None
    low, high = profile.expectedPriceRange
    ranked: list[Recommendation] = []
    for product in request.products:
        if (not product.stock or product.id == getattr(anchor, "id", None)
                or product.id in request.purchasedProductIds
                or product.category in request.blockedCategories or product.qualityScore < 0.5):
            continue
        content = float(np.dot(anchor_vector, _embedding(_product_text(product)))) if anchor_vector is not None else 0
        category = 1.0 if product.category in profile.preferredCategories else 0
        brand = 1.0 if product.brand in profile.preferredBrands else 0
        price = 1.0 if low <= product.priceInr <= high else max(0, 1 - abs(product.priceInr - (low + high) / 2) / max(high, 1))
        popularity = product.rating / 5
        components = {"content": content, "category": category, "brand": brand,
                      "price": price, "quality": product.qualityScore, "popularity": popularity}
        score = max(0, 0.38 * content + 0.2 * category + 0.1 * brand + 0.14 * price
                    + 0.1 * product.qualityScore + 0.08 * popularity)
        reason = "Similar features" if content > 0.45 else (
            f"Matches your interest in {product.category}" if category else "Strong quality and price match")
        ranked.append(Recommendation(productId=product.id, score=round(score, 4),
                                     reason=reason, components={k: round(v, 4) for k, v in components.items()}))
    return sorted(ranked, key=lambda item: item.score, reverse=True)[: request.limit]
