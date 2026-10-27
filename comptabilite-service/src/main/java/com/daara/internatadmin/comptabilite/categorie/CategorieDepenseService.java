package com.daara.internatadmin.comptabilite.categorie;

import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.comptabilite.categorie.dto.CategorieDepenseResponse;
import com.daara.internatadmin.comptabilite.categorie.dto.CreateCategorieDepenseRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategorieDepenseService {

    private final CategorieDepenseRepository repository;

    public CategorieDepenseService(CategorieDepenseRepository repository) {
        this.repository = repository;
    }

    /**
     * Le nom est unique en base. Correction du monolithe : un nom deja pris provoquait une erreur 500 au commit ;
     * il donne maintenant un 400 INVALID_REQUEST explicite.
     */
    @Audited(action = "CREATE", entityType = "CategorieDepense")
    public CategorieDepenseResponse create(CreateCategorieDepenseRequest dto) {
        CategorieDepense categorie = new CategorieDepense();
        categorie.setName(dto.name());
        categorie.setDescription(dto.description());
        try {
            return toResponse(repository.saveAndFlush(categorie));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("La catégorie '" + dto.name() + "' existe déjà");
        }
    }

    @Audited(action = "DEACTIVATE", entityType = "CategorieDepense")
    @Transactional
    public CategorieDepenseResponse deactivate(Long id) {
        CategorieDepense categorie = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie introuvable : " + id));
        categorie.setActive(false);
        return toResponse(categorie);
    }

    /** Filtre ACTIVE (par defaut), INACTIVE ou toute autre valeur pour l'ensemble, comme le monolithe. */
    @Transactional(readOnly = true)
    public List<CategorieDepenseResponse> list(String statusFilter) {
        List<CategorieDepense> categories = switch (statusFilter.toUpperCase()) {
            case "ACTIVE" -> repository.findByActiveTrue();
            case "INACTIVE" -> repository.findByActiveFalse();
            default -> repository.findAll();
        };
        return categories.stream().map(CategorieDepenseService::toResponse).toList();
    }

    static CategorieDepenseResponse toResponse(CategorieDepense c) {
        return new CategorieDepenseResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }
}
