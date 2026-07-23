package com.reengage.notification;

public interface NotificationProvider {
    DeliveryResult send(NotificationMessage message);
    String channel();

    record NotificationMessage(String recipient,String body,String idempotencyKey) {}
    record DeliveryResult(boolean accepted,String providerMessageId,String responseCode,String error) {}
}
