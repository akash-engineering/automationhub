package com.automationhub.document.service;

import java.math.BigDecimal;

public record InvoiceLine(String description, BigDecimal amount) {
}
