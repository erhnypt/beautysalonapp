package com.beautysalonapp.modules.notification.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Şablon değişken yerleştirme (§9.10). Saf domain — {@code {ad}}, {@code {tarih}},
 * {@code {tutar}} gibi süslü parantezli anahtarlar değerlerle değiştirilir.
 * Bilinmeyen anahtar boş string olur; kaçış için {@code {{} } desteklenmez (basit tutuldu).
 */
public final class TemplateRenderer {

    private static final Pattern VAR = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, String> safe = vars == null ? Map.of() : vars;
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = safe.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Şablonda geçen tüm değişken anahtarları (doküman/önizleme için). */
    public static java.util.Set<String> variablesIn(String template) {
        var out = new java.util.LinkedHashSet<String>();
        if (template != null) {
            Matcher m = VAR.matcher(template);
            while (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }
}
