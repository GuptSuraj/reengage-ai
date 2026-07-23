package com.reengage.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template){
        var backoff=new ExponentialBackOff(1000,2.0);
        backoff.setMaxInterval(30_000);
        backoff.setMaxElapsedTime(120_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template),backoff);
    }

    @Bean NewTopic behaviourTopic(){return topic("user-behaviour-events",6);}
    @Bean NewTopic cartTopic(){return topic("cart-events",3);}
    @Bean NewTopic purchaseTopic(){return topic("purchase-events",3);}
    @Bean NewTopic profileTopic(){return topic("profile-updates",6);}
    @Bean NewTopic notificationJobsTopic(){return topic("notification-jobs",3);}
    @Bean NewTopic notificationResultsTopic(){return topic("notification-results",3);}
    private NewTopic topic(String name,int partitions){
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
