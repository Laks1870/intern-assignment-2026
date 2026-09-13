package com.iby.invoice_reconciliation.dto;

public class OutputInvoice {
    private String invoiceId;
    private boolean discrepancyFound;
    private String generatedComment;
    private boolean requiresHumanReview;

    public String getInvoiceId() {
        return invoiceId;
    }
    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }
    public boolean isDiscrepancyFound() {
        return discrepancyFound;
    }
    public void setDiscrepancyFound(boolean discrepancyFound) {
        this.discrepancyFound = discrepancyFound;
    }
    public String getGeneratedComment() {
        return generatedComment;
    }
    public void setGeneratedComment(String generatedComment) {
        this.generatedComment = generatedComment;
    }
    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }
    public void setRequiresHumanReview(boolean requiresHumanReview) {
        this.requiresHumanReview = requiresHumanReview;
    }
}
