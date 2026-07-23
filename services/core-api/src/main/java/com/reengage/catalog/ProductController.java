package com.reengage.catalog;

import com.reengage.common.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final JdbcClient jdbc;
    ProductController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ProductCollection list(@RequestParam(defaultValue = "") String q,
                                  @RequestParam(defaultValue = "") String category,
                                  @RequestParam(defaultValue = "") String brand,
                                  @RequestParam(defaultValue = "0") int minPrice,
                                  @RequestParam(defaultValue = "1000000") int maxPrice) {
        var products = jdbc.sql("""
                SELECT id,name,brand,category,description,price_inr,rating,stock,
                       quality_score,features::text,accent,image_key
                FROM product WHERE active=true AND price_inr BETWEEN :min AND :max
                  AND (:category='' OR category=:category) AND (:brand='' OR brand=:brand)
                  AND (:q='' OR search_tsv @@ websearch_to_tsquery('english',:q)
                       OR lower(name) LIKE ('%' || lower(:q) || '%'))
                ORDER BY quality_score DESC,rating DESC,name
                """).param("min", minPrice).param("max", maxPrice).param("category", category)
                .param("brand", brand).param("q", q).query(Product.class).list();
        return new ProductCollection(products,
                jdbc.sql("SELECT DISTINCT category FROM product WHERE active=true ORDER BY category")
                        .query(String.class).list(),
                jdbc.sql("SELECT DISTINCT brand FROM product WHERE active=true ORDER BY brand")
                        .query(String.class).list());
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable String id) {
        return jdbc.sql("""
                SELECT id,name,brand,category,description,price_inr,rating,stock,
                       quality_score,features::text,accent,image_key
                FROM product WHERE id=:id AND active=true
                """).param("id", id).query(Product.class).optional()
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public record Product(String id, String name, String brand, String category, String description,
                          int priceInr, BigDecimal rating, int stock, BigDecimal qualityScore,
                          String features, String accent, String imageKey) {}
    public record ProductCollection(List<Product> products, List<String> categories, List<String> brands) {}
}
