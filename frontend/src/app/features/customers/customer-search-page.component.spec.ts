import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CustomerProfilePageComponent } from './customer-profile-page.component';
import { CustomerSearchPageComponent } from './customer-search-page.component';

describe('CustomerSearchPageComponent', () => {
  let fixture: ComponentFixture<CustomerSearchPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerSearchPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(CustomerSearchPageComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads every customer and links each result to its profile', () => {
    const request = http.expectOne('/api/v1/customers?query=');
    request.flush([
      {
        publicId: 'CUS-1001',
        fullName: 'Amina Khan',
        status: 'ACTIVE',
        createdAt: '2026-10-06T20:00:00Z',
      },
      {
        publicId: 'CUS-1002',
        fullName: 'Jon Bell',
        status: 'PROSPECT',
        createdAt: '2026-10-07T20:00:00Z',
      },
    ]);
    fixture.detectChanges();

    const results = fixture.nativeElement.querySelectorAll('[data-testid="customer-result"]');
    const link = fixture.nativeElement.querySelector('[data-testid="customer-result-link"]');
    expect(results.length).toBe(2);
    expect(link.getAttribute('href')).toBe('/customers/CUS-1001');
  });

  it('trims the submitted query', () => {
    http.expectOne('/api/v1/customers?query=').flush([]);
    fixture.detectChanges();

    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="customer-search-input"]',
    );
    input.value = '  amina  ';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    http.expectOne('/api/v1/customers?query=amina').flush([]);
  });

  it('shows an empty state when no customers match', () => {
    http.expectOne('/api/v1/customers?query=').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-search-empty"]')).not.toBeNull();
  });

  it('shows an error and retries the last query', () => {
    http.expectOne('/api/v1/customers?query=').flush([]);
    fixture.detectChanges();

    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="customer-search-input"]',
    );
    input.value = 'amina';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/v1/customers?query=amina')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-search-error"]')).not.toBeNull();
    fixture.nativeElement.querySelector('[data-testid="customer-search-retry"]').click();
    http.expectOne('/api/v1/customers?query=amina').flush([]);
  });

  it('cancels the previous search when a new one starts', () => {
    const initialRequest = http.expectOne('/api/v1/customers?query=');
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="customer-search-input"]',
    );
    input.value = 'amina';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(initialRequest.cancelled).toBeTrue();
    http.expectOne('/api/v1/customers?query=amina').flush([]);
  });
});

describe('Customer search navigation', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'customers', component: CustomerSearchPageComponent },
          { path: 'customers/:publicId', component: CustomerProfilePageComponent },
        ]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('restores the URL query and carries it through the profile back link', async () => {
    const harness = await RouterTestingHarness.create('/customers?q=amina');
    http.expectOne('/api/v1/customers?query=amina').flush([
      {
        publicId: 'CUS-1001',
        fullName: 'Amina Khan',
        status: 'ACTIVE',
        createdAt: '2026-10-06T20:00:00Z',
      },
    ]);
    harness.detectChanges();
    await harness.fixture.whenStable();

    const input: HTMLInputElement = harness.routeNativeElement!.querySelector(
      '[data-testid="customer-search-input"]',
    )!;
    expect(input.value).toBe('amina');
    const result: HTMLAnchorElement = harness.routeNativeElement!.querySelector(
      '[data-testid="customer-result-link"]',
    )!;
    expect(result.getAttribute('href')).toBe('/customers/CUS-1001?q=amina');
    result.click();
    await harness.fixture.whenStable();
    http.expectOne('/api/v1/customers/CUS-1001').flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();

    const back: HTMLAnchorElement = harness.routeNativeElement!.querySelector(
      '[data-testid="customer-profile-back"]',
    )!;
    expect(back.getAttribute('href')).toBe('/customers?q=amina');
    back.click();
    await harness.fixture.whenStable();
    http.expectOne('/api/v1/customers?query=amina').flush([]);
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/customers?q=amina');
    expect((harness.routeNativeElement!.querySelector(
      '[data-testid="customer-search-input"]',
    ) as HTMLInputElement).value).toBe('amina');
  });

  it('replaces the URL on submit and removes q when the search is cleared', async () => {
    const harness = await RouterTestingHarness.create('/customers');
    http.expectOne('/api/v1/customers?query=').flush([]);
    await harness.fixture.whenStable();
    const location = TestBed.inject(Location);
    const replace = spyOn(location, 'replaceState').and.callThrough();
    const push = spyOn(location, 'go').and.callThrough();
    const input: HTMLInputElement = harness.routeNativeElement!.querySelector(
      '[data-testid="customer-search-input"]',
    )!;
    input.value = '  Jon & Bell  ';
    input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/customers?query=Jon%20%26%20Bell').flush([]);
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/customers?q=Jon%20%26%20Bell');
    expect(replace).toHaveBeenCalled();
    expect(push).not.toHaveBeenCalled();

    input.value = '';
    input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/customers?query=').flush([]);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/customers');
  });

  it('links a directly opened profile without q to all customers', async () => {
    const harness = await RouterTestingHarness.create('/customers/CUS-1001');
    http.expectOne('/api/v1/customers/CUS-1001').flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();

    expect(harness.routeNativeElement!.querySelector('[data-testid="customer-profile-back"]')!
      .getAttribute('href')).toBe('/customers');
  });
});
