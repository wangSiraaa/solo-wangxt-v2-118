import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BatchSummary, Claim, TrialRequest, TrialResponse } from '../models/netting.model';

@Injectable({ providedIn: 'root' })
export class NettingService {
  private readonly apiBase = '/api';

  constructor(private readonly http: HttpClient) {
  }

  getClaims(): Observable<Claim[]> {
    return this.http.get<Claim[]>(`${this.apiBase}/claims`);
  }

  getBatches(): Observable<BatchSummary[]> {
    return this.http.get<BatchSummary[]>(`${this.apiBase}/batches`);
  }

  getBatch(batchId: string): Observable<TrialResponse> {
    return this.http.get<TrialResponse>(`${this.apiBase}/batches/${batchId}`);
  }

  runTrial(request: TrialRequest): Observable<TrialResponse> {
    return this.http.post<TrialResponse>(`${this.apiBase}/trials`, request);
  }

  confirm(batchId: string): Observable<TrialResponse> {
    return this.http.post<TrialResponse>(`${this.apiBase}/batches/${batchId}/confirm`, {});
  }
}
