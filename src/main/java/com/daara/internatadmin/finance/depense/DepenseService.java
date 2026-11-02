package com.daara.internatadmin.finance.depense;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.DuplicateEntityException;
import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.StatutMouvement;
import com.daara.internatadmin.finance.categoriedepense.CategorieDepense;
import com.daara.internatadmin.finance.categoriedepense.CategorieDepenseRepository;
import com.daara.internatadmin.finance.depense.dto.CreateDepenseRequest;
import com.daara.internatadmin.finance.depense.dto.DepenseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepenseService {

    private final DepenseRepository depenseRepository;
    private final CategorieDepenseRepository categorieDepenseRepository;

    @Audited(action = "CREATE", entityType = "Depense")
    @Transactional
    public DepenseResponse create(CreateDepenseRequest dto, Long currentUserId, String currentUsername) {
        CategorieDepense categorie = categorieDepenseRepository.findById(dto.categorieId())
                .orElseThrow(() -> new IllegalArgumentException("Catégorie introuvable : " + dto.categorieId()));

        if (!categorie.isActive()) {
            throw new IllegalArgumentException(
                    "La catégorie '" + categorie.getName() + "' est désactivée et ne peut plus être utilisée"
            );
        }

        LocalDate expenseDate = (dto.expenseDate() != null) ? dto.expenseDate() : LocalDate.now();

        if (!dto.confirmDuplicate()) {
            boolean possibleDuplicate = depenseRepository.existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
                    dto.categorieId(), dto.amount(), expenseDate, currentUserId, LocalDateTime.now().minusMinutes(2)
            );
            if (possibleDuplicate) {
                throw new DuplicateEntityException(
                        "Une dépense identique (même catégorie, montant et date) vient d'être enregistrée il y a moins de 2 minutes. " +
                                "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true."
                );
            }
        }

        Depense depense = new Depense();
        depense.setCategorie(categorie);
        depense.setAmount(dto.amount());
        depense.setDescription(dto.description());
        depense.setRegisteredByUserId(currentUserId);
        depense.setRegisteredByUsername(currentUsername);
        depense.setExpenseDate(expenseDate);

        depenseRepository.save(depense);
        return toResponse(depense);
    }

    @Audited(action = "CANCEL", entityType = "Depense")
    @Transactional
    public DepenseResponse cancel(Long id) {
        Depense depense = depenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable : " + id));

        if (depense.getStatus() == StatutMouvement.ANNULE) {
            throw new IllegalArgumentException("Cette dépense est déjà annulée");
        }

        depense.setStatus(StatutMouvement.ANNULE);
        return toResponse(depense);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "Depense")
    @Transactional
    public PrintActionResponse recordReceiptPrint(Long id) {
        Depense depense = depenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable : " + id));
        return new PrintActionResponse(depense.getId(), "Impression du reçu enregistrée pour la dépense #" + depense.getId());
    }

    public DepenseResponse getReceiptData(Long id) {
        Depense depense = depenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable : " + id));
        return toResponse(depense);
    }

    public List<DepenseResponse> list(int year, int month, Long categorieId) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Depense> depenses = (categorieId != null)
                ? depenseRepository.findByStatusAndCategorieIdAndExpenseDateBetween(StatutMouvement.VALIDE, categorieId, start, end)
                : depenseRepository.findByStatusAndExpenseDateBetween(StatutMouvement.VALIDE, start, end);

        return depenses.stream().map(this::toResponse).toList();
    }

    private DepenseResponse toResponse(Depense d) {
        return new DepenseResponse(
                d.getId(), d.getCategorie().getId(), d.getCategorie().getName(),
                d.getAmount(), d.getExpenseDate(), d.getDescription(),
                d.getRegisteredByUsername(), d.getStatus()
        );
    }
}