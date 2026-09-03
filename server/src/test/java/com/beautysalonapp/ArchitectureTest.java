package com.beautysalonapp;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Mimari kuralları (CLAUDE.md #2, #5). Testi zayıflatarak geçmeye çalışmayın.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.beautysalonapp");
    }

    @Test
    void para_alanlari_double_veya_float_olmaz() {
        ArchRule rule = fields()
                .that().areNotStatic()
                .should().notHaveRawType(double.class)
                .andShould().notHaveRawType(float.class)
                .andShould().notHaveRawType(Double.class)
                .andShould().notHaveRawType(Float.class)
                .because("Para ve ölçüm alanlarında BigDecimal kullanılır (CLAUDE.md #2)");
        rule.check(classes);
    }

    @Test
    void metotlar_double_veya_float_dondurmez_veya_parametre_almaz() {
        ArchRule rule = methods()
                .should().notHaveRawReturnType(double.class)
                .andShould().notHaveRawReturnType(float.class)
                .because("Mali hesaplamalar BigDecimal ile yapılır (CLAUDE.md #2)");
        rule.check(classes);
    }

    @Test
    void domain_katmani_spring_bilesenlerine_baglanmaz() {
        // Not: BaseEntity, Spring Data'nın saf metadata auditing anotasyonlarını
        // (@CreatedBy/@CreatedDate) kullanır — bu bilinçli bir taviz (bkz. docs/adr/0003).
        // Asıl kural: domain'de bileşen/context/web/boot bağımlılığı OLMAZ.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.stereotype..",
                        "org.springframework.context..",
                        "org.springframework.beans..",
                        "org.springframework.web..",
                        "org.springframework.boot..",
                        "org.springframework.transaction..")
                .because("Domain saf iş kurallarıdır; framework bağımlılığı application/infrastructure'da olur");
        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    void web_katmani_yalnizca_disaridan_cagrilir() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..web..")
                .because("Controller'lar dışa dönük uçtur; domain onlara bağlı olamaz");
        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    void disari_http_yalnizca_guarded_client_uzerinden() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName("com.beautysalonapp.outbound.GuardedRestClient")
                .and().doNotHaveFullyQualifiedName("com.beautysalonapp.outbound.OutboundHttpGuard")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestClient")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.reactive.function.client.WebClient")
                .because("Giden HTTP OutboundHttpGuard allowlist'inden geçmeli (§2.1)");
        rule.allowEmptyShould(true).check(classes);
    }
}
