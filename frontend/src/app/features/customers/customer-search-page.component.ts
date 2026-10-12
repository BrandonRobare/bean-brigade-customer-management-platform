import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CustomerApiService } from './customer-api.service';
import { Customer } from './customer.model';

@Component({
  selector: 'app-customer-search-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section>
      <h2>Customers</h2>
      <form class="search-form" (ngSubmit)="submit()">
        <label>
          Name or customer ID
          <input name="query" [(ngModel)]="query" data-testid="customer-search-input" />
        </label>
        <button type="submit" data-testid="customer-search-submit">Search</button>
      </form>

      @if (canManage()) {
        <form class="search-form" (ngSubmit)="create()" data-testid="customer-create-form">
          <label>
            New customer name
            <input name="fullName" [(ngModel)]="fullName" [disabled]="creating()" maxlength="200" aria-describedby="customer-create-error" data-testid="customer-create-name" />
          </label>
          <button type="submit" [disabled]="creating()" data-testid="customer-create-submit">{{ creating() ? 'Creating…' : 'Create customer' }}</button>
        </form>
        @if (createError()) {
          <p id="customer-create-error" role="alert" data-testid="customer-create-error">{{ createError() }}</p>
        }
      }

      @if (loading()) {
        <p data-testid="customer-search-loading">Searching…</p>
      } @else if (error()) {
        <div role="alert" data-testid="customer-search-error">
          <p>{{ error() }}</p>
          <button type="button" (click)="retry()" data-testid="customer-search-retry">Retry</button>
        </div>
      } @else if (customers().length === 0) {
        <p data-testid="customer-search-empty">No customers match "{{ lastQuery() }}".</p>
      } @else {
        <ul>
          @for (customer of customers(); track customer.publicId) {
            <li data-testid="customer-result">
              <a [routerLink]="['/customers', customer.publicId]" [queryParams]="{ q: lastQuery() || null }" data-testid="customer-result-link">
                {{ customer.fullName }}
              </a>
              <span>{{ customer.publicId }} · {{ customer.status }}</span>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styleUrl: './customer-search-page.component.scss',
})
export class CustomerSearchPageComponent {
  private readonly session = inject(AuthSessionService);
  private readonly destroyRef = inject(DestroyRef);
  readonly canManage = computed(() => this.session.roles().includes('ADMIN'));
  fullName = '';
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);
  private readonly api = inject(CustomerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private request?: Subscription;

  query = '';
  readonly customers = signal<Customer[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastQuery = signal('');

  constructor() {
    this.query = this.route.snapshot.queryParamMap.get('q') ?? '';
    this.search(this.query.trim());
  }

  submit(): void {
    const query = this.query.trim();
    void this.router.navigate(['/customers'], { queryParams: { q: query || null }, replaceUrl: true });
    this.search(query);
  }

  retry(): void {
    this.search(this.lastQuery());
  }

  create(): void {
    if (!this.canManage() || this.creating()) {
      return;
    }
    const name = this.fullName.trim();
    this.createError.set(!name ? 'Name is required.' : name.length > 200 ? 'Name must be at most 200 characters.' : null);
    if (this.createError()) {
      return;
    }
    this.creating.set(true);
    this.api.create(name).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (customer) => {
        this.creating.set(false);
        this.fullName = '';
        this.query = customer.publicId;
        this.submit();
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(err.status === 401 ? 'Sign in again to create a customer.'
          : err.status === 403 ? 'You do not have permission to create customers.'
          : err.status === 400 ? 'Customer was not created. Check the name.'
          : `Customer was not created (${err.status || 'network'}). Try again.`);
      },
    });
  }

  private search(query: string): void {
    this.request?.unsubscribe();
    this.lastQuery.set(query);
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api.search(query).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (customers) => {
        this.customers.set(customers);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(`Customer search failed (${err.status ?? 'network'}).`);
        this.loading.set(false);
      },
    });
  }
}
