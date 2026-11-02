package com.daara.internatadmin.finance.tresorerie;

import com.daara.internatadmin.finance.StatutMouvement;
import com.daara.internatadmin.finance.depense.DepenseRepository;
import com.daara.internatadmin.finance.paiementenfant.PaiementEnfantRepository;
import com.daara.internatadmin.finance.paiementenfant.StatutPaiement;
import com.daara.internatadmin.finance.paiementsalaire.PaiementSalaireRepository;
import com.daara.internatadmin.finance.paiementsalaire.StatutPaiementSalaire;
import com.daara.internatadmin.finance.recette.AutreRecetteRepository;
import com.daara.internatadmin.finance.tresorerie.dto.SoldeGlobalResponse;
import com.daara.internatadmin.finance.tresorerie.dto.TresorerieResponse;
import com.daara.internatadmin.finance.tresorerie.dto.TresorerieResponse.LigneDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TresorerieService {

    private final PaiementEnfantRepository paiementEnfantRepository;
    private final AutreRecetteRepository autreRecetteRepository;
    private final PaiementSalaireRepository paiementSalaireRepository;
    private final DepenseRepository depenseRepository;

    public TresorerieResponse getTresorerie(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        // --- Recettes ---
        BigDecimal totalPaiementsEnfants = paiementEnfantRepository
                .findByPaymentDateBetweenAndStatus(start, end, StatutPaiement.VALIDE)
                .stream().map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAutresRecettes = autreRecetteRepository
                .findByStatusAndRecetteDateBetween(StatutMouvement.VALIDE, start, end)
                .stream().map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LigneDetail> detailRecettes = List.of(
                new LigneDetail("Paiements des enfants (inscriptions + mensualités)", totalPaiementsEnfants),
                new LigneDetail("Autres recettes (dons, subventions)", totalAutresRecettes)
        );

        BigDecimal totalRecettes = totalPaiementsEnfants.add(totalAutresRecettes);

        // --- Dépenses ---
        BigDecimal totalSalaires = paiementSalaireRepository
                .findByConcernedMonthAndStatus(start, StatutPaiementSalaire.VALIDE)
                .stream().map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<com.daara.internatadmin.finance.depense.Depense> depenses = depenseRepository
                .findByStatusAndExpenseDateBetween(StatutMouvement.VALIDE, start, end);

        Map<String, BigDecimal> depensesParCategorie = depenses.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getCategorie().getName(),
                        Collectors.reducing(BigDecimal.ZERO, com.daara.internatadmin.finance.depense.Depense::getAmount, BigDecimal::add)
                ));

        BigDecimal totalDepensesCourantes = depenses.stream()
                .map(d -> d.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LigneDetail> detailDepenses = new java.util.ArrayList<>();
        detailDepenses.add(new LigneDetail("Salaires du personnel", totalSalaires));
        depensesParCategorie.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> detailDepenses.add(new LigneDetail(e.getKey(), e.getValue())));

        BigDecimal totalDepenses = totalSalaires.add(totalDepensesCourantes);

        return new TresorerieResponse(
                year, month, totalRecettes, totalDepenses, totalRecettes.subtract(totalDepenses),
                detailRecettes, detailDepenses
        );
    }

    public SoldeGlobalResponse getSoldeGlobal() {
        BigDecimal totalRecettes = paiementEnfantRepository.findByStatus(StatutPaiement.VALIDE).stream()
                .map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(autreRecetteRepository.findByStatus(StatutMouvement.VALIDE).stream()
                        .map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal totalDepenses = paiementSalaireRepository.findByStatus(StatutPaiementSalaire.VALIDE).stream()
                .map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(depenseRepository.findByStatus(StatutMouvement.VALIDE).stream()
                        .map(d -> d.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));

        return new SoldeGlobalResponse(totalRecettes, totalDepenses, totalRecettes.subtract(totalDepenses));
    }
}