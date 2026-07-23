package com.reengage.cart;

import tools.jackson.databind.ObjectMapper;
import com.reengage.common.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CartController {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    CartController(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @GetMapping("/cart")
    public CartResponse cart(Authentication auth) {
        var items = jdbc.sql("""
                SELECT p.id product_id,p.name,p.brand,p.category,p.price_inr,p.stock,
                       p.accent,p.image_key,c.quantity
                FROM cart_item c JOIN product p ON p.id=c.product_id
                WHERE c.user_id=:userId ORDER BY c.updated_at DESC
                """).param("userId", id(auth)).query(CartItem.class).list();
        return new CartResponse(items, items.stream().mapToInt(i -> i.priceInr() * i.quantity()).sum());
    }

    @PutMapping("/cart/items/{productId}")
    @Transactional
    public CartResponse put(@PathVariable String productId, @RequestBody QuantityRequest request,
                            Authentication auth) {
        var stock = jdbc.sql("SELECT stock FROM product WHERE id=:id AND active=true").param("id", productId)
                .query(Integer.class).optional().orElseThrow(() -> new NotFoundException("Product not found"));
        if (request.quantity() < 1 || request.quantity() > stock) throw new IllegalArgumentException("Invalid quantity");
        jdbc.sql("""
                INSERT INTO cart_item(user_id,product_id,quantity) VALUES (:userId,:productId,:quantity)
                ON CONFLICT(user_id,product_id) DO UPDATE SET quantity=excluded.quantity,updated_at=now()
                """).param("userId", id(auth)).param("productId", productId)
                .param("quantity", request.quantity()).update();
        return cart(auth);
    }

    @DeleteMapping("/cart/items/{productId}")
    public CartResponse remove(@PathVariable String productId, Authentication auth) {
        jdbc.sql("DELETE FROM cart_item WHERE user_id=:u AND product_id=:p")
                .param("u", id(auth)).param("p", productId).update();
        return cart(auth);
    }

    @PostMapping("/checkout")
    @Transactional
    public OrderResponse checkout(@RequestHeader("Idempotency-Key") String key, Authentication auth) throws Exception {
        var userId = id(auth);
        var old = jdbc.sql("""
                SELECT id,total_inr,created_at::text FROM purchase_order
                WHERE user_id=:u AND idempotency_key=:k
                """).param("u", userId).param("k", key).query(OrderResponse.class).optional();
        if (old.isPresent()) return old.get();
        var items = jdbc.sql("""
                SELECT p.id product_id,p.name,p.price_inr,p.stock,c.quantity
                FROM cart_item c JOIN product p ON p.id=c.product_id
                WHERE c.user_id=:u FOR UPDATE OF p
                """).param("u", userId).query(CheckoutItem.class).list();
        if (items.isEmpty()) throw new IllegalArgumentException("Cart is empty");
        items.forEach(i -> { if (i.quantity() > i.stock()) throw new IllegalArgumentException(i.name()+" is unavailable"); });
        var orderId = UUID.randomUUID();
        var total = items.stream().mapToInt(i -> i.priceInr() * i.quantity()).sum();
        jdbc.sql("INSERT INTO purchase_order(id,user_id,idempotency_key,total_inr) VALUES (:id,:u,:k,:t)")
                .param("id", orderId).param("u", userId).param("k", key).param("t", total).update();
        for (var i : items) {
            jdbc.sql("""
                    INSERT INTO order_item(order_id,product_id,product_name,unit_price_inr,quantity)
                    VALUES (:o,:p,:n,:price,:q)
                    """).param("o", orderId).param("p", i.productId()).param("n", i.name())
                    .param("price", i.priceInr()).param("q", i.quantity()).update();
            jdbc.sql("UPDATE product SET stock=stock-:q,version=version+1 WHERE id=:p")
                    .param("q", i.quantity()).param("p", i.productId()).update();
        }
        jdbc.sql("DELETE FROM cart_item WHERE user_id=:u").param("u", userId).update();
        jdbc.sql("""
                UPDATE notification_job SET status='CANCELLED',cancelled_at=now(),
                failure_reason='purchase_completed',updated_at=now()
                WHERE user_id=:u AND status IN ('SCHEDULED','PROCESSING')
                """).param("u", userId).update();
        var purchaseEventId=UUID.randomUUID();
        var sessionId=UUID.randomUUID();
        var purchaseMetadata=mapper.writeValueAsString(Map.of("orderId",orderId,"totalInr",total));
        jdbc.sql("""
                INSERT INTO behaviour_event(event_id,user_id,session_id,event_type,occurred_at,source_page,metadata)
                VALUES (:eventId,:u,:session,'PURCHASE_COMPLETED',now(),'/checkout',CAST(:metadata AS jsonb))
                """).param("eventId",purchaseEventId).param("u",userId).param("session",sessionId)
                .param("metadata",purchaseMetadata).update();
        var payload = mapper.writeValueAsString(Map.of("orderId", orderId, "userId", userId,
                "productIds", items.stream().map(CheckoutItem::productId).toList(), "totalInr", total));
        jdbc.sql("""
                INSERT INTO outbox_event(aggregate_type,aggregate_id,topic,event_key,payload)
                VALUES ('ORDER',:o,'purchase-events',:u,CAST(:payload AS jsonb))
                """).param("o", orderId.toString()).param("u", userId.toString()).param("payload", payload).update();
        var behaviourPayload=mapper.writeValueAsString(Map.of("eventId",purchaseEventId,"userId",userId,
                "anonymousId","","sessionId",sessionId,"eventType","PURCHASE_COMPLETED","productId","",
                "timestamp",java.time.Instant.now(),"sourcePage","/checkout","device",Map.of(),
                "metadata",Map.of("orderId",orderId,"totalInr",total)));
        jdbc.sql("""
                INSERT INTO outbox_event(aggregate_type,aggregate_id,topic,event_key,payload)
                VALUES ('BEHAVIOUR_EVENT',:eventId,'user-behaviour-events',:u,CAST(:payload AS jsonb))
                """).param("eventId",purchaseEventId.toString()).param("u",userId.toString())
                .param("payload",behaviourPayload).update();
        return jdbc.sql("SELECT id,total_inr,created_at::text FROM purchase_order WHERE id=:id")
                .param("id", orderId).query(OrderResponse.class).single();
    }

    @GetMapping("/orders")
    public List<Map<String,Object>> orders(Authentication auth) {
        return jdbc.sql("""
                SELECT o.id,o.status,o.total_inr,o.created_at,
                  COALESCE(jsonb_agg(jsonb_build_object('productId',i.product_id,'name',i.product_name,
                  'priceInr',i.unit_price_inr,'quantity',i.quantity)) FILTER (WHERE i.order_id IS NOT NULL),'[]') items
                FROM purchase_order o LEFT JOIN order_item i ON i.order_id=o.id
                WHERE o.user_id=:u GROUP BY o.id ORDER BY o.created_at DESC
                """).param("u", id(auth)).query().listOfRows();
    }
    private UUID id(Authentication auth) { return (UUID) auth.getPrincipal(); }
    public record QuantityRequest(int quantity) {}
    public record CartItem(String productId,String name,String brand,String category,int priceInr,int stock,
                           String accent,String imageKey,int quantity) {}
    public record CartResponse(List<CartItem> items,int totalInr) {}
    private record CheckoutItem(String productId,String name,int priceInr,int stock,int quantity) {}
    public record OrderResponse(UUID id,int totalInr,String createdAt) {}
}
