import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { InteractionApiService } from './interaction-api.service';
import { Interaction } from './interaction.model';

@Component({
  selector: 'app-interaction-timeline-item',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <strong>{{ interaction().interactionType }}</strong>
    <p>{{ interaction().summary }}</p>
    <time>{{ interaction().createdAt | date: 'medium' }}</time>
    @if (editing()) {
      <form (ngSubmit)="save()" data-testid="interaction-edit-form">
        <label [for]="'interaction-edit-type-' + interaction().id">Type</label>
        <select
          [id]="'interaction-edit-type-' + interaction().id"
          name="interactionType"
          [ngModel]="interactionType()"
          (ngModelChange)="interactionType.set($event)"
          [disabled]="saving()"
          data-testid="interaction-edit-type"
        >
          <option value="CALL">Call</option>
          <option value="EMAIL">Email</option>
          <option value="NOTE">Note</option>
          <option value="MEETING">Meeting</option>
        </select>
        <label [for]="'interaction-edit-summary-' + interaction().id">Summary</label>
        <textarea
          [id]="'interaction-edit-summary-' + interaction().id"
          name="summary"
          [ngModel]="summary()"
          (ngModelChange)="summary.set($event)"
          [disabled]="saving()"
          [attr.aria-invalid]="summaryError() ? true : null"
          [attr.aria-describedby]="summaryError() ? 'interaction-edit-error-' + interaction().id : null"
          maxlength="1024"
          data-testid="interaction-edit-summary"
        ></textarea>
        <span class="count">{{ summary().length }} / 1024</span>
        @if (summaryError()) {
          <p class="error" role="alert" [id]="'interaction-edit-error-' + interaction().id"
            data-testid="interaction-edit-summary-error">{{ summaryError() }}</p>
        }
        <div class="actions">
          <button type="submit" [disabled]="saving() || conflict() || missing() || !canEdit()" data-testid="interaction-edit-save">
            {{ saving() ? 'Saving…' : 'Save changes' }}
          </button>
          <button type="button" [disabled]="saving()" (click)="cancel()" data-testid="interaction-edit-cancel">Cancel</button>
        </div>
      </form>
    } @else {
      <div class="actions">
        @if (canEdit()) {
          <button type="button" [disabled]="deleting() || conflict() || missing()" (click)="edit()" data-testid="interaction-edit">Edit</button>
        }
        @if (canDelete()) {
          <button type="button" class="delete" [disabled]="deleting() || conflict() || missing()" (click)="delete()" data-testid="interaction-delete">
            {{ deleting() ? 'Deleting…' : 'Delete' }}
          </button>
        }
      </div>
    }
    @if (mutationError()) {
      <div role="alert" data-testid="interaction-mutation-error">
        <p>{{ mutationError() }}</p>
        @if (conflict() || missing()) {
          <button type="button" (click)="reload()" data-testid="interaction-reload">Reload interactions</button>
        }
      </div>
    }
  `,
  styleUrl: './interaction-timeline-item.component.scss',
})
export class InteractionTimelineItemComponent {
  private readonly api = inject(InteractionApiService);
  private readonly session = inject(AuthSessionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly interaction = input.required<Interaction>();
  readonly changed = output<void>();
  readonly canDelete = computed(() => this.session.roles().includes('ADMIN'));
  readonly canEdit = computed(() => this.canDelete() || this.session.roles().includes('AGENT'));
  readonly editing = signal(false);
  readonly interactionType = signal('NOTE');
  readonly summary = signal('');
  readonly summaryError = signal<string | null>(null);
  readonly mutationError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly conflict = signal(false);
  readonly missing = signal(false);

  edit(): void {
    this.interactionType.set(this.interaction().interactionType);
    this.summary.set(this.interaction().summary);
    this.summaryError.set(null);
    this.mutationError.set(null);
    this.editing.set(true);
  }

  cancel(): void {
    this.editing.set(false);
    this.summaryError.set(null);
    if (!this.conflict() && !this.missing()) {
      this.mutationError.set(null);
    }
  }

  reload(): void {
    this.editing.set(false);
    this.conflict.set(false);
    this.missing.set(false);
    this.summaryError.set(null);
    this.mutationError.set(null);
    this.changed.emit();
  }

  save(): void {
    if (this.saving() || this.conflict() || this.missing() || !this.canEdit()) {
      return;
    }
    const summary = this.summary().trim();
    this.summaryError.set(!summary ? 'Summary is required.' : summary.length > 1024 ? 'Summary must be at most 1024 characters.' : null);
    if (this.summaryError()) {
      return;
    }
    this.mutationError.set(null);
    this.saving.set(true);
    this.api.update(this.interaction().id, {
      version: this.interaction().version,
      interactionType: this.interactionType(),
      summary,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        this.changed.emit();
      },
      error: (err) => {
        this.saving.set(false);
        this.showError(err.status, 'saved');
      },
    });
  }

  delete(): void {
    if (this.deleting() || this.conflict() || this.missing() || !this.canDelete() || !window.confirm('Delete this interaction? This cannot be undone.')) {
      return;
    }
    this.mutationError.set(null);
    this.deleting.set(true);
    this.api.delete(this.interaction().id, this.interaction().version)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          this.deleting.set(false);
          this.changed.emit();
        },
        error: (err) => {
          this.deleting.set(false);
          this.showError(err.status, 'deleted');
        },
      });
  }

  private showError(status: number, action: string): void {
    this.conflict.set(status === 409);
    this.missing.set(status === 404);
    if (status === 409) {
      this.mutationError.set('This interaction changed. Reload interactions before trying again.');
    } else if (status === 404) {
      this.mutationError.set('This interaction no longer exists. Reload interactions.');
    } else if (status === 401) {
      this.mutationError.set('Sign in again to change this interaction.');
    } else if (status === 403) {
      this.mutationError.set('You do not have permission to change this interaction.');
    } else if (status === 400) {
      this.mutationError.set(`The interaction was not ${action}. Check the type and summary and reload interactions if needed.`);
    } else {
      this.mutationError.set(`The interaction was not ${action} (${status || 'network'}). Try again.`);
    }
  }
}
