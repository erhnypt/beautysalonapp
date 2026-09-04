package com.beautysalonapp.license.web;

import com.beautysalonapp.license.domain.AppRelease;
import com.beautysalonapp.license.domain.Customer;
import com.beautysalonapp.license.domain.Enums.Plan;
import com.beautysalonapp.license.domain.Enums.TransferStatus;
import com.beautysalonapp.license.repo.CustomerRepository;
import com.beautysalonapp.license.repo.HeartbeatLogRepository;
import com.beautysalonapp.license.repo.LicenseBindingRepository;
import com.beautysalonapp.license.repo.LicenseRepository;
import com.beautysalonapp.license.repo.PaymentRecordRepository;
import com.beautysalonapp.license.repo.SubscriptionRepository;
import com.beautysalonapp.license.repo.TransferRequestRepository;
import com.beautysalonapp.license.repo.AppReleaseRepository;
import com.beautysalonapp.license.service.LicenseService;
import com.beautysalonapp.license.service.LicenseService.ProvisionCommand;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@Controller
public class AdminController {

    private final CustomerRepository customers;
    private final SubscriptionRepository subscriptions;
    private final LicenseRepository licenses;
    private final LicenseBindingRepository bindings;
    private final HeartbeatLogRepository hbLogs;
    private final TransferRequestRepository transfers;
    private final PaymentRecordRepository payments;
    private final AppReleaseRepository releases;
    private final LicenseService licenseService;

    public AdminController(CustomerRepository customers, SubscriptionRepository subscriptions,
                           LicenseRepository licenses, LicenseBindingRepository bindings,
                           HeartbeatLogRepository hbLogs, TransferRequestRepository transfers,
                           PaymentRecordRepository payments, AppReleaseRepository releases,
                           LicenseService licenseService) {
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.licenses = licenses;
        this.bindings = bindings;
        this.hbLogs = hbLogs;
        this.transfers = transfers;
        this.payments = payments;
        this.releases = releases;
        this.licenseService = licenseService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"/", "/admin"})
    public String dashboard(Model model) {
        model.addAttribute("customers", customers.findAll());
        model.addAttribute("subscriptions", subscriptions.findAll());
        model.addAttribute("licenses", licenses.findAll());
        model.addAttribute("pendingTransfers", transfers.findAllByStatus(TransferStatus.PENDING));
        model.addAttribute("releases", releases.findAll());
        model.addAttribute("plans", Plan.values());
        return "admin/dashboard";
    }

    @GetMapping("/admin/license/{licenseId}")
    public String licenseDetail(@PathVariable String licenseId, Model model) {
        var lic = licenses.findByLicenseId(licenseId).orElseThrow();
        model.addAttribute("lic", lic);
        model.addAttribute("customer", customers.findById(lic.getCustomerId()).orElse(null));
        model.addAttribute("subscription", subscriptions.findById(lic.getSubscriptionId()).orElse(null));
        model.addAttribute("bindings", bindings.findAllByLicenseId(licenseId));
        model.addAttribute("heartbeats", hbLogs.findTop20ByLicenseIdOrderByReceivedAtDesc(licenseId));
        model.addAttribute("transfers", transfers.findAllByLicenseIdOrderByRequestedAtDesc(licenseId));
        model.addAttribute("plans", Plan.values());
        return "admin/license";
    }

    // --- POST işlemleri (redirect) ---

    @PostMapping("/admin/customers")
    public String createCustomer(@RequestParam String name, @RequestParam(required = false) String taxId,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone) {
        Customer c = new Customer();
        c.setName(name);
        c.setTaxId(taxId);
        c.setEmail(email);
        c.setPhone(phone);
        customers.save(c);
        return "redirect:/admin";
    }

    @PostMapping("/admin/provision")
    public String provision(@RequestParam Long customerId, @RequestParam Plan plan,
                            @RequestParam String modules, @RequestParam(defaultValue = "1") int maxTerminals,
                            @RequestParam(defaultValue = "1") int maxBranches,
                            @RequestParam(defaultValue = "5") int maxActiveUsers,
                            @RequestParam(required = false) Integer maxCustomers,
                            @RequestParam(defaultValue = "7") int graceDays,
                            @RequestParam(defaultValue = "false") boolean offlineMode,
                            @RequestParam(defaultValue = "0") BigDecimal monthlyFee,
                            @RequestParam(defaultValue = "1") int initialMonths) {
        licenseService.provision(new ProvisionCommand(customerId, plan, modules, maxTerminals, maxBranches,
                maxActiveUsers, maxCustomers, graceDays, offlineMode, monthlyFee, initialMonths));
        return "redirect:/admin";
    }

    @PostMapping("/admin/subscription/{id}/suspend")
    public String suspend(@PathVariable long id) {
        licenseService.suspend(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/subscription/{id}/reactivate")
    public String reactivate(@PathVariable long id) {
        licenseService.reactivate(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/subscription/{id}/cancel")
    public String cancel(@PathVariable long id) {
        licenseService.cancel(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/subscription/{id}/payment")
    public String payment(@PathVariable long id, @RequestParam BigDecimal amount,
                          @RequestParam(defaultValue = "1") int months, Authentication auth) {
        licenseService.recordPayment(id, amount, months, auth.getName());
        return "redirect:/admin";
    }

    @PostMapping("/admin/license/{licenseId}/plan")
    public String updatePlan(@PathVariable String licenseId, @RequestParam Plan plan,
                             @RequestParam String modules,
                             @RequestParam(required = false) Integer maxTerminals,
                             @RequestParam(required = false) Integer maxActiveUsers) {
        licenseService.updatePlanAndModules(licenseId, plan, modules, maxTerminals, maxActiveUsers);
        return "redirect:/admin/license/" + licenseId;
    }

    @PostMapping("/admin/transfer/{id}/approve")
    public String approveTransfer(@PathVariable long id, Authentication auth) {
        licenseService.approveTransfer(id, auth.getName());
        return "redirect:/admin";
    }

    @PostMapping("/admin/transfer/{id}/reject")
    public String rejectTransfer(@PathVariable long id, Authentication auth) {
        licenseService.rejectTransfer(id, auth.getName());
        return "redirect:/admin";
    }

    @PostMapping("/admin/releases")
    public String addRelease(@RequestParam String version, @RequestParam String url,
                             @RequestParam(required = false) String checksum,
                             @RequestParam(defaultValue = "false") boolean mandatory) {
        AppRelease r = new AppRelease();
        r.setVersion(version);
        r.setUrl(url);
        r.setChecksum(checksum);
        r.setMandatory(mandatory);
        releases.save(r);
        return "redirect:/admin";
    }
}
