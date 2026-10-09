import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InteractionApiService } from './interaction-api.service';
import { Interaction } from './interaction.model';

@Component({
  selector: 'app-interaction-list-page',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section>
      <h2>Interactions</h2>
      <p>Customer <code>{{ customerId }}</code> · correlation <code>lab-request-001</code></p>
      @if (error()) {
        <p role="alert">{{ error() }}</p>
      }
      <form (ngSubmit)="create()">
        <label>
          Summary
          <input name="summary" [(ngModel)]="summary" required data-testid="summary" />
        </label>
        <button type="submit" data-testid="create">Record note</button>
      </form>
      <ul>
        @for (row of rows(); track row.id) {
          <li data-testid="interaction-row">{{ row.interactionType }} — {{ row.summary }} ({{ row.correlationId }})</li>
        }
      </ul>
    </section>
  `,
  styles: `
    .meta { color: var(--color-muted); margin: 0 0 1.25rem; }
    code { background: #e8ecf5; padding: 0.1rem 0.4rem; border-radius: 4px; }
    .note-form {
      display: flex;
      align-items: flex-end;
      gap: 1rem;
      padding: 1rem;
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-left: 4px solid var(--color-primary);
      border-radius: var(--radius);
    }
    .entry {
      border-left: 4px solid var(--color-blue); /* keep the other properties */
    }
    .badge {
      align-self: flex-start;
      padding: 0.15rem 0.6rem;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: var(--color-navy);
      background: #DCEBFF;
      border-radius: 999px;
    }
    .summary { margin: 0; font-weight: 600; }
    .detail { margin: 0.25rem 0 0; font-size: 0.85rem; color: var(--color-muted); }
    .empty { padding: 1rem; color: var(--color-muted); background: var(--color-surface);
      border: 1px dashed var(color-border); border-radius: var(--radius); }
  `
})
export class InteractionListPageComponent {
  private readonly api = inject(InteractionApiService);

  readonly customerId = 'CUS-1001';
  summary = 'Follow-up on billing question';
  readonly rows = signal<Interaction[]>([]);
  readonly error = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  create(): void {
    this.error.set(null);
    this.api
      .create({
        customerId: this.customerId,
        interactionType: 'NOTE',
        summary: this.summary,
        correlationId: 'lab-request-001',
      })
      .subscribe({
        next: () => this.reload(),
        error: (err) => this.error.set(`Create failed (${err.status ?? 'network'})`),
      });
  }

  private reload(): void {
    this.api.list(this.customerId).subscribe({
      next: (rows) => this.rows.set(rows),
      error: (err) => this.error.set(`List failed (${err.status ?? 'network'})`),
    });
  }
}
