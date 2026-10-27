package com.daara.internatadmin.comptabilite.recette;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.comptabilite.StatutMouvement;
import com.daara.internatadmin.comptabilite.recette.dto.AutreRecetteResponse;
import com.daara.internatadmin.comptabilite.recette.dto.CreateAutreRecetteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class AutreRecetteService {

    /** Fenetre du controle de doublon : meme source, montant, date et utilisateur. */
    static final long DUPLICATE_WINDOW_MINUTES = 2;

    private final AutreRecetteRepository repository;
    private final Clock clock;

    public AutreRecetteService(AutreRecetteRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Audited(action = "CREATE", entityType = "AutreRecette")
    @Transactional
    public AutreRecetteResponse create(CreateAutreRecetteRequest dto, CurrentUser user) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate recetteDate = (dto.recetteDate() != null) ? dto.recetteDate() : now.toLocalDate();

        if (!dto.confirmDuplicate() && repository.existsBySourceAndAmountAndRecetteDateAndRegisteredByUserIdAndCreatedAtAfter(
                dto.source(), dto.amount(), recetteDate, user.id(), now.minusMinutes(DUPLICATE_WINDOW_MINUTES))) {
            throw new DuplicateEntityException(
                    "Une recette identique (même source, montant et date) vient d'être enregistrée il y a moins de 2 minutes. "
                            + "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true.");
        }

        AutreRecette recette = new AutreRecette();
        recette.setSource(dto.source());
        recette.setAmount(dto.amount());
        recette.setDescription(dto.description());
        recette.setRegisteredByUserId(user.id());
        recette.setRegisteredByUsername(user.username());
        recette.setRecetteDate(recetteDate);
        recette.setCreatedAt(now);
        return toResponse(repository.save(recette));
    }

    @Audited(action = "CANCEL", entityType = "AutreRecette")
    @Transactional
    public AutreRecetteResponse cancel(Long id) {
        AutreRecette recette = findRecette(id);
        if (recette.getStatus() == StatutMouvement.ANNULE) {
            throw new IllegalArgumentException("Cette recette est déjà annulée");
        }
        recette.setStatus(StatutMouvement.ANNULE);
        return toResponse(recette);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "AutreRecette")
    @Transactional(readOnly = true)
    public PrintActionResponse recordReceiptPrint(Long id) {
        AutreRecette recette = findRecette(id);
        return new PrintActionResponse(recette.getId(), "Impression du reçu enregistrée pour la recette #" + recette.getId());
    }

    @Transactional(readOnly = true)
    public AutreRecetteResponse getReceiptData(Long id) {
        return toResponse(findRecette(id));
    }

    /** Recettes valides d'un mois. */
    @Transactional(readOnly = true)
    public List<AutreRecetteResponse> list(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return repository.findByStatusAndRecetteDateBetween(StatutMouvement.VALIDE, ym.atDay(1), ym.atEndOfMonth())
                .stream().map(AutreRecetteService::toResponse).toList();
    }

    private AutreRecette findRecette(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Recette introuvable : " + id));
    }

    static AutreRecetteResponse toResponse(AutreRecette r) {
        return new AutreRecetteResponse(r.getId(), r.getSource(), r.getAmount(), r.getRecetteDate(),
                r.getDescription(), r.getRegisteredByUsername(), r.getStatus());
    }
}
