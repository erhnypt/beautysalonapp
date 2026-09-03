package com.beautysalonapp.modules.notification;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.modules.notification.application.EmailSender;
import com.beautysalonapp.modules.notification.application.SmsProvider;
import com.beautysalonapp.modules.notification.infrastructure.HttpSmsProvider;
import com.beautysalonapp.modules.notification.infrastructure.NoOpEmailSender;
import com.beautysalonapp.modules.notification.infrastructure.NoOpSmsProvider;
import com.beautysalonapp.modules.notification.infrastructure.SmtpEmailSender;
import com.beautysalonapp.outbound.GuardedRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Kanal sağlayıcı seçimi:
 * <ul>
 *   <li>SMS: {@code beautysalonapp.notification.sms-provider=HTTP} ise {@link HttpSmsProvider}, aksi halde NoOp.</li>
 *   <li>E-posta: {@code spring.mail.host} ayarlı ise SMTP, aksi halde NoOp.</li>
 * </ul>
 */
@Configuration
public class NotificationProvidersConfig {

    @Bean
    @ConditionalOnProperty(name = "beautysalonapp.notification.sms-provider", havingValue = "HTTP")
    public SmsProvider httpSmsProvider(AppProperties props, GuardedRestClient http) {
        return new HttpSmsProvider(props, http);
    }

    @Bean
    @ConditionalOnMissingBean(SmsProvider.class)
    public SmsProvider noOpSmsProvider() {
        return new NoOpSmsProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.mail.host")
    public EmailSender smtpEmailSender(JavaMailSender mail, AppProperties props) {
        return new SmtpEmailSender(mail, props);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender noOpEmailSender() {
        return new NoOpEmailSender();
    }
}
