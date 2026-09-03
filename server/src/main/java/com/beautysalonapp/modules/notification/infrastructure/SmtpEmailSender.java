package com.beautysalonapp.modules.notification.infrastructure;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.modules.notification.application.EmailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Müşterinin kendi SMTP sunucusu üzerinden e-posta (§2.1: dışarı çıkan yalnızca alıcı + gövde). */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final AppProperties props;

    public SmtpEmailSender(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.props = props;
    }

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            String from = props.getNotification().getEmailFrom();
            if (from != null && !from.isBlank()) {
                msg.setFrom(from);
            }
            msg.setTo(to);
            msg.setSubject(subject == null ? "" : subject);
            msg.setText(body == null ? "" : body);
            mail.send(msg);
        } catch (Exception e) {
            throw new EmailException("SMTP gönderimi başarısız: " + e.getMessage(), e);
        }
    }
}
