package com.reengage.catalog;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    RecommendationController(JdbcClient jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    @GetMapping
    public List<Map<String,Object>> recommendations(Authentication auth) throws Exception {
        var text=jdbc.sql("SELECT recommendations::text FROM user_profile WHERE user_id=:u")
                .param("u",(UUID)auth.getPrincipal()).query(String.class).optional().orElse("[]");
        List<Map<String,Object>> ranked=mapper.readValue(text,new TypeReference<>(){});
        if(ranked.isEmpty()) return jdbc.sql("""
                SELECT id "productId",name,brand,category,price_inr,rating,stock,accent,image_key,
                rating/5.0 score,'Popular and highly rated' reason
                FROM product WHERE active=true AND stock>0 ORDER BY quality_score DESC,rating DESC LIMIT 3
                """).query().listOfRows();
        var byId=new HashMap<String,Map<String,Object>>();
        ranked.forEach(item->byId.put(Objects.toString(item.get("productId")),item));
        var products=jdbc.sql("""
                SELECT id,name,brand,category,description,price_inr,rating,stock,accent,image_key
                FROM product WHERE id IN (:ids) AND active=true AND stock>0
                """).param("ids",byId.keySet()).query().listOfRows();
        products.forEach(product->product.putAll(byId.get(Objects.toString(product.get("id")))));
        products.sort(Comparator.comparingDouble(p->-((Number)p.getOrDefault("score",0)).doubleValue()));
        return products;
    }
}
