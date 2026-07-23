export interface Product {
  id: string; name: string; brand: string; category: string; description: string;
  priceInr: number; rating: number; stock: number; qualityScore: number;
  features: string; accent: string; imageKey: string;
}
export interface CartItem extends Product { productId: string; quantity: number; }

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/proxy/${path}`, {
    ...init,
    headers: {"content-type": "application/json", ...init.headers},
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.detail ?? body.message ?? "Request failed");
  return body as T;
}
export const money = (value: number) =>
  new Intl.NumberFormat("en-IN", {style: "currency", currency: "INR", maximumFractionDigits: 0}).format(value);
export const features = (product: Product): string[] => {
  try { return JSON.parse(product.features); } catch { return []; }
};
