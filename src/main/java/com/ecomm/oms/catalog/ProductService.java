package com.ecomm.oms.catalog;

import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<Product> browse(Long categoryId, String q, Pageable pageable) {
        String term = (q == null || q.isBlank()) ? null : q.trim();
        return productRepository.browse(categoryId, term, pageable);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("SKU already exists", "SKU_TAKEN");
        }
        Product product = new Product(
                request.sku().trim(),
                request.name().trim(),
                request.description(),
                request.basePrice(),
                request.taxCategoryOrDefault(),
                request.activeOrDefault(),
                resolveCategory(request.categoryId()));
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new ConflictException("SKU already exists", "SKU_TAKEN");
        }
        product.setSku(request.sku().trim());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        product.setTaxCategory(request.taxCategoryOrDefault());
        product.setActive(request.activeOrDefault());
        product.setCategory(resolveCategory(request.categoryId()));
        return product;
    }

    @Transactional
    public void delete(Long id) {
        Product product = get(id);
        productRepository.delete(product);
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> NotFoundException.of("Category", categoryId));
    }
}
