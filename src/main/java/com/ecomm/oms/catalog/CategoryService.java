package com.ecomm.oms.catalog;

import com.ecomm.oms.catalog.dto.CategoryRequest;
import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categoryRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Category", id));
    }

    @Transactional
    public Category create(CategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        return categoryRepository.save(new Category(request.name().trim(), parent));
    }

    @Transactional
    public Category update(Long id, CategoryRequest request) {
        Category category = get(id);
        Category parent = resolveParent(request.parentId());
        ensureNoCycle(category, parent);
        category.setName(request.name().trim());
        category.setParent(parent);
        return category;
    }

    @Transactional
    public void delete(Long id) {
        Category category = get(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Category has child categories", "CATEGORY_HAS_CHILDREN");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category has products", "CATEGORY_HAS_PRODUCTS");
        }
        categoryRepository.delete(category);
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> NotFoundException.of("Category", parentId));
    }

    /** Walk the proposed parent's ancestor chain to reject self-parenting and cycles. */
    private void ensureNoCycle(Category category, Category proposedParent) {
        for (Category ancestor = proposedParent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(category.getId())) {
                throw new ConflictException(
                        "A category cannot be its own ancestor", "CATEGORY_CYCLE");
            }
        }
    }
}
