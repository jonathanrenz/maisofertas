package com.maisofertas.ai;

import java.util.List;

/**
 * Conteúdo estruturado gerado (via IA ou fallback determinístico) para um
 * deal. Preço, desconto e link ficam de fora de propósito: esses dados já
 * existem no {@link com.maisofertas.deals.Deal} e são formatados de forma
 * determinística por canal, para nunca depender da IA acertar um número.
 */
public record DealContent(String hook, String productName, List<String> specs) {
}
