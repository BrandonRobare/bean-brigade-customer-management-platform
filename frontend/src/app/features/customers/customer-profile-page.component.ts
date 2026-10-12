import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { InteractionApiService } from '../interactions/interaction-api.service';
import { InteractionTimelineItemComponent } from '../interactions/interaction-timeline-item.component';
import { InteractionFormComponent } from '../interactions/interaction-form.component';
import { Interaction } from '../interactions/interaction.model';
import { FormsModule } from '@angular/forms';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { CustomerApiService } from './customer-api.service';
import { Customer } from './customer.model';

@Component({
  selector: 'app-customer-profile-page',
  standalone: true,
  imports: [FormsModule, RouterLink, InteractionFormComponent, InteractionTimelineItemComponent],
  template: `
    <a routerLink="/customers" [queryParams]="{ q: searchQuery }" data-testid="customer-profile-back">Back to search</a>

    <section class="profile">
      @if (customerLoading()) {
        <p data-testid="customer-profile-loading">Loading customer…</p>
      } @else if (customerError() === 'not-found') {
        <p data-testid="customer-profile-not-found">Customer {{ publicId() }} was not found.</p>
      } @else if (customerError() === 'invalid-id') {
        <p data-testid="customer-profile-invalid-id">Customer ID {{ publicId() }} is not valid.</p>
      } @else if (customerError() === 'error') {
        <div role="alert" data-testid="customer-profile-error">
          <p>Customer profile failed to load.</p>
          <button type="button" (click)="loadProfile()" data-testid="customer-profile-retry">Retry</button>
        </div>
      } @else if (customer()) {
        <div class="card customer-card">
          <h2 data-testid="customer-profile-name">{{ customer()!.fullName }}</h2>
          <p data-testid="customer-profile-id">{{ customer()!.publicId }}</p>
          <p data-testid="customer-profile-status">{{ customer()!.status }}</p>
          @if (canManage()) {
            @if (editing()) {
              <form (ngSubmit)="saveName()" data-testid="customer-edit-form">
                <label>
                  Customer name
                  <input name="fullName" [(ngModel)]="fullName" [disabled]="mutating()" maxlength="200" aria-describedby="customer-name-error" data-testid="customer-edit-name" />
                </label>
                @if (nameError()) {
                  <p id="customer-name-error" role="alert" data-testid="customer-name-error">{{ nameError() }}</p>
                }
                <button type="submit" [disabled]="mutating() || conflict() || missing()" data-testid="customer-edit-save">{{ mutating() ? 'Saving…' : 'Save name' }}</button>
                <button type="button" (click)="editing.set(false)" [disabled]="mutating()" data-testid="customer-edit-cancel">Cancel</button>
              </form>
            } @else {
              <button type="button" (click)="editName()" [disabled]="mutating() || conflict() || missing()" data-testid="customer-edit">Edit name</button>
            }
            @if (customer()!.status !== 'CLOSED') {
              <form (ngSubmit)="changeStatus()" data-testid="customer-status-form">
                <label>
                  New status
                  <select name="status" [(ngModel)]="status" [disabled]="mutating() || editing()" data-testid="customer-status">
                    @if (customer()!.status === 'PROSPECT') {
                      <option value="ACTIVE">ACTIVE</option>
                    }
                    <option value="CLOSED">CLOSED</option>
                  </select>
                </label>
                <button type="submit" [disabled]="mutating() || editing() || conflict() || missing()" data-testid="customer-status-submit">{{ mutating() ? 'Saving…' : 'Change status' }}</button>
              </form>
            }
            <button type="button" (click)="deleteCustomer()" [disabled]="mutating() || conflict() || missing()" data-testid="customer-delete">{{ mutating() ? 'Working…' : 'Delete customer' }}</button>
            @if (mutationError()) {
              <div role="alert" data-testid="customer-mutation-error">
                <p>{{ mutationError() }}</p>
                @if (conflict() || missing()) {
                  <button type="button" (click)="reloadCustomer()" data-testid="customer-reload">Reload customer</button>
                }
              </div>
            }
          }
        </div>

        <section class="interactions">
          <h3>Interactions</h3>
          <app-interaction-form
            [customerId]="customer()!.publicId"
            (saved)="loadInteractions()"
          />
          @if (interactionLoading()) {
            <p data-testid="interaction-timeline-loading">Loading interactions…</p>
          } @else if (interactionError()) {
            <div role="alert" data-testid="interaction-timeline-error">
              <p>Interactions failed to load.</p>
              <button type="button" (click)="loadInteractions()" data-testid="interaction-timeline-retry">Retry</button>
            </div>
          } @else if (interactions().length === 0) {
            <p data-testid="interaction-timeline-empty">No interactions yet.</p>
          } @else {
            <ol data-testid="interaction-timeline">
              @for (interaction of interactions(); track interaction.id) {
                <li class="card" data-testid="interaction-timeline-item">
                  <app-interaction-timeline-item
                    [interaction]="interaction"
                    (changed)="loadInteractions()"
                  />
                </li>
              }
            </ol>
          }
        </section>
      }
    </section>
  `,
  styleUrl: './customer-profile-page.component.scss',
})
export class CustomerProfilePageComponent {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);
  readonly canManage = computed(() => this.session.roles().includes('ADMIN'));
  readonly editing = signal(false);
  fullName = '';
  status: Customer['status'] = 'ACTIVE';
  readonly nameError = signal<string | null>(null);
  readonly mutationError = signal<string | null>(null);
  readonly mutating = signal(false);
  readonly conflict = signal(false);
  readonly missing = signal(false);
  private mutationRequest?: Subscription;
  private readonly customerApi = inject(CustomerApiService);
  private readonly interactionApi = inject(InteractionApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private customerRequest?: Subscription;
  private interactionRequest?: Subscription;

  readonly searchQuery = this.route.snapshot.queryParamMap.get('q');
  readonly publicId = signal('');
  readonly customer = signal<Customer | null>(null);
  readonly customerLoading = signal(false);
  readonly customerError = signal<'not-found' | 'invalid-id' | 'error' | null>(null);
  readonly interactions = signal<Interaction[]>([]);
  readonly interactionLoading = signal(false);
  readonly interactionError = signal(false);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.mutationRequest?.unsubscribe();
      this.mutating.set(false);
      this.editing.set(false);
      this.conflict.set(false);
      this.missing.set(false);
      this.mutationError.set(null);
      this.nameError.set(null);
      this.publicId.set(params.get('publicId') ?? '');
      this.loadProfile();
    });
  }

  loadProfile(): void {
    this.customerRequest?.unsubscribe();
    this.interactionRequest?.unsubscribe();
    this.customer.set(null);
    this.interactions.set([]);
    this.customerLoading.set(true);
    this.customerError.set(null);
    this.interactionLoading.set(false);
    this.interactionError.set(false);
    this.customerRequest = this.customerApi
      .get(this.publicId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.customer.set(customer);
          this.status = customer.status === 'PROSPECT' ? 'ACTIVE' : 'CLOSED';
          this.customerLoading.set(false);
          this.loadInteractions();
        },
        error: (err) => {
          this.customerError.set(err.status === 400 ? 'invalid-id' : err.status === 404 ? 'not-found' : 'error');
          this.customerLoading.set(false);
        },
      });
  }

  editName(): void {
    if (!this.canManage() || this.mutating() || this.conflict() || this.missing()) {
      return;
    }
    this.fullName = this.customer()!.fullName;
    this.nameError.set(null);
    this.mutationError.set(null);
    this.editing.set(true);
  }

  reloadCustomer(): void {
    this.editing.set(false);
    this.conflict.set(false);
    this.missing.set(false);
    this.nameError.set(null);
    this.mutationError.set(null);
    this.loadProfile();
  }

  saveName(): void {
    if (!this.canManage() || !this.customer() || this.mutating() || this.conflict() || this.missing()) {
      return;
    }
    const name = this.fullName.trim();
    this.nameError.set(!name ? 'Name is required.' : name.length > 200 ? 'Name must be at most 200 characters.' : null);
    if (this.nameError()) {
      return;
    }
    this.mutate(this.customerApi.update(this.publicId(), name, this.customer()!.version));
  }

  changeStatus(): void {
    if (!this.canManage() || !this.customer() || this.editing() || this.mutating() || this.conflict() || this.missing()) {
      return;
    }
    this.mutate(this.customerApi.changeStatus(this.publicId(), this.status, this.customer()!.version));
  }

  deleteCustomer(): void {
    if (!this.canManage() || !this.customer() || this.mutating() || this.conflict() || this.missing()
      || !window.confirm('Delete this customer? This cannot be undone.')) {
      return;
    }
    this.mutate(this.customerApi.delete(this.publicId(), this.customer()!.version), true);
  }

  private mutate(request: Observable<Customer | void>, deleting = false): void {
    this.mutating.set(true);
    this.mutationError.set(null);
    this.mutationRequest = request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.mutating.set(false);
        this.editing.set(false);
        if (deleting) {
          this.customer.set(null);
          this.interactions.set([]);
          void this.router.navigate(['/customers'], { queryParams: { q: this.searchQuery } });
        } else {
          this.loadProfile();
        }
      },
      error: (err) => {
        this.mutating.set(false);
        this.conflict.set(err.status === 409);
        this.missing.set(err.status === 404);
        this.mutationError.set(err.status === 409 ? 'Customer changed or this operation is blocked by status or interaction history. Reload customer before trying again.'
          : err.status === 404 ? 'Customer no longer exists. Reload customer.'
          : err.status === 401 ? 'Sign in again to change this customer.'
          : err.status === 403 ? 'You do not have permission to change this customer.'
          : err.status === 400 ? 'Customer was not saved. Check the name or status and reload if needed.'
          : `Customer was not ${deleting ? 'deleted' : 'saved'} (${err.status || 'network'}). Try again.`);
      },
    });
  }

  loadInteractions(): void {
    this.interactionRequest?.unsubscribe();
    this.interactions.set([]);
    this.interactionLoading.set(true);
    this.interactionError.set(false);
    this.interactionRequest = this.interactionApi
      .list(this.publicId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (interactions) => {
          this.interactions.set(interactions);
          this.interactionLoading.set(false);
        },
        error: () => {
          this.interactionError.set(true);
          this.interactionLoading.set(false);
        },
      });
  }
}
