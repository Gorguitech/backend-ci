package com.daara.internatadmin.dashboard;

import com.daara.internatadmin.dashboard.dto.GlobalDashboardResponse;
import com.daara.internatadmin.enfant.EnfantRepository;
import com.daara.internatadmin.enfant.StatutEnfant;
import com.daara.internatadmin.finance.paiementenfant.PaiementEnfantService;
import com.daara.internatadmin.finance.tresorerie.TresorerieService;
import com.daara.internatadmin.finance.tresorerie.dto.SoldeGlobalResponse;
import com.daara.internatadmin.personnel.EmployeRepository;
import com.daara.internatadmin.personnel.StatutEmploye;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EnfantRepository enfantRepository;
    private final EmployeRepository employeRepository;
    private final TresorerieService tresorerieService;
    private final PaiementEnfantService paiementEnfantService;

    public GlobalDashboardResponse getGlobalDashboard() {
        long enfantsActifs = enfantRepository.findByStatus(StatutEnfant.ACTIF).size();
        long employesActifs = employeRepository.findByStatus(StatutEmploye.ACTIF).size();

        SoldeGlobalResponse solde = tresorerieService.getSoldeGlobal();

        LocalDate now = LocalDate.now();
        long impayes = paiementEnfantService.getSuiviMensuel(now.getYear(), now.getMonthValue(), "ALL").stream()
                .filter(s -> !s.statutPaiement().equals("PAYE"))
                .count();

        return new GlobalDashboardResponse(
                enfantsActifs, employesActifs,
                solde.totalRecettes(), solde.totalDepenses(), solde.solde(),
                impayes
        );
    }
}