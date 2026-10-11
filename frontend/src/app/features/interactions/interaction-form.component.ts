import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { InteractionApiService } from './interaction-api.service';
import { Interaction } from './interaction.model';

@Component({
  selector: 'app-interaction-form',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="card" (ngSubmit)="submit()" data-testid="interaction-form">
      <label for="interaction-type">Type</label>
      <select
        id="interaction-type"
        name="interactionType"
        [ngModel]="interactionType()"
        (ngModelChange)="interactionType.set($event)"
        data-testid="interaction-type"
      >
        <option value="CALL">Call</option>
        <option value="EMAIL">Email</option>
        <option value="NOTE">Note</option>
        <option value="MEETING">Meeting</option>
      </select>

      <label for="interaction-summary">Summary</label>
      <textarea
        id="interaction-summary"
        name="summary"
        [ngModel]="summary()"
        (ngModelChange)="summary.set($event)"
        maxlength="1024"
        data-testid="interaction-summary"
      ></textarea>
      <span class="count" data-testid="interaction-summary-count">
        {{ summary().length }} / 1024
      </span>
      @if (summaryError()) {
        <p class="error" role="alert" data-testid="interaction-summary-error">Summary is required.</p>
      }
      @if (formError()) {
        <p class="error" role="alert" data-testid="interaction-form-error">{{ formError() }}</p>
      }

      <button type="submit" [disabled]="saving()" data-testid="interaction-submit">
        {{ saving() ? 'Saving…' : 'Record interaction' }}
      </button>
    </form>
  `,
  styles: `
    .card {
      display: grid;
      gap: 0.5rem;
      max-width: none;
      padding: 1rem;
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-left: 4px solid var(--color-blue);
      border-radius: var(--radius);
    }
    textarea { min-height: 6rem; resize: vertical; }
    .count { color: var(--color-muted); text-align: right; }
    .error { margin: 0; color: var(--color-error); }
    button { justify-self: start; }
  `,
})
export class InteractionFormComponent {
  private readonly api = inject(InteractionApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly customerId = input.required<string>();
  readonly saved = output<Interaction>();
  readonly interactionType = signal('NOTE');
  readonly summary = signal('');
  readonly summaryError = signal(false);
  readonly formError = signal<string | null>(null);
  readonly saving = signal(false);

  submit(): void {
    if (this.saving()) {
      return;
    }

    const summary = this.summary().trim();
    this.summaryError.set(!summary);
    this.formError.set(null);
    if (!summary) {
      return;
    }

    this.saving.set(true);
    this.api
      .create({
        customerId: this.customerId(),
        interactionType: this.interactionType(),
        summary,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (interaction) => {
          this.summary.set('');
          this.saving.set(false);
          this.saved.emit(interaction);
        },
        error: (err) => {
          if (err.status === 400) {
            this.formError.set('The interaction was not saved. Check the summary and try again.');
          } else if (err.status === 404) {
            this.formError.set('This customer no longer exists.');
          } else {
            this.formError.set(`The interaction was not saved (${err.status || 'network'}). Try again.`);
          }
          this.saving.set(false);
        },
      });
  }
}
