package com.daara.internatadmin.paiement.enfant;

import com.daara.internatadmin.common.error.AmountMismatchException;
import com.daara.internatadmin.common.error.PartialPaymentException;
import com.daara.internatadmin.common.error.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.PaiementLocks;
import com.daara.internatadmin.paiement.StatutPaiement;
import com.daara.internatadmin.paiement.client.EnfantView;
import com.daara.internatadmin.paiement.enfant.dto.CreatePaiementEnfantRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Regles d'enregistrement d'un paiement d'eleve, reprises du monolithe, dans une seule transaction.
 * Les erreurs metier sont des 409 que le formulaire du frontend interprete :
 * <ul>
 *   <li>mensualite deja soldee ou montant superieur au restant du : AMOUNT_MISMATCH ;</li>
 *   <li>montant inferieur au restant du sans confirmPartial : PARTIAL_PAYMENT_NOT_CONFIRMED ;</li>
 *   <li>inscription d'un montant different des frais : AMOUNT_MISMATCH ;</li>
 *   <li>inscription deja payee : PAYMENT_ALREADY_EXISTS.</li>
 * </ul>
 */
@Component
public class PaiementEnfantRecorder {

    private final PaiementEnfantRepository repository;
    private final PaiementLocks locks;

    public PaiementEnfantRecorder(PaiementEnfantRepository repository, PaiementLocks locks) {
        this.repository = repository;
        this.locks = locks;
    }

    @Transactional
    public PaiementEnfant record(CreatePaiementEnfantRequest dto, EnfantView enfant, CurrentUser user) {
        PaiementEnfant paiement = new PaiementEnfant();

        if (dto.paymentType() == TypePaiementEnfant.MENSUALITE) {
            applyMensualiteRules(dto, enfant, paiement);
        } else {
            // inscription : montant strictement fixe, aucune derogation possible
            if (dto.amount().compareTo(enfant.registrationFee()) != 0) {
                throw new AmountMismatchException("Le montant des frais d'inscription doit être exactement "
                        + enfant.registrationFee() + ". Aucune dérogation n'est autorisée.");
            }
        }

        paiement.setEnfantId(enfant.id());
        paiement.setEnfantMatricule(enfant.matricule());
        paiement.setEnfantFullName(enfant.fullName());
        paiement.setGuardianName(enfant.guardianName());
        paiement.setRegisteredByUserId(user.id());
        paiement.setRegisteredByUsername(user.username());
        paiement.setPaymentType(dto.paymentType());
        paiement.setPaymentMethod(dto.paymentMethod());
        paiement.setAmount(dto.amount());
        paiement.setExcessAmount(BigDecimal.ZERO);
        if (dto.paymentDate() != null) {
            paiement.setPaymentDate(dto.paymentDate());
        }

        try {
            return repository.saveAndFlush(paiement);
        } catch (DataIntegrityViolationException e) {
            // seul index unique : une inscription valide par eleve
            throw new PaymentAlreadyExistsException("Les frais d'inscription ont déjà été enregistrés pour cet enfant");
        }
    }

    private void applyMensualiteRules(CreatePaiementEnfantRequest dto, EnfantView enfant, PaiementEnfant paiement) {
        if (dto.concernedYear() == null || dto.concernedMonth() == null) {
            throw new IllegalArgumentException("L'année et le mois concernés sont requis pour une mensualité");
        }
        LocalDate concernedMonth = LocalDate.of(dto.concernedYear(), dto.concernedMonth(), 1);

        // serialise les paiements simultanes de cette mensualite jusqu'au commit
        locks.lockMensualite(enfant.id(), concernedMonth);

        List<PaiementEnfant> existing = repository.findByEnfantIdAndPaymentTypeAndConcernedMonthAndStatus(
                enfant.id(), TypePaiementEnfant.MENSUALITE, concernedMonth, StatutPaiement.VALIDE);

        BigDecimal alreadyPaid = existing.stream().map(PaiementEnfant::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedAmount = existing.stream()
                .map(PaiementEnfant::getExpectedAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(enfant.monthlyFee());
        BigDecimal remainingBefore = expectedAmount.subtract(alreadyPaid);

        if (remainingBefore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AmountMismatchException("La mensualité de " + dto.concernedMonth() + "/" + dto.concernedYear()
                    + " est déjà entièrement payée pour cet enfant");
        }

        int comparison = dto.amount().compareTo(remainingBefore);
        if (comparison > 0) {
            throw new AmountMismatchException("Le montant saisi (" + dto.amount() + ") dépasse le solde restant dû ("
                    + remainingBefore + "). Aucun dépassement n'est autorisé.");
        }
        if (comparison < 0 && !dto.confirmPartial()) {
            throw new PartialPaymentException("Le montant saisi (" + dto.amount() + ") est inférieur au solde restant dû ("
                    + remainingBefore + "). Si c'est un paiement partiel intentionnel, renvoyez avec confirmPartial=true.");
        }

        BigDecimal balanceAfter = remainingBefore.subtract(dto.amount());
        paiement.setConcernedMonth(concernedMonth);
        paiement.setExpectedAmount(expectedAmount);
        paiement.setBalanceAfter(balanceAfter);
        paiement.setIsPartial(balanceAfter.compareTo(BigDecimal.ZERO) > 0);
    }
}
