package com.daara.internatadmin.finance.recette;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.DuplicateEntityException;
import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.StatutMouvement;
import com.daara.internatadmin.finance.recette.dto.AutreRecetteResponse;
import com.daara.internatadmin.finance.recette.dto.CreateAutreRecetteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AutreRecetteService {

    private final AutreRecetteRepository autreRecetteRepository;



    @Audited(action = "CREATE", entityType = "AutreRecette")
    @Transactional
    public AutreRecetteResponse create(CreateAutreRecetteRequest dto, Long currentUserId, String currentUsername) {
        LocalDate recetteDate = (dto.recetteDate() != null) ? dto.recetteDate() : LocalDate.now();

        if (!dto.confirmDuplicate()) {
            boolean possibleDuplicate = autreRecetteRepository.existsBySourceAndAmountAndRecetteDateAndRegisteredByUserIdAndCreatedAtAfter(
                    dto.source(), dto.amount(), recetteDate, currentUserId, LocalDateTime.now().minusMinutes(2)
            );
            if (possibleDuplicate) {
                throw new DuplicateEntityException(
                        "Une recette identique (même source, montant et date) vient d'être enregistrée il y a moins de 2 minutes. " +
                                "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true."
                );
            }
        }

        AutreRecette recette = new AutreRecette();
        recette.setSource(dto.source());
        recette.setAmount(dto.amount());
        recette.setDescription(dto.description());
        recette.setRegisteredByUserId(currentUserId);
        recette.setRegisteredByUsername(currentUsername);
        recette.setRecetteDate(recetteDate);

        autreRecetteRepository.save(recette);
        return toResponse(recette);
    }
    @Audited(action = "CANCEL", entityType = "AutreRecette")
    @Transactional
    public AutreRecetteResponse cancel(Long id) {
        AutreRecette recette = autreRecetteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recette introuvable : " + id));

        if (recette.getStatus() == StatutMouvement.ANNULE) {
            throw new IllegalArgumentException("Cette recette est déjà annulée");
        }

        recette.setStatus(StatutMouvement.ANNULE);
        return toResponse(recette);
    }

    public List<AutreRecetteResponse> list(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        return autreRecetteRepository.findByStatusAndRecetteDateBetween(StatutMouvement.VALIDE, start, end)
                .stream().map(this::toResponse).toList();
    }

    private AutreRecetteResponse toResponse(AutreRecette r) {
        return new AutreRecetteResponse(
                r.getId(), r.getSource(), r.getAmount(), r.getRecetteDate(),
                r.getDescription(), r.getRegisteredByUsername(), r.getStatus()
        );
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "AutreRecette")
    @Transactional
    public PrintActionResponse recordReceiptPrint(Long id) {
        AutreRecette recette = autreRecetteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recette introuvable : " + id));
        return new PrintActionResponse(recette.getId(), "Impression du reçu enregistrée pour la recette #" + recette.getId());
    }

    public AutreRecetteResponse getReceiptData(Long id) {
        AutreRecette recette = autreRecetteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recette introuvable : " + id));
        return toResponse(recette);
    }
}