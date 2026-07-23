package com.reengage.profile;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class AiClient {
    private final RestClient client;
    AiClient(RestClient aiRestClient) { this.client = aiRestClient; }

    public Evaluation evaluate(Map<String,Object> request) {
        return client.post().uri("/v1/evaluate").body(request).retrieve().body(Evaluation.class);
    }

    public record Evaluation(double intentScore,String intentLevel,List<Map<String,Object>> signals,
                             Map<String,Object> profile,List<Recommendation> recommendations,
                             String anchorProductId,String modelVersion) {}
    public record Recommendation(String productId,double score,String reason,Map<String,Double> components) {}
}
