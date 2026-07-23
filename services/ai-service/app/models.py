from datetime import datetime
from typing import Any, Optional
from pydantic import BaseModel, Field


class Event(BaseModel):
    eventId: str
    eventType: str
    productId: Optional[str] = None
    timestamp: datetime
    metadata: dict[str, Any] = Field(default_factory=dict)


class Product(BaseModel):
    id: str
    name: str
    brand: str
    category: str
    description: str
    priceInr: int
    rating: float
    stock: int
    qualityScore: float = 1.0
    features: list[str] = Field(default_factory=list)


class EvaluateRequest(BaseModel):
    userId: str
    events: list[Event]
    products: list[Product]
    purchasedProductIds: set[str] = Field(default_factory=set)
    blockedCategories: set[str] = Field(default_factory=set)
    anchorProductId: Optional[str] = None
    limit: int = Field(default=3, ge=1, le=20)


class Signal(BaseModel):
    name: str
    impact: float


class Recommendation(BaseModel):
    productId: str
    score: float
    reason: str
    components: dict[str, float]


class Profile(BaseModel):
    preferredCategories: list[str]
    preferredBrands: list[str]
    expectedPriceRange: list[int]
    recentSearches: list[str]
    recentlyViewed: list[str]


class EvaluateResponse(BaseModel):
    intentScore: float
    intentLevel: str
    signals: list[Signal]
    profile: Profile
    recommendations: list[Recommendation]
    anchorProductId: Optional[str]
    modelVersion: str
