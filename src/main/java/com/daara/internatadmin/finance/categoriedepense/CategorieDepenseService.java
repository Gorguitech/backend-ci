package com.daara.internatadmin.finance.categoriedepense;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.finance.categoriedepense.dto.CategorieDepenseResponse;
import com.daara.internatadmin.finance.categoriedepense.dto.CreateCategorieDepenseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorieDepenseService {

    private final CategorieDepenseRepository categorieDepenseRepository;

    @Audited(action = "CREATE", entityType = "CategorieDepense")
    @Transactional
    public CategorieDepenseResponse create(CreateCategorieDepenseRequest dto) {
        CategorieDepense categorie = new CategorieDepense();
        categorie.setName(dto.name());
        categorie.setDescription(dto.description());
        categorieDepenseRepository.save(categorie);
        return toResponse(categorie);
    }

    @Audited(action = "DEACTIVATE", entityType = "CategorieDepense")
    @Transactional
    public CategorieDepenseResponse deactivate(Long id) {
        CategorieDepense categorie = categorieDepenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie introuvable : " + id));
        categorie.setActive(false);
        return toResponse(categorie);
    }


    public List<CategorieDepenseResponse> listAll(String statusFilter) {
        List<CategorieDepense> categories = switch (statusFilter.toUpperCase()) {
            case "ACTIVE" -> categorieDepenseRepository.findByActiveTrue();
            case "INACTIVE" -> categorieDepenseRepository.findByActiveFalse();
            default -> categorieDepenseRepository.findAll();
        };
        return categories.stream().map(this::toResponse).toList();
    }

    private CategorieDepenseResponse toResponse(CategorieDepense c) {
        return new CategorieDepenseResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }
}