package com.iby.invoice_reconciliation.service;

import com.iby.invoice_reconciliation.dto.OutputInvoice;
import com.iby.invoice_reconciliation.enitity.InputInvoice;
import com.iby.invoice_reconciliation.enitity.ReferenceInvoice;
import com.iby.invoice_reconciliation.repository.ReferenceInvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CheckInvoice {
    @Autowired
    private ReferenceInvoiceRepository referenceRepository;
    public OutputInvoice checkInvoice(InputInvoice input) {
        ReferenceInvoice reference = referenceRepository.findById(input.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("No reference record found for invoice: " + input.getInvoiceId()));

        boolean amountMatches = input.getAmount() == reference.getAmount();
        boolean nameMatches = input.getCompanyName().equals(reference.getCompanyName());
        boolean discrepancyFound = !amountMatches || !nameMatches;

        String comment;
        if (discrepancyFound) {
            comment = String.format("請求書照合完了。%s　金額：%,d円。差異あり要確認。",
                    input.getInvoiceId(), input.getAmount());
        } else {
            comment = String.format("請求書照合完了。%s　金額：%,d円。差異なし承認。",
                    input.getInvoiceId(), input.getAmount());
        }
        OutputInvoice output = new OutputInvoice();
        output.setInvoiceId(input.getInvoiceId());
        output.setDiscrepancyFound(discrepancyFound);
        output.setGeneratedComment(comment);
        output.setRequiresHumanReview(discrepancyFound);

        return output;
    }

}
