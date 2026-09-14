package com.beautysalonapp.modules.reporting.web;

import com.beautysalonapp.modules.reporting.application.ReportService;
import com.beautysalonapp.modules.reporting.application.ReportService.DailyDashboard;
import com.beautysalonapp.modules.reporting.application.ReportService.LostCustomerRow;
import com.beautysalonapp.security.application.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ReportService reports;
    private final UserService userService;

    public DashboardController(ReportService reports, UserService userService) {
        this.reports = reports;
        this.userService = userService;
    }

    /**
     * {@code branchId} verilmezse tüm şubeler (Faz 8 merkezi görünüm, bkz. docs/adr/0006) —
     * kullanıcı belirli şube(ler)e kısıtlanmışsa (§B5) bu, kendi şubesi(leri) olur ve
     * kısıtlı olmadığı bir şube istenirse 403 döner (bkz. {@code UserService.resolveRequestedBranch}).
     *
     * <p>Ciro, tahsilat, gider, ödeme türü dağılımı ve taksit/çek uyarılarını döndürür —
     * mali özet niteliğindedir; bu yüzden {@code /end-of-day} ile aynı {@code REPORTING_VIEW}
     * yetkisine bağlıdır (aksi halde her oturum açan kullanıcı işletmenin günlük mali
     * tablosunu görebilir).
     */
    @GetMapping("/today")
    @PreAuthorize("hasAuthority('REPORTING_VIEW')")
    public DailyDashboard today(@RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.today(userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/end-of-day")
    @PreAuthorize("hasAuthority('REPORTING_VIEW')")
    public String endOfDay() {
        return reports.endOfDaySummary();
    }

    /** Kayıp müşteri raporu (plan §10.3) — en az {@code idleDays} gündür gelmeyen/almayan müşteriler. */
    @GetMapping("/reports/lost-customers")
    @PreAuthorize("hasAuthority('PARTY_REPORT')")
    public List<LostCustomerRow> lostCustomers(@RequestParam(defaultValue = "90") int idleDays,
                                               @RequestParam(required = false) Long branchId,
                                               Authentication auth) {
        return reports.lostCustomers(idleDays, userService.resolveRequestedBranch(auth.getName(), branchId));
    }
}
