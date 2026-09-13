package com.iby.invoice_reconciliation.enitity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class InputInvoice {
    @Id
    String invoiceId;
    long amount;
    String companyName;

    public String getInvoiceId() {
        return invoiceId;
    }
    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }
    public long getAmount() {
        return amount;
    }
    public void setAmount(long amount) {
        this.amount = amount;
    }
    public String getCompanyName() {
        return companyName;
    }
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }
}
