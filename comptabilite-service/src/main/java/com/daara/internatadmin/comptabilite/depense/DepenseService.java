package com.daara.internatadmin.comptabilite.depense;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.comptabilite.StatutMouvement;
import com.daara.internatadmin.comptabilite.categorie.CategorieDepense;
import com.daara.internatadmin.comptabilite.categorie.CategorieDepenseRepository;
import com.daara.internatadmin.comptabilite.depense.dto.CreateDepenseRequest;
import com.daara.internatadmin.comptabilite.depense.dto.DepenseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class DepenseService {

    /** Fenetre du controle de doublon : meme categorie, montant, date et utilisateur. */
    static final long DUPLICATE_WINDOW_MINUTES = 2;

    private final DepenseRepository depenseRepository;
    private final CategorieDepenseRepository categorieRepository;
    private final Clock clock;

    public DepenseService(DepenseRepository depenseRepository, CategorieDepenseRepository categorieRepository, Clock clock) {
        this.depenseRepository = depenseRepository;
        this.categorieRepository = categorieRepository;
        this.clock = clock;
    }

    @Audited(action = "CREATE", entityType = "Depense")
    @Transactional
    public DepenseResponse create(CreateDepenseRequest dto, CurrentUser user) {
        CategorieDepense categorie = categorieRepository.findById(dto.categorieId())
                .orElseThrow(() -> new IllegalArgumentException("Catégorie introuvable : " + dto.categorieId()));
        if (!categorie.isActive()) {
            throw new IllegalArgumentException(
                    "La catégorie '" + categorie.getName() + "' est désactivée et ne peut plus être utilisée");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate expenseDate = (dto.expenseDate() != null) ? dto.expenseDate() : now.toLocalDate();

        if (!dto.confirmDuplicate() && depenseRepository.existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
                dto.categorieId(), dto.amount(), expenseDate, user.id(), now.minusMinutes(DUPLICATE_WINDOW_MINUTES))) {
            throw new DuplicateEntityException(
                    "Une dépense identique (même catégorie, montant et date) vient d'être enregistrée il y a moins de 2 minutes. "
                            + "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true.");
        }

        Depense depense = new Depense();
        depense.setCategorie(categorie);
        depense.setAmount(dto.amount());
        depense.setDescription(dto.description());
        depense.setRegisteredByUserId(user.id());
        depense.setRegisteredByUsername(user.username());
        depense.setExpenseDate(expenseDate);
        depense.setCreatedAt(now);
        return toResponse(depenseRepository.save(depense));
    }

    @Audited(action = "CANCEL", entityType = "Depense")
    @Transactional
    public DepenseResponse cancel(Long id) {
        Depense depense = findDepense(id);
        if (depense.getStatus() == StatutMouvement.ANNULE) {
            throw new IllegalArgumentException("Cette dépense est déjà annulée");
        }
        depense.setStatus(StatutMouvement.ANNULE);
        return toResponse(depense);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "Depense")
    @Transactional(readOnly = true)
    public PrintActionResponse recordReceiptPrint(Long id) {
        Depense depense = findDepense(id);
        return new PrintActionResponse(depense.getId(), "Impression du reçu enregistrée pour la dépense #" + depense.getId());
    }

    @Transactional(readOnly = true)
    public DepenseResponse getReceiptData(Long id) {
        return toResponse(findDepense(id));
    }

    /** Depenses valides d'un mois, eventuellement d'une seule categorie. */
    @Transactional(readOnly = true)
    public List<DepenseResponse> list(int year, int month, Long categorieId) {
        YearMonth ym = YearMonth.of(year, month);
        List<Depense> depenses = (categorieId != null)
                ? depenseRepository.findByStatusAndCategorieIdAndExpenseDateBetween(
                        StatutMouvement.VALIDE, categorieId, ym.atDay(1), ym.atEndOfMonth())
                : depenseRepository.findByStatusAndExpenseDateBetween(StatutMouvement.VALIDE, ym.atDay(1), ym.atEndOfMonth());
        return depenses.stream().map(DepenseService::toResponse).toList();
    }

    private Depense findDepense(Long id) {
        return depenseRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Dépense introuvable : " + id));
    }

    static DepenseResponse toResponse(Depense d) {
        return new DepenseResponse(d.getId(), d.getCategorie().getId(), d.getCategorie().getName(), d.getAmount(),
                d.getExpenseDate(), d.getDescription(), d.getRegisteredByUsername(), d.getStatus());
    }
}
