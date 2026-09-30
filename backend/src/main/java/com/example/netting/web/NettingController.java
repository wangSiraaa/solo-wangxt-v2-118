package com.example.netting.web;

import com.example.netting.dto.BatchSummaryDto;
import com.example.netting.dto.TrialRequest;
import com.example.netting.dto.TrialResponse;
import com.example.netting.service.BatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class NettingController {

    private final BatchService batchService;

    public NettingController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping("/claims")
    public List<TrialResponse.ClaimDto> claims() {
        return batchService.listClaims();
    }

    @GetMapping("/batches")
    public List<BatchSummaryDto> batches() {
        return batchService.listBatches();
    }

    @GetMapping("/batches/{batchId}")
    public TrialResponse batch(@PathVariable UUID batchId) {
        return batchService.getBatch(batchId);
    }

    @PostMapping("/trials")
    @ResponseStatus(HttpStatus.CREATED)
    public TrialResponse runTrial(@Valid @RequestBody TrialRequest request) {
        return batchService.runTrial(request);
    }

    @PostMapping("/batches/{batchId}/confirm")
    public TrialResponse confirm(@PathVariable UUID batchId) {
        return batchService.confirmBatch(batchId);
    }
}
