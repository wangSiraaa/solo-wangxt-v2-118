export type ClaimStatus = 'OPEN' | 'NETTED' | 'SETTLED';
export type BatchStatus = 'TRIAL' | 'CONFIRMED' | 'CANCELLED';
export type AllocationType = 'MUTUAL_OFFSET' | 'SETTLEMENT' | 'SETTLEMENT_CHAIN';
export type ResidualBearer = 'PAYER' | 'RECEIVER';
export type ExclusionReason =
  | 'MISSING_AGREEMENT'
  | 'AGREEMENT_SCOPE_FILTERED'
  | 'CURRENCY_FILTERED'
  | 'NOT_PARTY_TO_AGREEMENT'
  | 'AGREEMENT_NOT_IN_EFFECT'
  | 'PLEDGED'
  | 'DISPUTED'
  | 'NOT_OPEN';

export interface EntityRef {
  id: number;
  code: string;
  name: string;
}

export interface AgreementRef {
  id: number;
  code: string;
  name: string;
}

export interface Claim {
  id: number;
  invoiceNumber: string;
  creditor: EntityRef;
  debtor: EntityRef;
  agreement: AgreementRef | null;
  amount: number;
  currency: string;
  invoiceDate: string;
  dueDate: string;
  status: ClaimStatus;
  pledged: boolean;
  disputed: boolean;
  description?: string;
}

export interface FxRateInput {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
}

export interface TrialRequest {
  agreementCode?: string | null;
  currency?: string | null;
  targetCurrency?: string | null;
  fxRateTime?: string | null;
  residualBearer: ResidualBearer;
  fxRates: FxRateInput[];
}

export interface FxResult {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  rateTime: string;
  source: string;
  convertedAmount: number;
  residualAmount: number;
  residualBearer: ResidualBearer;
  residualEntity: EntityRef;
}

export interface TraceItem {
  invoiceNumber: string;
  creditor: EntityRef;
  debtor: EntityRef;
  originalAmount: number;
  allocatedAmount: number;
  allocationType: AllocationType;
  settlementSequenceNo?: number | null;
}

export interface Position {
  entity: EntityRef;
  payable: number;
  receivable: number;
  netPosition: number;
}

export interface Settlement {
  sequenceNo: number;
  payer: EntityRef;
  receiver: EntityRef;
  amount: number;
  currency: string;
  fx: FxResult | null;
  trace: TraceItem[];
}

export interface GroupResult {
  agreementCode: string;
  agreementName: string;
  currency: string;
  grossPaymentCount: number;
  residualPaymentCount: number;
  grossAmount: number;
  mutualOffsetAmount: number;
  residualAmount: number;
  positions: Position[];
  settlements: Settlement[];
  trace: TraceItem[];
}

export interface BalanceCheck {
  agreementCode: string;
  currency: string;
  entity: EntityRef;
  originalNetPosition: number;
  proposedNetPosition: number;
  difference: number;
}

export interface CurrencyTotal {
  currency: string;
  grossAmount: number;
  residualAmount: number;
  mutualOffsetAmount: number;
}

export interface ExcludedClaim {
  claim: Claim;
  reasons: ExclusionReason[];
}

export interface TrialResponse {
  batchId: string;
  batchRef: string;
  status: BatchStatus;
  targetCurrency?: string | null;
  residualBearer: ResidualBearer;
  fxRateTime?: string | null;
  calculatedAt: string;
  confirmedAt?: string | null;
  grossPaymentCount: number;
  residualPaymentCount: number;
  eliminatedPaymentCount: number;
  amountTotals: CurrencyTotal[];
  eligibleClaims: Claim[];
  excludedClaims: ExcludedClaim[];
  groups: GroupResult[];
  balanceChecks: BalanceCheck[];
  inputSignature: string;
}

export interface BatchSummary {
  batchId: string;
  batchRef: string;
  status: BatchStatus;
  targetCurrency?: string | null;
  residualBearer: ResidualBearer;
  grossPaymentCount: number;
  residualPaymentCount: number;
  eliminatedPaymentCount: number;
  calculatedAt: string;
  confirmedAt?: string | null;
}
