import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
