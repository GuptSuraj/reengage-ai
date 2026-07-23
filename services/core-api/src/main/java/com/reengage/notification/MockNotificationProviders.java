package com.reengage.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

abstract class BaseMockProvider implements NotificationProvider {
    private final boolean fail;
    BaseMockProvider(@Value("${app.notification.force-provider-failure:false}") boolean fail) { this.fail=fail; }
    @Override public DeliveryResult send(NotificationMessage message) {
        if(fail) return new DeliveryResult(false,null,"503","mock provider unavailable");
        return new DeliveryResult(true,"mock_"+UUID.randomUUID(),"202",null);
    }
}

@Component
class MockWhatsAppProvider extends BaseMockProvider {
    MockWhatsAppProvider(@Value("${app.notification.force-provider-failure:false}") boolean fail){ super(fail); }
    @Override public String channel(){ return "WHATSAPP"; }
}

@Component
class MockEmailProvider extends BaseMockProvider {
    MockEmailProvider(@Value("${app.notification.force-provider-failure:false}") boolean fail){ super(fail); }
    @Override public String channel(){ return "EMAIL"; }
}
