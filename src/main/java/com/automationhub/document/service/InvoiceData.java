package com.automationhub.document.service;

import java.util.List;

public record InvoiceData(
        String title,
        String recipient,
        String currency,
        List<InvoiceLine> lines
) {
}
