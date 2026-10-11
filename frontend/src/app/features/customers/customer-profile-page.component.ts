import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { InteractionApiService } from '../interactions/interaction-api.service';
import { InteractionFormComponent } from '../interactions/interaction-form.component';
import { Interaction } from '../interactions/interaction.model';
import { CustomerApiService } from './customer-api.service';
import { Customer } from './customer.model';

@Component({
  selector: 'app-customer-profile-page',
  standalone: true,
  imports: [DatePipe, RouterLink, InteractionFormComponent],
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
                  <strong>{{ interaction.interactionType }}</strong>
                  <p>{{ interaction.summary }}</p>
                  <time>{{ interaction.createdAt | date: 'medium' }}</time>
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
          this.customerLoading.set(false);
          this.loadInteractions();
        },
        error: (err) => {
          this.customerError.set(err.status === 400 ? 'invalid-id' : err.status === 404 ? 'not-found' : 'error');
          this.customerLoading.set(false);
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
