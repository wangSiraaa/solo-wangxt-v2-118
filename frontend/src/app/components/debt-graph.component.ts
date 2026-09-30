import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Claim, EntityRef, TraceItem, TrialResponse } from '../models/netting.model';

interface NodePlacement {
  entity: EntityRef;
  x: number;
  y: number;
}

interface EdgePlacement {
  claim: Claim;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  mx: number;
  my: number;
  curve: number;
  color: string;
  width: number;
  dash: string | null;
  selected: boolean;
  related: boolean;
  settlementSequences: number[];
}

@Component({
  selector: 'app-debt-graph',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="graph-wrap">
      <svg *ngIf="nodes.length" [attr.width]="width" [attr.height]="height" role="img"
           aria-label="法人债务图">
        <defs>
          <marker *ngFor="let color of colors" [attr.id]="'arrow-' + color" markerWidth="10" markerHeight="10"
                  refX="9" refY="3" orient="auto-start-reverse" markerUnits="strokeWidth">
            <path d="M0,0 L0,6 L9,3 z" [attr.fill]="color"></path>
          </marker>
        </defs>

        <g *ngFor="let edge of edges">
          <path [attr.d]="edgePath(edge)" [attr.stroke]="edge.color" [attr.stroke-width]="edge.width"
                fill="none" [attr.stroke-dasharray]="edge.dash"
                [attr.marker-end]="'url(#arrow-' + markerColor(edge.color) + ')'"
                [style.cursor]="'pointer'" (click)="selectInvoice(edge.claim.invoiceNumber)">
            <title>{{ edgeTitle(edge) }}</title>
          </path>
          <text [attr.x]="edge.mx" [attr.y]="edge.my" class="edge-label" text-anchor="middle">
            {{ edge.claim.invoiceNumber }} · {{ edge.claim.amount }} {{ edge.claim.currency }}
          </text>
        </g>

        <g *ngFor="let node of nodes">
          <circle [attr.cx]="node.x" [attr.cy]="node.y" r="34" fill="#ffffff" stroke="#2563eb" stroke-width="3"></circle>
          <text [attr.x]="node.x" [attr.y]="node.y - 2" text-anchor="middle" font-weight="800">
            {{ node.entity.code }}
          </text>
          <text [attr.x]="node.x" [attr.y]="node.y + 15" text-anchor="middle" font-size="11" fill="#64748b">
            {{ node.entity.name }}
          </text>
        </g>
      </svg>
    </div>
  `
})
export class DebtGraphComponent implements OnChanges {
  @Input({ required: true }) claims: Claim[] = [];
  @Input() result: TrialResponse | null = null;
  @Input() selectedInvoice = '';
  @Output() selectedInvoiceChange = new EventEmitter<string>();

  readonly width = 980;
  readonly height = 620;
  readonly colors = ['#059669', '#2563eb', '#dc2626', '#7c3aed'];

  nodes: NodePlacement[] = [];
  edges: EdgePlacement[] = [];

  ngOnChanges(): void {
    this.buildGraph();
  }

  edgePath(edge: EdgePlacement): string {
    return `M ${edge.x1} ${edge.y1} Q ${edge.mx + edge.curve} ${edge.my - edge.curve} ${edge.x2} ${edge.y2}`;
  }

  markerColor(color: string): string {
    return color.replace('#', '');
  }

  edgeTitle(edge: EdgePlacement): string {
    const seq = edge.settlementSequences.length
      ? `；模拟付款 ${edge.settlementSequences.join(',')}`
      : '；互抵';
    return `${edge.claim.invoiceNumber}: ${edge.claim.debtor.code} → ${edge.claim.creditor.code}, `
      + `${edge.claim.amount} ${edge.claim.currency}${seq}`;
  }

  selectInvoice(invoice: string): void {
    this.selectedInvoiceChange.emit(this.selectedInvoice === invoice ? '' : invoice);
  }

  private buildGraph(): void {
    const entities = new Map<number, EntityRef>();
    for (const claim of this.claims) {
      entities.set(claim.debtor.id, claim.debtor);
      entities.set(claim.creditor.id, claim.creditor);
    }
    const entityList = [...entities.values()].sort((a, b) => a.id - b.id);
    const cx = this.width / 2;
    const cy = this.height / 2;
    const radius = Math.min(this.width, this.height) * 0.34;
    this.nodes = entityList.map((entity, index) => {
      const angle = -Math.PI / 2 + (2 * Math.PI * index) / Math.max(entityList.length, 1);
      return {
        entity,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle)
      };
    });

    const nodeById = new Map(this.nodes.map(node => [node.entity.id, node]));
    const traces = this.result?.groups.flatMap(group => group.trace) ?? [];
    const eligibleInvoices = new Set(this.result?.eligibleClaims.map(claim => claim.invoiceNumber));
    this.edges = this.claims.map(claim => {
      const from = nodeById.get(claim.debtor.id)!;
      const to = nodeById.get(claim.creditor.id)!;
      const angle = Math.atan2(to.y - from.y, to.x - from.x);
      const x1 = from.x + 38 * Math.cos(angle);
      const y1 = from.y + 38 * Math.sin(angle);
      const x2 = to.x - 38 * Math.cos(angle);
      const y2 = to.y - 38 * Math.sin(angle);
      const claimTraces = traces.filter(trace => trace.invoiceNumber === claim.invoiceNumber);
      const sequences = [...new Set(claimTraces
        .map(trace => trace.settlementSequenceNo)
        .filter((seq): seq is number => typeof seq === 'number'))];
      const selected = this.selectedInvoice === claim.invoiceNumber;
      const related = this.selectedInvoice
        ? claimTraces.some(trace => this.traceSharesSettlement(trace, this.selectedInvoice, traces))
        : false;
      const isExcluded = this.result && !eligibleInvoices.has(claim.invoiceNumber);
      const hasResidual = sequences.length > 0;
      const color = isExcluded ? '#dc2626' : hasResidual ? '#2563eb' : '#059669';
      return {
        claim,
        x1,
        y1,
        x2,
        y2,
        mx: (from.x + to.x) / 2,
        my: (from.y + to.y) / 2,
        curve: this.pairCurve(claim.debtor.id, claim.creditor.id),
        color: selected || related ? '#7c3aed' : color,
        width: selected || related ? 4.5 : 2,
        dash: isExcluded ? '6 5' : null,
        selected,
        related,
        settlementSequences: sequences
      };
    });
  }

  private traceSharesSettlement(trace: TraceItem, selectedInvoice: string, allTraces: TraceItem[]): boolean {
    if (trace.settlementSequenceNo == null) {
      return false;
    }
    return allTraces.some(other => other.invoiceNumber === selectedInvoice
      && other.settlementSequenceNo === trace.settlementSequenceNo);
  }

  private pairCurve(left: number, right: number): number {
    return left > right ? 35 : -25;
  }
}
