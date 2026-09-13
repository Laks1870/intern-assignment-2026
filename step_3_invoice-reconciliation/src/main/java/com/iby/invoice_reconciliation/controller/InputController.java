package com.iby.invoice_reconciliation.controller;

import com.iby.invoice_reconciliation.dto.OutputInvoice;
import com.iby.invoice_reconciliation.enitity.InputInvoice;
import com.iby.invoice_reconciliation.repository.InputInvoiceRepository;
import com.iby.invoice_reconciliation.service.CheckInvoice;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/send_invoice")
public class InputController {
private final InputInvoiceRepository inputInvoiceRepository;
private final CheckInvoice checkInvoice;
public InputController(InputInvoiceRepository inputInvoiceRepository, CheckInvoice checkInvoice) {
    this.inputInvoiceRepository = inputInvoiceRepository;
    this.checkInvoice = checkInvoice;
}
@PostMapping
public InputInvoice save(@RequestBody InputInvoice inputInvoice) {
    return inputInvoiceRepository.save(inputInvoice);
}
@PostMapping("/check")
public OutputInvoice check(@RequestBody InputInvoice inputInvoice) {
    return checkInvoice.checkInvoice(inputInvoice);
    }
}
