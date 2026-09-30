import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { NettingService } from './services/netting.service';
import { DebtGraphComponent } from './components/debt-graph.component';
import {
  AllocationType,
  BatchSummary,
  Claim,
  ExclusionReason,
  ResidualBearer,
  TrialResponse
} from './models/netting.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule, DebtGraphComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  claims: Claim[] = [];
  batches: BatchSummary[] = [];
  result: TrialResponse | null = null;
  selectedInvoice = '';
  loading = false;
  confirming = false;
  error = '';
  note = '';

  agreementCode = '';
  currency = '';
  targetCurrency = 'CNY';
  fxRateTime = this.defaultDateTimeLocal();
  residualBearer: ResidualBearer = 'PAYER';
  eurToTarget = 7.81234567895;
  usdToTarget = 7.12345678901;
  cnyToTarget = 1;
  includeFxDisplay = true;

  allClaims: Claim[] = [];

  constructor(private readonly netting: NettingService) {
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.netting.getClaims().subscribe({
      next: claims => {
        this.allClaims = claims;
        this.claims = claims;
        this.loading = false;
        this.netting.getBatches().subscribe(batches => this.batches = batches);
      },
      error: err => {
        this.loading = false;
        this.error = this.message(err);
      }
    });
  }

  runTrial(): void {
    this.loading = true;
    this.error = '';
    this.note = '';
    this.result = null;
    this.selectedInvoice = '';
    const rates = this.includeFxDisplay
      ? [
        { fromCurrency: 'EUR', toCurrency: this.targetCurrency || 'CNY', rate: this.eurToTarget },
        { fromCurrency: 'USD', toCurrency: this.targetCurrency || 'CNY', rate: this.usdToTarget },
        { fromCurrency: 'CNY', toCurrency: this.targetCurrency || 'CNY', rate: this.cnyToTarget }
      ].filter(rate => rate.fromCurrency !== rate.toCurrency)
      : [];

    this.netting.runTrial({
      agreementCode: this.agreementCode || null,
      currency: this.currency || null,
      targetCurrency: this.includeFxDisplay ? this.targetCurrency : null,
      fxRateTime: new Date(this.fxRateTime).toISOString(),
      residualBearer: this.residualBearer,
      fxRates: rates
    }).subscribe({
      next: result => {
        this.result = result;
        this.claims = this.visibleClaims();
        this.loading = false;
        this.refreshBatches();
      },
      error: err => {
        this.loading = false;
        this.error = this.message(err);
      }
    });
  }

  confirm(): void {
    if (!this.result || this.result.status !== 'TRIAL') {
      return;
    }
    this.confirming = true;
    this.error = '';
    this.netting.confirm(this.result.batchId).subscribe({
      next: result => {
        this.result = result;
        this.claims = this.visibleClaims();
        this.confirming = false;
        this.note = '方案已确认；原始债权已锁定为 NETTED，页面仍只生成模拟付款指令，不接入真实银行。';
        this.reload();
      },
      error: err => {
        this.confirming = false;
        this.error = this.message(err);
      }
    });
  }

  loadBatch(batchId: string): void {
    this.loading = true;
    this.error = '';
    this.netting.getBatch(batchId).subscribe({
      next: result => {
        this.result = result;
        this.claims = this.visibleClaims();
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.error = this.message(err);
      }
    });
  }

  selectInvoice(invoice: string): void {
    this.selectedInvoice = this.selectedInvoice === invoice ? '' : invoice;
  }

  visibleClaims(): Claim[] {
    if (!this.result) {
      return this.allClaims;
    }
    const eligible = new Set(this.result.eligibleClaims.map(claim => claim.invoiceNumber));
    return this.allClaims.filter(claim => this.result?.excludedClaims.some(item => item.claim.id === claim.id)
      || eligible.has(claim.invoiceNumber));
  }

  selectedClaim(): Claim | undefined {
    return this.allClaims.find(claim => claim.invoiceNumber === this.selectedInvoice);
  }

  selectedTraces() {
    return this.result?.groups.flatMap(group => group.trace
      .map(trace => ({ group, trace }))
      .filter(item => item.trace.invoiceNumber === this.selectedInvoice)) ?? [];
  }

  selectedSettlements() {
    const sequences = new Set(this.selectedTraces()
      .map(item => item.trace.settlementSequenceNo)
      .filter((seq): seq is number => typeof seq === 'number'));
    return this.result?.groups.flatMap(group => group.settlements
      .filter(settlement => sequences.has(settlement.sequenceNo))) ?? [];
  }

  relatedInvoices(): string[] {
    const selectedSettlements = this.selectedSettlements();
    const sequences = new Set(selectedSettlements.map(item => item.sequenceNo));
    return [...new Set((this.result?.groups.flatMap(group => group.trace) ?? [])
      .filter(trace => trace.settlementSequenceNo && sequences.has(trace.settlementSequenceNo)
        && trace.invoiceNumber !== this.selectedInvoice)
      .map(trace => trace.invoiceNumber))];
  }

  exclusionText(reason: ExclusionReason): string {
    const map: Record<ExclusionReason, string> = {
      MISSING_AGREEMENT: '无互抵协议',
      AGREEMENT_SCOPE_FILTERED: '不在选择的协议范围',
      CURRENCY_FILTERED: '不在选择的币种范围',
      NOT_PARTY_TO_AGREEMENT: '法人不是协议方',
      AGREEMENT_NOT_IN_EFFECT: '协议在选择时点未生效',
      PLEDGED: '债权已质押',
      DISPUTED: '债权存在争议',
      NOT_OPEN: '债权已非 OPEN'
    };
    return map[reason];
  }

  allocationText(type: AllocationType): string {
    return {
      MUTUAL_OFFSET: '互抵抵销',
      SETTLEMENT: '模拟付款直连发票',
      SETTLEMENT_CHAIN: '模拟付款链式追踪'
    }[type];
  }

  statusClass(status: string): string {
    return status === 'TRIAL' ? 'trial' : 'confirmed';
  }

  isEligible(claim: Claim): boolean {
    return !!this.result?.eligibleClaims.some(item => item.id === claim.id);
  }

  exclusionFor(claim: Claim) {
    return this.result?.excludedClaims.find(item => item.claim.id === claim.id);
  }

  netClass(value: number): string {
    return value > 0 ? 'positive' : value < 0 ? 'negative' : 'muted';
  }

  refreshBatches(): void {
    this.netting.getBatches().subscribe(batches => this.batches = batches);
  }

  private message(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error: unknown }).error;
      if (typeof body === 'object' && body !== null && 'message' in body) {
        return String((body as { message: unknown }).message);
      }
    }
    return '请求失败，请确认 Spring Boot 与 PostgreSQL 已启动。';
  }

  private defaultDateTimeLocal(): string {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 16);
  }
}
